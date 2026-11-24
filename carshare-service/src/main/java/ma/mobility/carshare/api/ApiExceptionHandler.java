package ma.mobility.carshare.api;

import jakarta.servlet.http.HttpServletRequest;
import ma.mobility.canonical.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/** Mêmes classes d erreurs que les bridges : refus métier (409), opérateur injoignable (503), requête invalide (400). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OperatorRefusedException.class)
    public ResponseEntity<ApiError> refused(OperatorRefusedException e, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ErrorCode.NOT_AVAILABLE, e.getMessage(), req);
    }

    @ExceptionHandler(OperatorUnavailableException.class)
    public ResponseEntity<ApiError> unavailable(OperatorUnavailableException e, HttpServletRequest req) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PROVIDER_UNAVAILABLE, e.getMessage(), req);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
                       MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> invalid(Exception e, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, e.getMessage(), req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, ErrorCode code, String message, HttpServletRequest req) {
        String correlationId = req.getHeader("traceparent");
        if (correlationId == null) correlationId = UUID.randomUUID().toString();
        return ResponseEntity.status(status).body(new ApiError(code, message, correlationId));
    }
}
