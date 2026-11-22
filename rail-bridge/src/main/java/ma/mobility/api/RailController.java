package ma.mobility.rail.api;

import ma.mobility.rail.gateway.RailGateway;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Contrat REST commun v1 : GET /offers, POST /holds, POST /bookings, DELETE /bookings/{ref}. */
@RestController
@RequestMapping("/v1")
public class RailController {

    private final RailGateway gateway;
    private final IdempotencyStore idempotency;

    public RailController(RailGateway gateway, IdempotencyStore idempotency) {
        this.gateway = gateway;
        this.idempotency = idempotency;
    }

    @GetMapping("/offers")
    public List<Dto.Offer> offers(@RequestParam String origin,
                                  @RequestParam String destination,
                                  @RequestParam String date) {
        return gateway.search(origin, destination, date);
    }

    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public Dto.Hold hold(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                         @RequestBody Dto.HoldRequest request) {
        if (request.seats <= 0) {
            throw new IllegalArgumentException("seats doit être positif");
        }
        final String tripId = Ids.raw(request.offerId);
        final int seats = request.seats;
        return idempotency.once("hold", key, () -> gateway.hold(tripId, seats));
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public Dto.Booking confirm(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                               @RequestBody Dto.BookingRequest request) {
        final String holdId = Ids.raw(request.holdId);
        return idempotency.once("confirm", key, () -> gateway.confirm(holdId));
    }

    /** Compensation. Accepte un holdId ou un bookingId ; répond toujours 204, même si déjà annulé ou inconnu. */
    @DeleteMapping("/bookings/{ref:.+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable("ref") String ref) {
        gateway.cancel(Ids.raw(ref));
    }
}