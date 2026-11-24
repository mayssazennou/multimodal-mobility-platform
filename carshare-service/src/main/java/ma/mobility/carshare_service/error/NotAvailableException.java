package ma.mobility.carshare_service.error;

public class NotAvailableException extends RuntimeException {
    public NotAvailableException(String reason) { super(reason); }
}