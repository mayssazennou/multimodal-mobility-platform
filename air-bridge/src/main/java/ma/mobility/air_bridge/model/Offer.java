package ma.mobility.air_bridge.model;

import java.time.LocalDateTime;

public record Offer(
        String offerId,
        Mode mode,
        String providerRef,
        String origin,
        String destination,
        LocalDateTime departure,
        LocalDateTime arrival,
        Money price,
        int seatsAvailable
) {

}
