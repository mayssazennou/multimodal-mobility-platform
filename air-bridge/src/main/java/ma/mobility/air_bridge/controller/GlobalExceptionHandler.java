package ma.mobility.air_bridge.controller;


import ma.mobility.air_bridge.dto.ApiError;
import ma.mobility.air_bridge.exception.OperatorRefusedException;
import ma.mobility.air_bridge.exception.OperatorUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OperatorRefusedException.class)
    public ResponseEntity<ApiError> handleRefused(OperatorRefusedException e) {
        return build(HttpStatus.CONFLICT, "NOT_AVAILABLE", e);
    }

    @ExceptionHandler(OperatorUnavailableException.class)
    public ResponseEntity<ApiError> handleUnavailable(OperatorUnavailableException e) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE", e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, Exception e) {
        String correlationId = correlationId();
        log.warn("[{}] {} : {}", correlationId, code, e.getMessage(), e);
        return ResponseEntity.status(status)
                .body(new ApiError(code, e.getMessage(), correlationId));
    }

    // Provisoire : sera remplacé par l'identifiant de trace OpenTelemetry (Lot 3)
    private String correlationId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
