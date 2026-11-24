package ma.mobility.carshare_service.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
public class Reservation {
    @Id
    private String id;
    @Column(name = "hold_id") private String holdId;
    @Column(name = "booking_ref") private String bookingRef;
    @Column(name = "vehicle_id") private String vehicleId;
    @Column(name = "start_at") private Instant startAt;
    @Column(name = "end_at") private Instant endAt;
    @Enumerated(EnumType.STRING) private ReservationStatus status;
    @Column(name = "hold_expires_at") private Instant holdExpiresAt;
    @Column(name = "idempotency_key") private String idempotencyKey;
    @Version private Long version;
}