package ma.mobility.air_bridge.controller;


import lombok.RequiredArgsConstructor;
import ma.mobility.air_bridge.dto.CancelRequest;
import ma.mobility.air_bridge.dto.HoldRequest;
import ma.mobility.air_bridge.model.Booking;
import ma.mobility.air_bridge.model.Hold;
import ma.mobility.air_bridge.model.Offer;
import ma.mobility.air_bridge.service.AirService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AirController {

    private final AirService airService;

    @GetMapping("/offers")
    public List<Offer> search(@RequestParam String origin,
                              @RequestParam String destination,
                              @RequestParam String date) {
        return airService.searchOffers(origin, destination, date);
    }

    @PostMapping("/holds")
    public Hold hold(@RequestBody HoldRequest request,
                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // idempotencyKey : lue mais pas encore utilisée (étape suivante)
        return airService.hold(request.offerId(), request.seats());
    }

    @PostMapping("/holds/{holdId}/confirm")
    public Booking confirm(@PathVariable String holdId,
                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return airService.confirm(holdId);
    }

    @PostMapping("/cancellations")
    public ResponseEntity<Void> cancel(@RequestBody CancelRequest request) {
        airService.cancel(request.reference());
        return ResponseEntity.noContent().build();   // 204, toujours en cas de succès
    }
}
