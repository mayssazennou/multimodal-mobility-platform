package ma.mobility.saga_service.orchestrator;

import ma.mobility.saga_service.api.BookingRequest;
import ma.mobility.saga_service.client.PartnerClient;
import ma.mobility.saga_service.domain.SagaState;
import ma.mobility.saga_service.domain.StepPhase;
import ma.mobility.saga_service.domain.StepStatus;
import ma.mobility.saga_service.domain.StepType;
import ma.mobility.saga_service.entities.SagaEntity;
import ma.mobility.saga_service.entities.SagaStepEntity;
import ma.mobility.saga_service.exception.PartnerRejectedException;
import ma.mobility.saga_service.exception.PartnerUncertainException;
import ma.mobility.saga_service.repository.SagaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class BookingSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BookingSagaOrchestrator.class);

    private final SagaRepository repo;
    private final List<PartnerClient> partners;   // ordre : RAIL, AIR, CAR

    public BookingSagaOrchestrator(SagaRepository repo, List<PartnerClient> partners) {
        this.repo = repo;
        this.partners = partners.stream()
                .sorted(Comparator.comparing(PartnerClient::type))
                .toList();
    }

    // ------------------------------------------------------------------
    // Point d'entrée + idempotence de la réservation entière
    // ------------------------------------------------------------------
    public SagaEntity start(BookingRequest req, String idempotencyKey) {
        Optional<SagaEntity> existing = repo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Clé {} déjà connue -> saga existante renvoyée", idempotencyKey);
            return existing.get();
        }

        SagaEntity saga;
        try {
            saga = repo.save(new SagaEntity(idempotencyKey));
        } catch (DataIntegrityViolationException e) {
            // deux requêtes simultanées : la contrainte UNIQUE a départagé
            return repo.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }
        return run(saga, req);
    }

    // ------------------------------------------------------------------
    // Déroulement : HOLD x3 puis CONFIRM x3
    // ------------------------------------------------------------------
    private SagaEntity run(SagaEntity saga, BookingRequest req) {
        String key = saga.getIdempotencyKey();

        // ---------- Phase 1 : HOLD ----------
        saga = transition(saga, SagaState.HOLDING);
        for (PartnerClient p : partners) {
            SagaEntity current = saga;
            SagaStepEntity step = current.step(p.type());
            try {
                String holdId = p.hold(req.resourceFor(p.type()), req.seats(),
                        opKey(key, p.type(), StepPhase.HOLD));
                step.markDone(StepPhase.HOLD, holdId);
                saga = repo.save(current);
            } catch (PartnerRejectedException e) {
                step.markFailed(StepPhase.HOLD, e.getMessage());
                return compensate(current, "Hold refusé : " + e.getMessage());
            } catch (PartnerUncertainException e) {
                step.markUncertain(StepPhase.HOLD, e.getMessage());
                return compensate(current, "Hold incertain : " + e.getMessage());
            }
        }
        saga = transition(saga, SagaState.HELD);

        // ---------- Phase 2 : CONFIRM ----------
        saga = transition(saga, SagaState.CONFIRMING);
        for (PartnerClient p : partners) {
            SagaEntity current = saga;
            SagaStepEntity step = current.step(p.type());
            try {
                String ref = p.confirm(step.getExternalRef(),
                        opKey(key, p.type(), StepPhase.CONFIRM));
                step.markDone(StepPhase.CONFIRM, ref);   // le bookingRef remplace le holdId
                saga = repo.save(current);
            } catch (PartnerRejectedException e) {
                step.markFailed(StepPhase.CONFIRM, e.getMessage());
                return compensate(current, "Confirm refusé : " + e.getMessage());
            } catch (PartnerUncertainException e) {
                step.markUncertain(StepPhase.CONFIRM, e.getMessage());
                return compensate(current, "Confirm incertain : " + e.getMessage());
            }
        }

        return transition(saga, SagaState.CONFIRMED);
    }

    // ------------------------------------------------------------------
    // Compensation : cancel(référence) sur tout ce qui a une référence
    // ------------------------------------------------------------------
    private SagaEntity compensate(SagaEntity saga, String reason) {
        log.warn("Compensation de la saga {} : {}", saga.getId(), reason);
        saga.fail(reason);

        // Un confirm au résultat inconnu ne peut pas être garanti annulé -> intervention humaine
        boolean manual = saga.getSteps().stream().anyMatch(s ->
                s.getPhase() == StepPhase.CONFIRM && s.getStatus() == StepStatus.UNCERTAIN);

        saga = transition(saga, SagaState.COMPENSATING);

        List<PartnerClient> reversed = new ArrayList<>(partners);
        Collections.reverse(reversed);

        for (PartnerClient p : reversed) {
            SagaStepEntity step = findStep(saga, p.type());
            if (step == null || !step.hasReference()) {
                continue;   // rien à annuler chez ce partenaire
            }
            try {
                p.cancel(step.getExternalRef(), opKey(saga.getIdempotencyKey(), p.type(), StepPhase.CANCEL));
                step.markDone(StepPhase.CANCEL, null);
            } catch (PartnerRejectedException | PartnerUncertainException e) {
                step.markFailed(StepPhase.CANCEL, e.getMessage());
                manual = true;
            }
            saga = repo.save(saga);
        }

        return transition(saga, manual ? SagaState.FAILED_MANUAL : SagaState.COMPENSATED);
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    /** On réassigne toujours le résultat de save() : c'est l'instance gérée par JPA. */
    private SagaEntity transition(SagaEntity saga, SagaState state) {
        saga.transitionTo(state);
        return repo.save(saga);
    }

    private SagaStepEntity findStep(SagaEntity saga, StepType type) {
        return saga.getSteps().stream()
                .filter(s -> s.getType() == type)
                .findFirst()
                .orElse(null);
    }

    /** Clé envoyée au bridge : clé-client:PARTENAIRE:OPERATION */
    private String opKey(String sagaKey, StepType type, StepPhase phase) {
        return sagaKey + ":" + type + ":" + phase;
    }
}