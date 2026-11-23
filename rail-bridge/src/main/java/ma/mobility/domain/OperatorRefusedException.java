package ma.mobility.rail.domain;

/** Refus métier de l'opérateur (NotAvailable). Renvoyé en HTTP 409 ; ne doit pas ouvrir un circuit breaker. */
public class OperatorRefusedException extends RuntimeException {
    public OperatorRefusedException(String message) {
        super(message);
    }
}