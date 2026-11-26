package ma.mobility.carshare.api;

import ma.mobility.canonical.*;
import ma.mobility.carshare.CarshareService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Contrat REST commun v1 : GET /offers, POST /holds, POST /bookings, POST /cancellations. */
@RestController
@RequestMapping("/v1")
public class CarshareController {
    private final CarshareService service;

    public CarshareController(CarshareService service) { this.service = service; }

    @GetMapping("/offers")
    public List<Offer> offers(@RequestParam String origin, @RequestParam String destination, @RequestParam String date) {
        return service.search(origin, destination, date);
    }

    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public Hold hold(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                     @RequestBody HoldRequest request) {
        return service.hold(request.offerId(), request.seats(), key);
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public Booking confirm(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                           @RequestBody BookingRequest request) {
        return service.confirm(request.holdId(), key);
    }

    /** Compensation : accepte un holdId ou un bookingId ; répond toujours 204, même déjà annulé ou inconnu. */
    @PostMapping("/cancellations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@RequestBody CancelRequest request) {
        service.cancel(request.reference());
    }
}
