package ma.mobility.canonical;

/** Réservation confirmée chez un opérateur (phase 2 de la saga). bookingId = mode + ":" + providerBookingRef. */
public record Booking(
        String bookingId,
        Mode mode,
        String providerBookingRef,
        String holdId,
        BookingStatus status) {
}