package ma.mobility.canonical;

import java.time.LocalDate;
import java.util.List;

/**
 * Contrat logique commun à tous les opérateurs : rail, air et autopartage l'implémentent
 * (les deux premiers via un bridge, le dernier nativement). La saga ne connaît que ceci.
 */
public interface OperatorPort {

    Mode mode();

    List<Offer> search(String origin, String destination, LocalDate date);

    /** @throws OperatorRefusedException si l'opérateur refuse ; OperatorUnavailableException si injoignable */
    Hold hold(String offerId, int seats);

    /** @throws OperatorRefusedException si le maintien est inconnu ou expiré */
    Booking confirm(String holdId);

    /**
     * Compensation. Idempotente : annuler une référence déjà annulée ou inconnue réussit sans erreur.
     * Accepte un holdId ou un bookingId.
     */
    void cancel(String reference);
}