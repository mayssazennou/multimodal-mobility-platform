package ma.mobility.carshare_service.controller;

import lombok.RequiredArgsConstructor;

import ma.mobility.carshare_service.dto.*;
import ma.mobility.carshare_service.service.CarShareService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class CarShareController {

    private final CarShareService carShareService;

    @GetMapping("/offers")
    public List<Offer> search(@RequestParam String city,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return carShareService.searchOffers(city, from, to);
    }

    @PostMapping("/holds")
    public Hold hold(@RequestBody HoldRequest request,
                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return carShareService.hold(request.offerId(), request.from(), request.to(), idempotencyKey);
    }

    @PostMapping("/holds/{holdId}/confirm")
    public Booking confirm(@PathVariable String holdId,
                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return carShareService.confirm(holdId, idempotencyKey);
    }

    @PostMapping("/cancellations")
    public ResponseEntity<Void> cancel(@RequestBody CancelRequest request) {
        carShareService.cancel(request.reference());
        return ResponseEntity.noContent().build();   // 204 toujours, même si inconnu ou déjà annulé
    }
}
