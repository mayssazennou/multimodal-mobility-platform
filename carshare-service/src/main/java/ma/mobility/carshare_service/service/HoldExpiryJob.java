package ma.mobility.carshare_service.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mobility.carshare_service.repository.ReservationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class HoldExpiryJob {

    private final ReservationRepository reservations;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void expire() {
        int n = reservations.expireHolds(Instant.now());
        if (n > 0) log.info("{} hold(s) expiré(s)", n);
    }
}