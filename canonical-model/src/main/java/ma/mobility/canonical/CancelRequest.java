package ma.mobility.canonical;

/** Corps de POST /v1/bookings : confirme un maintien existant. */
public record CancelRequest(String reference) {
}