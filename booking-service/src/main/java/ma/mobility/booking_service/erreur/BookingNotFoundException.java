package ma.mobility.booking_service.erreur;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(String id) { super("dossier inconnu : " + id); }
}