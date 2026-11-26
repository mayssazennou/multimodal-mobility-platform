package ma.mobility.saga_service.repository;


import ma.mobility.saga_service.domain.SagaState;
import ma.mobility.saga_service.entities.SagaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaRepository extends JpaRepository<SagaEntity, UUID> {

    Optional<SagaEntity> findByIdempotencyKey(String idempotencyKey);

    /** Pour le job de reprise après crash. */
    List<SagaEntity> findByStateIn(Collection<SagaState> states);
}