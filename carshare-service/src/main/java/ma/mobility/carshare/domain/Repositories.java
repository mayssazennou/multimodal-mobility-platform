package ma.mobility.carshare.domain;

import ma.mobility.canonical.HoldStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class Repositories {
    private Repositories() { }

    public interface OfferRepository extends JpaRepository<VehicleOfferEntity, String> {
        List<VehicleOfferEntity> findByOriginIgnoreCaseAndDestinationIgnoreCase(String origin, String destination);

        /** Décrément atomique : 0 ligne modifiée = stock insuffisant (pas de survente même en concurrence). */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("update VehicleOfferEntity o set o.available = o.available - :n where o.providerRef = :ref and o.available >= :n")
        int tryReserve(@Param("ref") String ref, @Param("n") int n);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("update VehicleOfferEntity o set o.available = o.available + :n where o.providerRef = :ref")
        int release(@Param("ref") String ref, @Param("n") int n);
    }

    public interface HoldRepository extends JpaRepository<HoldEntity, String> {
        List<HoldEntity> findByStatusAndExpiresAtBefore(HoldStatus status, Instant before);
    }

    public interface BookingRepository extends JpaRepository<BookingEntity, String> {
        Optional<BookingEntity> findByHoldId(String holdId);
    }

    public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> { }
}
