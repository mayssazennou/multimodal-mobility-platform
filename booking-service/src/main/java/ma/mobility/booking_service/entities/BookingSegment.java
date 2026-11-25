package ma.mobility.booking_service.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.mobility.canonical.BookingStatus;
import ma.mobility.canonical.Mode;

@Entity
@Table(name="booking_segment",  uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "mode"}))
@Getter
@Setter
@NoArgsConstructor
public class BookingSegment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private TravelBooking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;                        // ma.mobility.canonical.Mode

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "hold_ref")
    private String holdRef;

    @Column(name = "booking_ref")
    private String bookingRef;

    @Column(name = "price_minor")
    private Long priceMinor;


}
