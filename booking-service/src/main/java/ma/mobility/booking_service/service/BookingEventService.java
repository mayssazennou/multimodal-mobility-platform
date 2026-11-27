package ma.mobility.booking_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mobility.booking_service.entities.BookingSegment;
import ma.mobility.booking_service.entities.ProcessedEvent;
import ma.mobility.booking_service.entities.TravelBooking;
import ma.mobility.booking_service.erreur.InvalidEventException;
import ma.mobility.booking_service.repository.ProcessedEventRepository;
import ma.mobility.booking_service.repository.TravelBookingRepository;
import ma.mobility.canonical.Booking;
import ma.mobility.canonical.BookingEvent;
import ma.mobility.canonical.BookingEventType;
import ma.mobility.canonical.BookingStatus;
import ma.mobility.canonical.TravelStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEventService {

    private final TravelBookingRepository bookingRepo;
    private final ProcessedEventRepository processedRepo;

    /** Une seule transaction : application de l'evenement + marquage "traite". */
    @Transactional
    public void apply(BookingEvent e) {
        validate(e);

        if (processedRepo.existsById(e.eventId())) {
            log.info("Evenement {} deja traite, ignore", e.eventId());
            return;
        }

        TravelBooking b = bookingRepo.findWithSegmentsById(e.bookingId()).orElse(null);

        switch (e.type()) {
            case BOOKING_STARTED -> onStarted(e, b);
            case BOOKING_COMPLETED -> onFinished(e, b, TravelStatus.CONFIRMED, BookingStatus.CONFIRMED);
            case BOOKING_CANCELLED -> onFinished(e, b, TravelStatus.CANCELLED, BookingStatus.CANCELLED);
        }

        processedRepo.save(new ProcessedEvent(e.eventId(), Instant.now()));
    }

    private void onStarted(BookingEvent e, TravelBooking existing) {
        if (existing != null) {
            log.info("Dossier {} existe deja, BOOKING_STARTED ignore", e.bookingId());
            return;
        }
        bookingRepo.save(newBooking(e));
    }

    private void onFinished(BookingEvent e, TravelBooking existing,
                            TravelStatus finalStatus, BookingStatus segmentStatus) {
        TravelBooking b = existing != null ? existing : newBooking(e);   // creation a la volee

        if (b.getStatus() != TravelStatus.PENDING) {
            log.warn("Dossier {} deja {}, evenement {} ({}) ignore",
                    b.getId(), b.getStatus(), e.eventId(), e.type());
            return;
        }

        b.setStatus(finalStatus);
        b.setUpdatedAt(Instant.now());
        if (finalStatus == TravelStatus.CONFIRMED) {
            b.setTotalAmountMinor(e.total().amountMinor());
            b.setCurrency(e.total().currency());
        } else {
            b.setFailureReason(e.reason());
        }
        for (Booking x : e.bookings()) {
            upsertSegment(b, x, segmentStatus);
        }
        bookingRepo.save(b);
    }

    private void upsertSegment(TravelBooking b, Booking x, BookingStatus status) {
        BookingSegment s = b.getSegments().stream()
                .filter(seg -> seg.getMode() == x.mode())
                .findFirst()
                .orElseGet(() -> {
                    BookingSegment n = new BookingSegment();
                    n.setMode(x.mode());
                    b.addSegment(n);
                    return n;
                });
        s.setStatus(status);
        s.setHoldRef(x.holdId());
        s.setBookingRef(x.providerBookingRef());
    }

    private TravelBooking newBooking(BookingEvent e) {
        TravelBooking b = new TravelBooking();
        b.setId(e.bookingId());
        b.setTravelerId(e.travelerId());
        b.setStatus(TravelStatus.PENDING);
        Instant now = Instant.now();
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        return b;
    }

    private void validate(BookingEvent e) {
        if (e == null) throw new InvalidEventException("evenement nul");
        if (isBlank(e.eventId())) throw new InvalidEventException("eventId manquant");
        if (e.type() == null) throw new InvalidEventException("type manquant");
        if (isBlank(e.bookingId())) throw new InvalidEventException("bookingId manquant");
        if (isBlank(e.travelerId())) throw new InvalidEventException("travelerId manquant");
        if (e.type() == BookingEventType.BOOKING_COMPLETED && e.total() == null)
            throw new InvalidEventException("BOOKING_COMPLETED sans total");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}