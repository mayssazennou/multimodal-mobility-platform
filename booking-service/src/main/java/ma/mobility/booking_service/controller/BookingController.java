package ma.mobility.booking_service.controller;

import lombok.RequiredArgsConstructor;
import ma.mobility.booking_service.erreur.BookingNotFoundException;
import ma.mobility.booking_service.repository.TravelBookingRepository;
import ma.mobility.canonical.Booking;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final TravelBookingRepository bookings;
    private final TravelBookingMapper mapper;

    @GetMapping("/{id}")
    public TravelBookingView get(@PathVariable String id){
        return bookings.findWithSegmentsById(id).map(mapper::view).orElseThrow(() -> new BookingNotFoundException(id));
    }
    @GetMapping
    public List<TravelBookingView> byTraveler(@RequestParam String travelerId){
        return bookings.findByTravelerIdOrderByCreatedAt(travelerId).stream().map(mapper::view).toList();
    }



}
