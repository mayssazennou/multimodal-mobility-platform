package ma.mobility.saga_service.exception;


/** Timeout ou panne réseau : on ignore si le partenaire a exécuté l'opération. */
public class PartnerUncertainException extends RuntimeException {
    public PartnerUncertainException(String message, Throwable cause) { super(message, cause); }
}