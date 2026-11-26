package ma.mobility.carshare.domain;

import jakarta.persistence.*;
import ma.mobility.canonical.BookingStatus;

@Entity
@Table(name = "car_booking")
public class BookingEntity {
    @Id private String id;               // identifiant brut : BK-CAR-<uuid>
    private String holdId;               // identifiant brut du maintien
    @Enumerated(EnumType.STRING) private BookingStatus status;

    protected BookingEntity() { }

    public BookingEntity(String id, String holdId, BookingStatus status) {
        this.id = id; this.holdId = holdId; this.status = status;
    }

    public String getId() { return id; }
    public String getHoldId() { return holdId; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
}
