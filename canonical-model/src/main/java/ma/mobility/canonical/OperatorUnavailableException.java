package ma.mobility.canonical;

/** Opérateur injoignable ou trop lent (HTTP 503 côté REST). C'est l'échec que le circuit breaker compte. */
public class OperatorUnavailableException extends RuntimeException {
    public OperatorUnavailableException(String message, Throwable cause) { super(message, cause); }
}