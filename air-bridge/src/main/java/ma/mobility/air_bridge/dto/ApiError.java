package ma.mobility.air_bridge.dto;

public record ApiError(String code, String message, String correlationId) {
}
