package ma.mobility.carshare;

import ma.mobility.canonical.*;
import ma.mobility.carshare.api.Ids;
import ma.mobility.carshare.domain.*;
import ma.mobility.carshare.domain.Repositories.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;   // Jackson 3 (Boot 4) : tools.jackson, exceptions non contrôlées

import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Autopartage : implémente nativement le contrat commun (pas de legacy derrière).
 * Chaque méthode publique est une transaction locale ACID ; la cohérence globale reste celle de la saga.
 */
@Service
public class CarshareService {
    /** Même fenêtre que les opérateurs legacy (10 min), mais ici l échéance est exposée dans Hold.expiresAt. */
    static final Duration HOLD_TTL = Duration.ofMinutes(10);

    private final OfferRepository offers;
    private final HoldRepository holds;
    private final BookingRepository bookings;
    private final IdempotencyRepository idem;
    private final ObjectMapper json;

    public CarshareService(OfferRepository offers, HoldRepository holds, BookingRepository bookings,
                           IdempotencyRepository idem, ObjectMapper json) {
        this.offers = offers; this.holds = holds; this.bookings = bookings; this.idem = idem; this.json = json;
    }

    @Transactional(readOnly = true)
    public List<Offer> search(String origin, String destination, String date) {
        final LocalDate day;
        try { day = LocalDate.parse(date); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("date attendue au format AAAA-MM-JJ : " + date); }
        return offers.findByOriginIgnoreCaseAndDestinationIgnoreCase(origin, destination).stream()
                .map(o -> new Offer(Ids.canonical(o.getProviderRef()), Mode.CAR, o.getProviderRef(),
                        o.getOrigin(), o.getDestination(),
                        day.atTime(o.getPickupTime()), day.atTime(o.getReturnTime()),
                        Money.mad(o.getPriceMinor()), o.getAvailable()))
                .toList();
    }

    @Transactional
    public Hold hold(String offerId, int seats, String key) {
        return once("hold", key, Hold.class, () -> {
            if (seats < 1) throw new IllegalArgumentException("seats doit être >= 1");
            String ref = Ids.raw(offerId);
            if (!offers.existsById(ref)) throw new OperatorRefusedException("Offre inconnue : " + ref);
            if (offers.tryReserve(ref, seats) == 0) {
                int dispo = offers.findById(ref).map(VehicleOfferEntity::getAvailable).orElse(0);
                throw new OperatorRefusedException(
                        "Plus assez de véhicules sur " + ref + " (demandé " + seats + ", disponible " + dispo + ")");
            }
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            HoldEntity h = holds.save(new HoldEntity("HOLD-CAR-" + UUID.randomUUID(), ref, seats,
                    HoldStatus.HELD, now, now.plus(HOLD_TTL)));
            return toHold(h);
        });
    }

    @Transactional
    public Booking confirm(String holdId, String key) {
        return once("confirm", key, Booking.class, () -> {
            String raw = Ids.raw(holdId);
            HoldEntity h = holds.findById(raw).orElseThrow(() -> new OperatorRefusedException("Maintien inconnu : " + raw));
            switch (h.getStatus()) {
                case CONFIRMED:   // rejeu de la saga : on renvoie la réservation existante
                    return bookings.findByHoldId(raw).map(this::toBooking)
                            .orElseThrow(() -> new IllegalStateException("maintien confirmé sans réservation : " + raw));
                case CANCELLED:
                    throw new OperatorRefusedException("Maintien annulé ou expiré : " + raw);
                default:
                    if (h.getExpiresAt().isBefore(Instant.now())) {
                        throw new OperatorRefusedException("Maintien expiré : " + raw);
                    }
            }
            h.setStatus(HoldStatus.CONFIRMED);
            BookingEntity b = bookings.save(new BookingEntity("BK-CAR-" + UUID.randomUUID(), raw, BookingStatus.CONFIRMED));
            return toBooking(b);
        });
    }

    /** Idempotent par construction : référence inconnue ou déjà annulée = rien à faire, pas d erreur. */
    @Transactional
    public void cancel(String reference) {
        String raw = Ids.raw(reference);
        Optional<BookingEntity> booking = bookings.findById(raw);
        if (booking.isPresent()) { cancelBooking(booking.get()); return; }
        holds.findById(raw).ifPresent(h -> {
            if (h.getStatus() == HoldStatus.HELD) {
                h.setStatus(HoldStatus.CANCELLED);
                offers.release(h.getOfferRef(), h.getQuantity());
            } else if (h.getStatus() == HoldStatus.CONFIRMED) {
                bookings.findByHoldId(h.getId()).ifPresent(this::cancelBooking);
            }
        });
    }

    /** Libère les maintiens expirés (les opérateurs legacy font de même au bout de 10 minutes). */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void expireHolds() {
        for (HoldEntity h : holds.findByStatusAndExpiresAtBefore(HoldStatus.HELD, Instant.now())) {
            h.setStatus(HoldStatus.CANCELLED);
            offers.release(h.getOfferRef(), h.getQuantity());
        }
    }

    private void cancelBooking(BookingEntity b) {
        if (b.getStatus() != BookingStatus.CONFIRMED) return;
        b.setStatus(BookingStatus.CANCELLED);
        holds.findById(b.getHoldId()).ifPresent(h -> {
            h.setStatus(HoldStatus.CANCELLED);
            offers.release(h.getOfferRef(), h.getQuantity());
        });
    }

    private Hold toHold(HoldEntity h) {
        return new Hold(Ids.canonical(h.getId()), Mode.CAR, h.getId(), Ids.canonical(h.getOfferRef()),
                h.getQuantity(), h.getStatus(), h.getCreatedAt(), h.getExpiresAt());
    }

    private Booking toBooking(BookingEntity b) {
        return new Booking(Ids.canonical(b.getId()), Mode.CAR, b.getId(), Ids.canonical(b.getHoldId()), b.getStatus());
    }

    /** Idempotency-Key : même clé = même réponse que la première fois, sans rejouer l opération. */
    private <T> T once(String operation, String key, Class<T> type, Supplier<T> action) {
        if (key == null || key.isBlank()) return action.get();
        String recordKey = operation + ":" + key;
        Optional<IdempotencyRecord> previous = idem.findById(recordKey);
        if (previous.isPresent()) return json.readValue(previous.get().getResponse(), type);
        T result = action.get();
        idem.save(new IdempotencyRecord(recordKey, json.writeValueAsString(result)));
        return result;
    }
}
