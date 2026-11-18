package com.fiap.workshop.management.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_message")
public class ProcessedMessageJpaEntity {

    @Id
    @Column(name = "message_id", columnDefinition = "CHAR(36)", nullable = false)
    private String messageId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected ProcessedMessageJpaEntity() {}

    public ProcessedMessageJpaEntity(String messageId, LocalDateTime processedAt) {
        this.messageId = messageId;
        this.processedAt = processedAt;
    }

    public String getMessageId() { return messageId; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
