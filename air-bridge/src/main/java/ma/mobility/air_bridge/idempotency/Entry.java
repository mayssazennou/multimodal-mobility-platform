package ma.mobility.air_bridge.idempotency;

public record Entry(String fingerprint, Status status,String result) {
}
