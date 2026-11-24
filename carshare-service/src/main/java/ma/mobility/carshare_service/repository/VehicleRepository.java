package ma.mobility.carshare_service.repository;

import ma.mobility.carshare_service.entities.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, String> {

    @Query("""
        select v from Vehicle v
        where v.city = :city
          and not exists (
            select 1 from Reservation r
            where r.vehicleId = v.id
              and r.status in (ma.mobility.carshare.domain.ReservationStatus.HELD,
                               ma.mobility.carshare.domain.ReservationStatus.CONFIRMED)
              and r.startAt < :to and r.endAt > :from)
        """)
    List<Vehicle> findFree(String city, Instant from, Instant to);
}