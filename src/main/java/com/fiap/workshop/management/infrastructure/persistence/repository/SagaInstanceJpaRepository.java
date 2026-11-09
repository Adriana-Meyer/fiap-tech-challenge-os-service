package com.fiap.workshop.management.infrastructure.persistence.repository;

import com.fiap.workshop.management.infrastructure.persistence.entity.SagaInstanceJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SagaInstanceJpaRepository extends JpaRepository<SagaInstanceJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SagaInstanceJpaEntity s WHERE s.id = :id")
    Optional<SagaInstanceJpaEntity> findByIdForUpdate(@Param("id") String id);

    Optional<SagaInstanceJpaEntity> findFirstByServiceOrderIdOrderByCreatedAtDesc(String serviceOrderId);
}
