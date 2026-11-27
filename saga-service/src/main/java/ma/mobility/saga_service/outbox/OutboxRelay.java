package ma.mobility.saga_service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mobility.canonical.BookingEvent;
import ma.mobility.saga_service.entities.OutboxEvent;
import ma.mobility.saga_service.kafka.BookingEventPublisher;
import ma.mobility.saga_service.repository.OutboxEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository repo;
    private final BookingEventPublisher publisher;
    private final JsonMapper mapper;

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<OutboxEvent> pending = repo.findTop50ByPublishedAtIsNullOrderByIdAsc();
        for (OutboxEvent o : pending) {
            try {
                BookingEvent event = mapper.readValue(o.getPayload(), BookingEvent.class);
                publisher.publish(event);          // attend l'accuse du broker
                o.setPublishedAt(Instant.now());
                repo.save(o);
            } catch (Exception e) {
                log.warn("Publication outbox echouee (id={}), reessai au prochain passage : {}",
                        o.getId(), e.getMessage());
                return;                            // on s'arrete : on garde l'ordre
            }
        }
    }
}
