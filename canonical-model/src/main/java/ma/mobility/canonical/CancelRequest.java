package ma.mobility.canonical;

/** Corps de POST /v1/cancellations : reference = holdId (maintien) ou bookingId (réservation). */
public record CancelRequest(String reference) {
}
