package ma.mobility.saga_service.exception;

/** Le partenaire a répondu NON (plus de place, vol inconnu...). On sait que rien n'a été fait. */
public class PartnerRejectedException extends RuntimeException {
    public PartnerRejectedException(String message) { super(message); }
}