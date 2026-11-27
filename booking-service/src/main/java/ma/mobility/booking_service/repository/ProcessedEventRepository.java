package ma.mobility.booking_service.repository;

import ma.mobility.booking_service.entities.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent,String> {
}
