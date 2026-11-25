package ma.mobility.booking_service.repository;

import ma.mobility.booking_service.entities.TravelBooking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TravelBookingRepository extends JpaRepository<TravelBooking,String> {
    @EntityGraph(attributePaths = "segments")
    Optional<TravelBooking> findWithSegmentsById(String id);

    @EntityGraph(attributePaths = "segments")
    List<TravelBooking> findByTravelerIdOrderByCreatedAt(String travelerId);
}
