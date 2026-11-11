package com.fiap.workshop.management.infrastructure.persistence.repository;

import com.fiap.workshop.management.infrastructure.persistence.entity.ProcessedMessageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageJpaRepository extends JpaRepository<ProcessedMessageJpaEntity, String> {
}
