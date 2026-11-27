package ma.mobility.saga_service;

import ma.mobility.canonical.BookingEvent;
import ma.mobility.saga_service.kafka.BookingEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class BookingEventPublisherTest {
    @Autowired
    BookingEventPublisher publisher;

    @Test
    void publi_un_evenement_started() throws Exception{
        publisher.publish(BookingEvent.started("saga-pub-1","t1"));
    }
}
