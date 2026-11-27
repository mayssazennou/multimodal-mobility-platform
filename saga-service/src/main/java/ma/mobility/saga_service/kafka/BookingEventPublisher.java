package ma.mobility.saga_service.kafka;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mobility.canonical.BookingEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class BookingEventPublisher {

    private final KafkaTemplate<String, BookingEvent> kafka;
    @Value("${booking.kafka.topic}")
    private String topic;

    /** Publie et ATTEND l'accuse du broker : en cas d'echec, l'exception remonte. */
    public void publish(BookingEvent event) throws Exception{
        kafka.send(topic,event.bookingId(),event).get(10, TimeUnit.SECONDS);
        log.info("Evenement publie : id={} type={} dossier={}",event.eventId(),event.type(),event.bookingId());
    }

}
