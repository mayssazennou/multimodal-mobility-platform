package ma.mobility.booking_service.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.mobility.canonical.TravelStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
@Getter
@Setter
@Entity
@Table(name="travel_booking")
public class TravelBooking {
    @Id
    private String id;
    @Column(name="traveler_id", nullable = false)
    private String travelerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TravelStatus status;

    @Column(name = "total_amount_minor")
    private Long totalAmountMinor;

    private String currency;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingSegment> segments = new ArrayList<>();

    public void addSegment(BookingSegment s) {
        s.setBooking(this);
        segments.add(s);
    }
}
