package ma.mobility.carshare.domain;

import jakarta.persistence.*;
import ma.mobility.canonical.HoldStatus;
import java.time.Instant;

@Entity
@Table(name = "car_hold")
public class HoldEntity {
    @Id private String id;               // identifiant brut : HOLD-CAR-<uuid>
    private String offerRef;
    private int quantity;
    @Enumerated(EnumType.STRING) private HoldStatus status;
    private Instant createdAt;
    private Instant expiresAt;

    protected HoldEntity() { }

    public HoldEntity(String id, String offerRef, int quantity, HoldStatus status, Instant createdAt, Instant expiresAt) {
        this.id = id; this.offerRef = offerRef; this.quantity = quantity;
        this.status = status; this.createdAt = createdAt; this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public String getOfferRef() { return offerRef; }
    public int getQuantity() { return quantity; }
    public HoldStatus getStatus() { return status; }
    public void setStatus(HoldStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
