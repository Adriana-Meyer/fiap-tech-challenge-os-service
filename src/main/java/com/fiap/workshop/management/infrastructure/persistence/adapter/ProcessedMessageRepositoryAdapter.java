package com.fiap.workshop.management.infrastructure.persistence.adapter;

import com.fiap.workshop.management.domain.repository.ProcessedMessageRepository;
import com.fiap.workshop.management.infrastructure.persistence.entity.ProcessedMessageJpaEntity;
import com.fiap.workshop.management.infrastructure.persistence.repository.ProcessedMessageJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public class ProcessedMessageRepositoryAdapter implements ProcessedMessageRepository {

    private final ProcessedMessageJpaRepository jpaRepository;

    public ProcessedMessageRepositoryAdapter(ProcessedMessageJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Two copies of the same message handled at the same instant would both
     * pass the check; the primary key then fails the second insert, its
     * transaction rolls back and the message is retried, finding it recorded.
     */
    @Override
    public boolean markProcessed(UUID messageId) {
        String id = messageId.toString();
        if (jpaRepository.existsById(id)) {
            return false;
        }
        jpaRepository.save(new ProcessedMessageJpaEntity(id, LocalDateTime.now()));
        return true;
    }
}
