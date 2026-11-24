package ma.mobility.carshare_service.dto;

import java.time.Instant;

public record Hold(String holdId, String offerId, Instant expiresAt) {}

