package ma.mobility.saga_service.api;

import jakarta.validation.Valid;
import ma.mobility.saga_service.orchestrator.BookingSagaOrchestrator;
import ma.mobility.saga_service.repository.SagaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingSagaOrchestrator orchestrator;
    private final SagaRepository repo;

    public BookingController(BookingSagaOrchestrator orchestrator, SagaRepository repo) {
        this.orchestrator = orchestrator;
        this.repo = repo;
    }

    @PostMapping
    public BookingResponse book(@RequestHeader("Idempotency-Key") String key,
                                @Valid @RequestBody BookingRequest req) {
        return BookingResponse.from(orchestrator.start(req, key));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> get(@PathVariable UUID id) {
        return repo.findById(id)
                .map(s -> ResponseEntity.ok(BookingResponse.from(s)))
                .orElse(ResponseEntity.notFound().build());
    }
}
