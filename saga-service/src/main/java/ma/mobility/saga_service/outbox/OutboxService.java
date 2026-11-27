package ma.mobility.saga_service.outbox;

import lombok.RequiredArgsConstructor;
import ma.mobility.canonical.BookingEvent;
import ma.mobility.saga_service.entities.OutboxEvent;
import ma.mobility.saga_service.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repo;
    private final JsonMapper mapper;

    /** MANDATORY : refuse de s'executer hors d'une transaction (garde-fou de l'outbox). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(BookingEvent e) {
        OutboxEvent o = new OutboxEvent();
        o.setEventId(e.eventId());
        o.setBookingId(e.bookingId());
        o.setPayload(mapper.writeValueAsString(e));
        o.setCreatedAt(Instant.now());
        repo.save(o);
    }
}
