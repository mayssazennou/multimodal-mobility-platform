package ma.mobility.canonical;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Offre de transport, indépendante de l'opérateur d'origine.
 * offerId = mode + ":" + providerRef (ex. "AIR:AIR-CMN-ORY-1330") : unique entre opérateurs.
 * Les horaires sont sans fuseau, comme dans les contrats legacy (hypothèse documentée dans l'ADR).
 */
public record Offer(
        String offerId,
        Mode mode,
        String providerRef,
        String origin,
        String destination,
        LocalDateTime departure,
        LocalDateTime arrival,
        Money price,
        int seatsAvailable) {

    public Offer {
        Objects.requireNonNull(offerId); Objects.requireNonNull(mode); Objects.requireNonNull(providerRef);
        Objects.requireNonNull(origin); Objects.requireNonNull(destination);
        Objects.requireNonNull(departure); Objects.requireNonNull(price);
        if (seatsAvailable < 0) throw new IllegalArgumentException("places négatives");
    }

    public static String idOf(Mode mode, String providerRef) { return mode + ":" + providerRef; }
}