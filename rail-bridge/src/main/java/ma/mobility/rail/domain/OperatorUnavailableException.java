package ma.mobility.rail.domain;

/** Opérateur injoignable ou trop lent (exception système CORBA, IOR illisible). Renvoyé en HTTP 503. */
public class OperatorUnavailableException extends RuntimeException {
    public OperatorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}