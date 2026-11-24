package ma.mobility.rail.api;

import ma.mobility.rail.domain.OperatorRefusedException;
import ma.mobility.rail.domain.OperatorUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

/** Deux classes d'erreurs : refus métier (409) et opérateur injoignable (503). Jamais de texte legacy parsé. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(OperatorRefusedException.class)
    public ResponseEntity<Dto.ApiError> refused(OperatorRefusedException e, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "NOT_AVAILABLE", e.getMessage(), req);
    }

    @ExceptionHandler(OperatorUnavailableException.class)
    public ResponseEntity<Dto.ApiError> unavailable(OperatorUnavailableException e, HttpServletRequest req) {
        log.warn("Opérateur ferroviaire injoignable : {}", e.getMessage(), e);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE", e.getMessage(), req);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Dto.ApiError> invalid(Exception e, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage(), req);
    }

    private ResponseEntity<Dto.ApiError> build(HttpStatus status, String code, String message, HttpServletRequest req) {
        String correlationId = req.getHeader("traceparent");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        return ResponseEntity.status(status).body(new Dto.ApiError(code, message, correlationId));
    }
}