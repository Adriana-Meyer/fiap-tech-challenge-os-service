package com.fiap.workshop.management.infrastructure.persistence.adapter;

import com.fiap.workshop.management.domain.model.saga.SagaInstance;
import com.fiap.workshop.management.domain.repository.SagaInstanceRepository;
import com.fiap.workshop.management.infrastructure.persistence.mapper.SagaInstanceMapper;
import com.fiap.workshop.management.infrastructure.persistence.repository.SagaInstanceJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class SagaInstanceRepositoryAdapter implements SagaInstanceRepository {

    private final SagaInstanceJpaRepository jpaRepository;
    private final SagaInstanceMapper mapper;

    public SagaInstanceRepositoryAdapter(SagaInstanceJpaRepository jpaRepository, SagaInstanceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public SagaInstance save(SagaInstance saga) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(saga)));
    }

    @Override
    public Optional<SagaInstance> findById(UUID sagaId) {
        return jpaRepository.findById(sagaId.toString()).map(mapper::toDomain);
    }

    @Override
    public Optional<SagaInstance> findByIdForUpdate(UUID sagaId) {
        return jpaRepository.findByIdForUpdate(sagaId.toString()).map(mapper::toDomain);
    }

    @Override
    public Optional<SagaInstance> findLatestByServiceOrderId(UUID serviceOrderId) {
        return jpaRepository.findFirstByServiceOrderIdOrderByCreatedAtDesc(serviceOrderId.toString())
                .map(mapper::toDomain);
    }
}
