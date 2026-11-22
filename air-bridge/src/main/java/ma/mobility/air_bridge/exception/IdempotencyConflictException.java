package ma.mobility.air_bridge.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) { super(message); }
}
