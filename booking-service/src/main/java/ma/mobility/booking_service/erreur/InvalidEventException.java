package ma.mobility.booking_service.erreur;


/** Evenement mal forme ou incoherent : inutile de le rejouer, il part directement en DLT. */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }
}
