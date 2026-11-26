package ma.mobility.carshare.domain;

import jakarta.persistence.*;
import java.time.LocalTime;

/** Une formule de location : lieu de prise en charge (origin), lieu de restitution (destination), stock de véhicules. */
@Entity
@Table(name = "car_offer")
public class VehicleOfferEntity {
    @Id private String providerRef;      // ex. CAR-PAR-PAR-0900
    private String origin;
    private String destination;
    private LocalTime pickupTime;
    private LocalTime returnTime;
    private long priceMinor;
    private int available;

    protected VehicleOfferEntity() { }

    public VehicleOfferEntity(String providerRef, String origin, String destination,
                              LocalTime pickupTime, LocalTime returnTime, long priceMinor, int available) {
        this.providerRef = providerRef; this.origin = origin; this.destination = destination;
        this.pickupTime = pickupTime; this.returnTime = returnTime;
        this.priceMinor = priceMinor; this.available = available;
    }

    public String getProviderRef() { return providerRef; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public LocalTime getPickupTime() { return pickupTime; }
    public LocalTime getReturnTime() { return returnTime; }
    public long getPriceMinor() { return priceMinor; }
    public int getAvailable() { return available; }
}
