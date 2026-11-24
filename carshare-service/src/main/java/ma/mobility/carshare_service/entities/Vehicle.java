package ma.mobility.carshare_service.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vehicle")
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {
    @Id
    private String id;
    private String plate;
    private String city;
    private String category;
    @Column(name = "price_per_hour_minor") private long pricePerHourMinor;
}