package com.fiap.workshop.management.domain.repository;

import com.fiap.workshop.management.domain.model.saga.SagaInstance;

import java.util.Optional;
import java.util.UUID;

public interface SagaInstanceRepository {

    SagaInstance save(SagaInstance saga);

    Optional<SagaInstance> findById(UUID sagaId);

    /**
     * Loads the saga locking its row until the transaction ends. Answers from
     * different services can arrive together (the acknowledgements of two
     * compensations, for one); without the lock each would read the same
     * state and the last write would erase the other's.
     */
    Optional<SagaInstance> findByIdForUpdate(UUID sagaId);

    /** The most recent saga of the order, whatever its status. */
    Optional<SagaInstance> findLatestByServiceOrderId(UUID serviceOrderId);
}
