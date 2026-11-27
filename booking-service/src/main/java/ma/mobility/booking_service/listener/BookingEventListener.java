package ma.mobility.booking_service.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mobility.booking_service.service.BookingEventService;
import ma.mobility.canonical.BookingEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class BookingEventListener {
    private final BookingEventService bookingEventService;

    @KafkaListener(topics = "${booking.kafka.topic}")
    public void onEvent(BookingEvent event){
        log.info("\"Evenement recu : id={} type={} dossier={}",
                event!=null ? event.eventId():null,
                event != null? event.type():null,
                event!=null? event.bookingId():null);
        bookingEventService.apply(event);

    }
}
