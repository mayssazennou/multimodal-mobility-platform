package ma.mobility.saga_service.entities;


import jakarta.persistence.*;
import ma.mobility.saga_service.domain.SagaState;
import ma.mobility.saga_service.domain.StepType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "saga")
public class SagaEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaState state = SagaState.STARTED;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "saga", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SagaStepEntity> steps = new ArrayList<>();

    protected SagaEntity() {}

    public SagaEntity(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void transitionTo(SagaState newState) {
        this.state = newState;
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public SagaStepEntity step(StepType type) {
        return steps.stream()
                .filter(s -> s.getType() == type)
                .findFirst()
                .orElseGet(() -> {
                    SagaStepEntity s = new SagaStepEntity(this, type);
                    steps.add(s);
                    return s;
                });
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public SagaState getState() { return state; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<SagaStepEntity> getSteps() { return steps; }
}