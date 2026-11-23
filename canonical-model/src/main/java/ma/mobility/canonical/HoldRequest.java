package ma.mobility.canonical;

/** Corps de POST /v1/holds. */
public record HoldRequest(String offerId, int seats) {
}