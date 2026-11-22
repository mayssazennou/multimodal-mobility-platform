package ma.mobility.air_bridge.exception;

public class OperatorUnavailableException extends RuntimeException {

    public OperatorUnavailableException(String message) {
        super(message);
    }

    public OperatorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}