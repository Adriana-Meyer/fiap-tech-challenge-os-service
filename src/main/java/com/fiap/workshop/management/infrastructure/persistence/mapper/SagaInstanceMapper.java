package com.fiap.workshop.management.infrastructure.persistence.mapper;

import com.fiap.workshop.management.domain.model.saga.Compensation;
import com.fiap.workshop.management.domain.model.saga.SagaInstance;
import com.fiap.workshop.management.domain.model.saga.SagaStatus;
import com.fiap.workshop.management.domain.model.saga.SagaStep;
import com.fiap.workshop.management.infrastructure.persistence.entity.SagaInstanceJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class SagaInstanceMapper {

    public SagaInstance toDomain(SagaInstanceJpaEntity e) {
        return new SagaInstance(UUID.fromString(e.getId()), UUID.fromString(e.getServiceOrderId()),
                SagaStep.valueOf(e.getStep()), SagaStatus.valueOf(e.getStatus()), e.getPaymentMethod(),
                parse(e.getPendingCompensations()), e.getFailureReason(), e.getCreatedAt(), e.getUpdatedAt());
    }

    public SagaInstanceJpaEntity toEntity(SagaInstance s) {
        return new SagaInstanceJpaEntity(s.getId().toString(), s.getServiceOrderId().toString(),
                s.getStep().name(), s.getStatus().name(), s.getPaymentMethod(),
                format(s.getPendingCompensations()), s.getFailureReason(), s.getCreatedAt(), s.getUpdatedAt());
    }

    private static String format(Set<Compensation> compensations) {
        return compensations.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static Set<Compensation> parse(String value) {
        if (value == null || value.isBlank()) {
            return EnumSet.noneOf(Compensation.class);
        }
        return Arrays.stream(value.split(",")).map(Compensation::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Compensation.class)));
    }
}
