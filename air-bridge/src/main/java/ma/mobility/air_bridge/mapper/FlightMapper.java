package ma.mobility.air_bridge.mapper;


import ma.mobility.air_bridge.model.Mode;
import ma.mobility.air_bridge.model.Offer;
import ma.mobility.airbridge.generated.Flight;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class FlightMapper {
    public static final String AIR_PREFIX = "AIR:";
    public Offer toOffer(Flight flight){
        return new Offer(
                "AIR:"+flight.getFlightId(),
                Mode.AIR,
                flight.getFlightId(),
                flight.getOrigin(),
                flight.getDestination(),
                LocalDateTime.parse(flight.getDeparture()),
                LocalDateTime.parse(flight.getArrival()),
                new Offer.Money(flight.getPriceMinor(),"MAD"),
                flight.getSeatsAvailable()

        );
    }
}
