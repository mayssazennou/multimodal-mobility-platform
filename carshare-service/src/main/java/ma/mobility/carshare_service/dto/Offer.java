package ma.mobility.carshare_service.dto;

import java.time.Instant;

public record Offer(String offerId, String vehicleId, String plate, String city,
                    String category, Instant from, Instant to, long priceMinor) {}