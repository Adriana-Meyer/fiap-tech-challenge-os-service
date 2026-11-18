package com.fiap.workshop.management.infrastructure.persistence.mapper;

import com.fiap.workshop.management.domain.model.saga.Compensation;
import com.fiap.workshop.management.domain.model.saga.SagaInstance;
import com.fiap.workshop.management.domain.model.saga.SagaStatus;
import com.fiap.workshop.management.domain.model.saga.SagaStep;
import com.fiap.workshop.management.infrastructure.persistence.entity.SagaInstanceJpaEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SagaInstanceMapperTest {

    private final SagaInstanceMapper mapper = new SagaInstanceMapper();

    @Test
    void shouldRoundTripASagaWithPendingCompensations() {
        LocalDateTime now = LocalDateTime.now();
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), UUID.randomUUID(), SagaStep.PROCESS_PAYMENT,
                SagaStatus.COMPENSATING, "pix", Set.of(Compensation.RELEASE_STOCK, Compensation.CANCEL_BUDGET),
                "declined", now, now);

        SagaInstanceJpaEntity entity = mapper.toEntity(saga);
        SagaInstance back = mapper.toDomain(entity);

        assertEquals("CANCEL_BUDGET,RELEASE_STOCK", entity.getPendingCompensations());
        assertEquals(saga.getId(), back.getId());
        assertEquals(saga.getServiceOrderId(), back.getServiceOrderId());
        assertEquals(SagaStep.PROCESS_PAYMENT, back.getStep());
        assertEquals(SagaStatus.COMPENSATING, back.getStatus());
        assertEquals("pix", back.getPaymentMethod());
        assertEquals(saga.getPendingCompensations(), back.getPendingCompensations());
        assertEquals("declined", back.getFailureReason());
    }

    @Test
    void shouldMapNoCompensationsToAnEmptyColumnAndBack() {
        SagaInstance saga = SagaInstance.start(UUID.randomUUID());

        SagaInstanceJpaEntity entity = mapper.toEntity(saga);

        assertEquals("", entity.getPendingCompensations());
        assertTrue(mapper.toDomain(entity).getPendingCompensations().isEmpty());
        assertNull(mapper.toDomain(entity).getPaymentMethod());
    }
}
