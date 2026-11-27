package ma.mobility.booking_service;

import ma.mobility.booking_service.entities.TravelBooking;
import ma.mobility.booking_service.erreur.InvalidEventException;
import ma.mobility.booking_service.repository.TravelBookingRepository;
import ma.mobility.booking_service.service.BookingEventService;
import ma.mobility.canonical.Booking;
import ma.mobility.canonical.BookingEvent;
import ma.mobility.canonical.BookingStatus;
import ma.mobility.canonical.Mode;
import ma.mobility.canonical.Money;
import ma.mobility.canonical.TravelStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BookingEventServiceTest {

    @Autowired
    BookingEventService service;
    @Autowired TravelBookingRepository repo;

    private static Booking rail() {
        return new Booking("RAIL:R-1", Mode.RAIL, "R-1", "hold-1", BookingStatus.CONFIRMED);
    }

    private TravelBooking load(String id) {
        return repo.findWithSegmentsById(id).orElseThrow();
    }

    @Test
    void nominal_started_puis_completed() {
        String id = "t-" + UUID.randomUUID();
        service.apply(BookingEvent.started(id, "t1"));
        assertThat(load(id).getStatus()).isEqualTo(TravelStatus.PENDING);

        service.apply(BookingEvent.completed(id, "t1", List.of(rail()), Money.mad(250000)));
        TravelBooking b = load(id);
        assertThat(b.getStatus()).isEqualTo(TravelStatus.CONFIRMED);
        assertThat(b.getTotalAmountMinor()).isEqualTo(250000L);
        assertThat(b.getSegments()).hasSize(1);
    }

    @Test
    void doublon_meme_eventId_est_ignore() {
        String id = "t-" + UUID.randomUUID();
        BookingEvent started = BookingEvent.started(id, "t1");
        service.apply(started);
        service.apply(started);                       // rejeu
        assertThat(repo.findById(id)).isPresent();
    }

    @Test
    void completed_avant_started_cree_le_dossier() {
        String id = "t-" + UUID.randomUUID();
        service.apply(BookingEvent.completed(id, "t1", List.of(rail()), Money.mad(100)));
        service.apply(BookingEvent.started(id, "t1"));        // arrive en retard
        assertThat(load(id).getStatus()).isEqualTo(TravelStatus.CONFIRMED);
    }

    @Test
    void etat_final_jamais_ecrase() {
        String id = "t-" + UUID.randomUUID();
        service.apply(BookingEvent.started(id, "t1"));
        service.apply(BookingEvent.cancelled(id, "t1", List.of(rail()), "plus de place"));
        service.apply(BookingEvent.completed(id, "t1", List.of(rail()), Money.mad(100)));
        TravelBooking b = load(id);
        assertThat(b.getStatus()).isEqualTo(TravelStatus.CANCELLED);
        assertThat(b.getFailureReason()).isEqualTo("plus de place");
    }

    @Test
    void evenement_invalide_est_rejete() {
        assertThatThrownBy(() -> service.apply(BookingEvent.completed("x", "t1", List.of(), null)))
                .isInstanceOf(InvalidEventException.class);
    }
}