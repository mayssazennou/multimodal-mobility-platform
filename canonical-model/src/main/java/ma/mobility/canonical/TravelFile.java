package ma.mobility.canonical;

import java.time.Instant;
import java.util.List;

/** Dossier de voyage consolidé : l'objet métier que le client voit, sans trace de CORBA ni de SOAP. */
public record TravelFile(
        String fileId,
        TravelStatus status,
        List<Booking> bookings,
        Money total,
        Instant createdAt) {

    public TravelFile {
        bookings = List.copyOf(bookings);
    }
}