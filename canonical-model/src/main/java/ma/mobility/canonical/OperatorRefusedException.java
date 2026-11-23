package ma.mobility.canonical;

/** Refus métier d'un opérateur (HTTP 409 côté REST). Ne doit pas ouvrir un circuit breaker. */
public class OperatorRefusedException extends RuntimeException {
    public OperatorRefusedException(String message) { super(message); }
}