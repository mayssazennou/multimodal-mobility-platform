package ma.mobility.air_bridge.service;

import lombok.RequiredArgsConstructor;
import ma.mobility.air_bridge.client.AirSoapClient;
import ma.mobility.air_bridge.exception.OperatorRefusedException;
import ma.mobility.air_bridge.exception.OperatorUnavailableException;
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

import java.time.DateTimeException;
import java.util.List;
import java.util.function.Supplier;

import static ma.mobility.air_bridge.mapper.FlightMapper.AIR_PREFIX;

@Service
@RequiredArgsConstructor
public class AirService {

    private static final Logger log = LoggerFactory.getLogger(AirService.class);

    private static final String STATUS_CANCELLED = "BOOKING_CANCELLED";
    private static final String STATUS_NOT_FOUND = "NOT_FOUND";

    private final FlightMapper mapper;
    private final AirSoapClient airSoapClient;

    // ---------------------------------------------------------------- search
    public List<Offer> searchOffers(String origin, String destination, String date) {

        List<Flight> flights = callPartner(
                () -> airSoapClient.searchFlights(origin, destination, date),
                "Recherche refusée par l'opérateur aérien");

        try {
            return flights.stream().map(mapper::toOffer).toList();
        } catch (DateTimeException | NullPointerException e) {
            // Le partenaire a renvoyé une donnée inexploitable (date mal formée ou absente)
            log.warn("Vol invalide reçu de l'opérateur aérien : {}", e.getMessage());
            throw new OperatorUnavailableException("Réponse de recherche invalide de l'opérateur aérien", e);
        }
    }

    // ------------------------------------------------------------------ hold
    public Hold hold(String offerId, int seats) {

        if (offerId == null || !offerId.startsWith(AIR_PREFIX)) {
            throw new IllegalArgumentException("offerId invalide : doit commencer par " + AIR_PREFIX);
        }
        if (seats <= 0) {
            throw new IllegalArgumentException("Le nombre de places doit être positif");
        }

        String flightId = offerId.substring(AIR_PREFIX.length());

        String holdId = callPartner(
                () -> airSoapClient.holdFlight(flightId, seats),
                "Réservation refusée par l'opérateur aérien");

        if (holdId == null || holdId.isBlank()) {
            log.warn("holdId invalide reçu de l'opérateur aérien pour le vol '{}'", flightId);
            throw new OperatorUnavailableException("Réponse de réservation invalide de l'opérateur aérien");
        }

        return new Hold(holdId, offerId, Mode.AIR);
    }

    // --------------------------------------------------------------- confirm
    public Booking confirm(String holdId) {

        if (holdId == null || holdId.isBlank()) {
            throw new IllegalArgumentException("Le holdId est obligatoire");
        }

        String bookingRef = callPartner(
                () -> airSoapClient.confirmFlight(holdId),
                "Confirmation refusée par l'opérateur aérien");

        if (bookingRef == null || bookingRef.isBlank()) {
            log.warn("bookingRef invalide reçu de l'opérateur aérien pour le hold '{}'", holdId);
            throw new OperatorUnavailableException("Réponse de confirmation invalide de l'opérateur aérien");
        }

        return new Booking(bookingRef, Mode.AIR);
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