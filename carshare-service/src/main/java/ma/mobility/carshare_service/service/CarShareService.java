package ma.mobility.carshare_service.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import ma.mobility.carshare_service.dto.Booking;
import ma.mobility.carshare_service.dto.Hold;
import ma.mobility.carshare_service.dto.Offer;
import ma.mobility.carshare_service.entities.Reservation;
import ma.mobility.carshare_service.entities.ReservationStatus;
import ma.mobility.carshare_service.entities.Vehicle;
import ma.mobility.carshare_service.error.NotAvailableException;
import ma.mobility.carshare_service.repository.ReservationRepository;
import ma.mobility.carshare_service.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CarShareService {

    private final VehicleRepository vehicles;
    private final ReservationRepository reservations;

    @Value("${carshare.hold-ttl-minutes}")
    private long holdTtlMinutes;

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Offer> searchOffers(String city, Instant from, Instant to) {
        checkSlot(from, to);
        long hours = Math.max(1, Duration.between(from, to).toHours());
        return vehicles.findFree(city, from, to).stream()
                .map(v -> new Offer(v.getId(), v.getId(), v.getPlate(), v.getCity(),
                        v.getCategory(), from, to, v.getPricePerHourMinor() * hours))
                .toList();
    }

    @Transactional
    public Hold hold(String offerId, Instant from, Instant to, String key) {
        // Rejeu : même clé = même hold, rien n'est recréé
        if (key != null) {
            Optional<Reservation> existing = reservations.findByIdempotencyKey(key);
            if (existing.isPresent()) return toHold(existing.get());
        }
        checkSlot(from, to);
        Vehicle v = vehicles.findById(offerId)
                .orElseThrow(() -> new NotAvailableException("offre inconnue"));

        Reservation r = new Reservation();
        r.setId(UUID.randomUUID().toString());
        r.setHoldId(UUID.randomUUID().toString());
        r.setVehicleId(v.getId());
        r.setStartAt(from);
        r.setEndAt(to);
        r.setStatus(ReservationStatus.HELD);
        r.setHoldExpiresAt(Instant.now().plus(holdTtlMinutes, ChronoUnit.MINUTES));
        r.setIdempotencyKey(key);
        try {
            reservations.saveAndFlush(r);   // la contrainte no_overlap se déclenche ici
        } catch (DataIntegrityViolationException e) {
            throw new NotAvailableException("véhicule indisponible sur ce créneau");
        }
        return toHold(r);
    }

    @Transactional
    public Booking confirm(String holdId, String key) {
        Reservation r = reservations.findByHoldId(holdId)
                .orElseThrow(() -> new NotAvailableException("hold inconnu"));
        if (r.getStatus() == ReservationStatus.CONFIRMED) return toBooking(r);   // rejeu
        if (r.getStatus() != ReservationStatus.HELD
                || r.getHoldExpiresAt().isBefore(Instant.now()))
            throw new NotAvailableException("hold expiré ou annulé");
        r.setStatus(ReservationStatus.CONFIRMED);
        r.setBookingRef(UUID.randomUUID().toString());
        return toBooking(r);
    }

    @Transactional
    public void cancel(String reference) {
        reservations.findByBookingRef(reference).ifPresent(r -> {
            if (r.getStatus() != ReservationStatus.CANCELLED) {
                r.setStatus(ReservationStatus.CANCELLED);
            }
        });   // référence inconnue = OK (idempotent)
    }

    private void checkSlot(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from))
            throw new IllegalArgumentException("créneau invalide : from doit précéder to");
    }

    private Hold toHold(Reservation r) {
        return new Hold(r.getHoldId(), r.getVehicleId(), r.getHoldExpiresAt());
    }

    private Booking toBooking(Reservation r) {
        return new Booking(r.getBookingRef(), r.getHoldId(), r.getStatus().name());
    }
}