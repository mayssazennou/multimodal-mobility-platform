package ma.mobility.air_bridge.exception;

public class OperatorRefusedException extends RuntimeException {

    public OperatorRefusedException(String message) {
        super(message);
    }

    public OperatorRefusedException(String message, Throwable cause) {
        super(message, cause);
    }
}