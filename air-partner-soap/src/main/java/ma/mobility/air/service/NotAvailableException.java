package ma.mobility.air.service;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/**
 * Levée lorsqu'une opération ne peut aboutir (vol inconnu, plus de place,
 * maintien expiré…). Spring WS la convertit en SOAP Fault côté réponse.
 */
@SoapFault(faultCode = FaultCode.SERVER)
public class NotAvailableException extends RuntimeException {
    public NotAvailableException(String message) {
        super(message);
    }
}
