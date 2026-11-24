package ma.mobility.carshare_service.dto;

import java.time.Instant;

public record HoldRequest(String offerId, Integer seats, Instant from, Instant to) {}

