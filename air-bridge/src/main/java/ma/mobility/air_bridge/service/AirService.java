package ma.mobility.air_bridge.service;

import lombok.RequiredArgsConstructor;
import ma.mobility.air_bridge.client.AirSoapClient;
import ma.mobility.air_bridge.exception.IdempotencyConflictException;
import ma.mobility.air_bridge.exception.OperatorRefusedException;
import ma.mobility.air_bridge.exception.OperatorUnavailableException;
import ma.mobility.air_bridge.exception.RequestInProgressException;
import ma.mobility.air_bridge.idempotency.Entry;
import ma.mobility.air_bridge.idempotency.IdempotencyStore;
import ma.mobility.air_bridge.idempotency.Status;
import ma.mobility.air_bridge.mapper.FlightMapper;
import ma.mobility.air_bridge.model.Booking;
import ma.mobility.air_bridge.model.Hold;
import ma.mobility.air_bridge.model.Mode;
import ma.mobility.air_bridge.model.Offer;
import ma.mobility.airbridge.generated.Flight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.soap.client.SoapFaultClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.DateTimeException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static ma.mobility.air_bridge.mapper.FlightMapper.AIR_PREFIX;

@Service
@RequiredArgsConstructor
public class AirService {

    private static final Logger log = LoggerFactory.getLogger(AirService.class);
    private final IdempotencyStore store;
    private final ObjectMapper mapper;
    private static final String STATUS_CANCELLED = "BOOKING_CANCELLED";
    private static final String STATUS_NOT_FOUND = "NOT_FOUND";

    private final FlightMapper flightMapper;
    private final AirSoapClient airSoapClient;

    // ---------------------------------------------------------------- search
    public List<Offer> searchOffers(String origin, String destination, String date) {

        List<Flight> flights = callPartner(
                () -> airSoapClient.searchFlights(origin, destination, date),
                "Recherche refusée par l'opérateur aérien");

        try {
            return flights.stream().map(flightMapper::toOffer).toList();
        } catch (DateTimeException | NullPointerException e) {
            // Le partenaire a renvoyé une donnée inexploitable (date mal formée ou absente)
            log.warn("Vol invalide reçu de l'opérateur aérien : {}", e.getMessage());
            throw new OperatorUnavailableException("Réponse de recherche invalide de l'opérateur aérien", e);
        }
    }

    // ------------------------------------------------------------------ hold
    public Hold hold(String offerId, int seats, String idempotencyKey) {

        if (offerId == null || !offerId.startsWith(AIR_PREFIX)) {
            throw new IllegalArgumentException("offerId invalide : doit commencer par " + AIR_PREFIX);
        }
        if (seats <= 0) {
            throw new IllegalArgumentException("Le nombre de places doit être positif");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("L'en-tête Idempotency-Key est obligatoire");
        }

        String key = "hold:" + idempotencyKey;
        String fingerprint = offerId + ":" + seats;

        // Chemin normal : on est le premier
        if (store.tryLock(key, fingerprint)) {
            return executeHold(key, fingerprint, offerId, seats);
        }

        // Chemin rejeu : la clé existe déjà
        Optional<Entry> existing = store.get(key);

        if (existing.isEmpty()) {
            // Expirée entre tryLock et get : on réessaie UNE fois
            if (store.tryLock(key, fingerprint)) {
                return executeHold(key, fingerprint, offerId, seats);
            }
            throw new RequestInProgressException("Requête concurrente en cours");
        }

        Entry entry = existing.get();

        if (!entry.fingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException("Idempotency-Key déjà utilisée avec une autre requête");
        }
        if (entry.status() == Status.IN_PROGRESS) {
            throw new RequestInProgressException("Requête identique déjà en cours de traitement");
        }

        try {
            return mapper.readValue(entry.result(), Hold.class);   // aucun appel au partenaire
        } catch (JacksonException e) {
            throw new OperatorUnavailableException("Résultat mémorisé illisible", e);
        }
    }

    private Hold executeHold(String key, String fingerprint, String offerId, int seats) {
        Hold hold;
        try {
            String flightId = offerId.substring(AIR_PREFIX.length());

            String holdId = callPartner(
                    () -> airSoapClient.holdFlight(flightId, seats),
                    "Réservation refusée par l'opérateur aérien");

            if (holdId == null || holdId.isBlank()) {
                log.warn("holdId invalide reçu de l'opérateur aérien pour le vol '{}'", flightId);
                throw new OperatorUnavailableException("Réponse de réservation invalide de l'opérateur aérien");
            }
            hold = new Hold(holdId, offerId, Mode.AIR);
        } catch (RuntimeException e) {
            releaseQuietly(key);   // la saga pourra rejouer
            throw e;               // l'erreur d'origine est conservée
        }

        // Le hold existe chez le partenaire : une panne Redis ici ne doit pas le perdre
        try {
            store.saveResult(key, fingerprint, mapper.writeValueAsString(hold));
        } catch (RuntimeException e) {   // couvre aussi JacksonException (Jackson 3)
            log.warn("Hold {} créé mais résultat non mémorisé : {}", hold.holdId(), e.getMessage());
        }
        return hold;
    }

    private void releaseQuietly(String key) {
        try {
            store.release(key);
        } catch (RuntimeException e) {
            log.warn("Impossible de libérer la clé {} (expirera seule en 30 s)", key);
        }
    }

    // --------------------------------------------------------------- confirm
    public Booking confirm(String holdId, String idempotencyKey) {

        if (holdId == null || holdId.isBlank()) {
            throw new IllegalArgumentException("Le holdId est obligatoire");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("L'en-tête Idempotency-Key est obligatoire");
        }

        String key = "confirm:" + idempotencyKey;
        String fingerprint = holdId;

        // Chemin normal : on est le premier
        if (store.tryLock(key, fingerprint)) {
            return executeConfirm(key, fingerprint, holdId);
        }

        // Chemin rejeu : la clé existe déjà
        Optional<Entry> existing = store.get(key);

        if (existing.isEmpty()) {
            // Expirée entre tryLock et get : on réessaie UNE fois
            if (store.tryLock(key, fingerprint)) {
                return executeConfirm(key, fingerprint, holdId);
            }
            throw new RequestInProgressException("Requête concurrente en cours");
        }

        Entry entry = existing.get();

        if (!entry.fingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException("Idempotency-Key déjà utilisée avec une autre requête");
        }
        if (entry.status() == Status.IN_PROGRESS) {
            throw new RequestInProgressException("Requête identique déjà en cours de traitement");
        }

        try {
            return mapper.readValue(entry.result(), Booking.class);   // aucun appel au partenaire
        } catch (JacksonException e) {
            throw new OperatorUnavailableException("Résultat mémorisé illisible", e);
        }
    }

    private Booking executeConfirm(String key, String fingerprint, String holdId) {
        Booking booking;
        try {
            String bookingRef = callPartner(
                    () -> airSoapClient.confirmFlight(holdId),
                    "Confirmation refusée par l'opérateur aérien");

            if (bookingRef == null || bookingRef.isBlank()) {
                log.warn("bookingRef invalide reçu de l'opérateur aérien pour le hold '{}'", holdId);
                throw new OperatorUnavailableException("Réponse de confirmation invalide de l'opérateur aérien");
            }
            booking = new Booking(bookingRef, Mode.AIR);
        } catch (RuntimeException e) {
            releaseQuietly(key);   // la saga pourra rejouer
            throw e;               // l'erreur d'origine est conservée
        }

        // La réservation existe chez le partenaire : une panne Redis ne doit pas la perdre
        try {
            store.saveResult(key, fingerprint, mapper.writeValueAsString(booking));
        } catch (RuntimeException e) {
            log.warn("Booking {} créé mais résultat non mémorisé : {}", booking.bookingRef(), e.getMessage());
        }
        return booking;
    }

    // ---------------------------------------------------------------- cancel
    public void cancel(String reference) {

        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("La référence d'annulation est obligatoire");
        }

        String status = callPartner(
                () -> airSoapClient.cancelFlight(reference),
                "Erreur inattendue de l'opérateur aérien à l'annulation");

        // NOT_FOUND = déjà annulé : succès (idempotence)
        if (STATUS_CANCELLED.equals(status) || STATUS_NOT_FOUND.equals(status)) {
            return;
        }

        log.warn("Statut d'annulation inattendu reçu de l'opérateur aérien : '{}'", status);
        throw new OperatorUnavailableException("Réponse d'annulation inattendue de l'opérateur aérien");
    }

    // ------------------------------------------------- traduction des erreurs
    /**
     * Point unique de traduction des exceptions Spring WS en exceptions canoniques.
     * Aucune exception Spring WS ne sort de cette classe.
     */
    private <T> T callPartner(Supplier<T> call, String refusedMessage) {
        try {
            return call.get();
        } catch (SoapFaultClientException e) {
            throw new OperatorRefusedException(refusedMessage, e);
        } catch (WebServiceIOException e) {
            throw new OperatorUnavailableException("Opérateur aérien injoignable", e);
        } catch (WebServiceClientException e) {
            // Filet de sécurité : autre erreur côté client SOAP (ex. réponse non conforme)
            throw new OperatorUnavailableException("Réponse invalide de l'opérateur aérien", e);
        }
    }
}