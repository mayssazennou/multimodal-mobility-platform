package ma.mobility.saga_service.entities;

import jakarta.persistence.*;
import ma.mobility.saga_service.domain.StepPhase;
import ma.mobility.saga_service.domain.StepStatus;
import ma.mobility.saga_service.domain.StepType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_step",
        uniqueConstraints = @UniqueConstraint(columnNames = {"saga_id", "type"}))
public class SagaStepEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saga_id")
    private SagaEntity saga;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepPhase phase = StepPhase.HOLD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepStatus status = StepStatus.PENDING;

    /** holdId après le hold, remplacé par le bookingRef après le confirm. */
    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SagaStepEntity() {}

    SagaStepEntity(SagaEntity saga, StepType type) {
        this.saga = saga;
        this.type = type;
    }

    public void markDone(StepPhase phase, String ref) {
        this.phase = phase;
        this.status = StepStatus.DONE;
        if (ref != null) this.externalRef = ref;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(StepPhase phase, String error) {
        this.phase = phase;
        this.status = StepStatus.FAILED;
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    public void markUncertain(StepPhase phase, String error) {
        this.phase = phase;
        this.status = StepStatus.UNCERTAIN;
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    /** Y a-t-il quelque chose à annuler chez le partenaire ? */
    public boolean hasReference() {
        return externalRef != null && !externalRef.isBlank();
    }

    public UUID getId() { return id; }
    public SagaEntity getSaga() { return saga; }
    public StepType getType() { return type; }
    public StepPhase getPhase() { return phase; }
    public StepStatus getStatus() { return status; }
    public String getExternalRef() { return externalRef; }
    public String getLastError() { return lastError; }
    public Instant getUpdatedAt() { return updatedAt; }
}