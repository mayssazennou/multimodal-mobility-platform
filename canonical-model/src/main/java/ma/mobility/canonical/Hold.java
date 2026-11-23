package ma.mobility.canonical;

import java.time.Instant;

/** Maintien provisoire d'une ressource (phase 1 de la saga). holdId = mode + ":" + providerHoldId. */
public record Hold(
        String holdId,
        Mode mode,
        String providerHoldId,
        String offerId,
        int quantity,
        HoldStatus status;
        Instant createdAt
        Instant expiresAt;) {
}