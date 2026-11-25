package ma.mobility.canonical;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Message publie par la saga (via l'outbox) et consomme par le booking-service.
 * Enveloppe : eventId, type, bookingId, occurredAt.
 * Contenu : travelerId, bookings, total, reason (selon le type).
 */
public record BookingEvent(
        String eventId,
        BookingEventType type,
        String bookingId,
        Instant occurredAt,
        String travelerId,
        List<Booking> bookings,
        Money total,
        String reason) {

    public BookingEvent {
        bookings = bookings == null ? List.of() : List.copyOf(bookings);
    }

    public static BookingEvent started(String bookingId, String travelerId) {
        return new BookingEvent(UUID.randomUUID().toString(), BookingEventType.BOOKING_STARTED,
                bookingId, Instant.now(), travelerId, List.of(), null, null);
    }

    public static BookingEvent completed(String bookingId, String travelerId,
                                         List<Booking> bookings, Money total) {
        return new BookingEvent(UUID.randomUUID().toString(), BookingEventType.BOOKING_COMPLETED,
                bookingId, Instant.now(), travelerId, bookings, total, null);
    }

    public static BookingEvent cancelled(String bookingId, String travelerId,
                                         List<Booking> bookings, String reason) {
        return new BookingEvent(UUID.randomUUID().toString(), BookingEventType.BOOKING_CANCELLED,
                bookingId, Instant.now(), travelerId, bookings, null, reason);
    }
}
