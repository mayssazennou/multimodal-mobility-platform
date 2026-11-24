package ma.mobility.carshare_service.repository;

import ma.mobility.carshare_service.entities.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
    Optional<Reservation> findByHoldId(String holdId);
    Optional<Reservation> findByBookingRef(String bookingRef);
    Optional<Reservation> findByIdempotencyKey(String key);

    @Modifying
    @Query("""
        update Reservation r set r.status = ma.mobility.carshare.domain.ReservationStatus.EXPIRED
        where r.status = ma.mobility.carshare.domain.ReservationStatus.HELD
          and r.holdExpiresAt < :now
        """)
    int expireHolds(Instant now);
}