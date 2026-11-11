package com.fiap.workshop.management.application.dto.saga;

import com.fiap.workshop.management.domain.model.saga.SagaInstance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SagaResponse(
        UUID id,
        UUID serviceOrderId,
        String step,
        String status,
        String paymentMethod,
        List<String> pendingCompensations,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SagaResponse from(SagaInstance saga) {
        return new SagaResponse(
                saga.getId(),
                saga.getServiceOrderId(),
                saga.getStep().name(),
                saga.getStatus().name(),
                saga.getPaymentMethod(),
                saga.getPendingCompensations().stream().map(Enum::name).toList(),
                saga.getFailureReason(),
                saga.getCreatedAt(),
                saga.getUpdatedAt());
    }
}
