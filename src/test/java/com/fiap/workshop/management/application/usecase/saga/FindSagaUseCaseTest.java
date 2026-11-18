package com.fiap.workshop.management.application.usecase.saga;

import com.fiap.workshop.management.application.dto.saga.SagaResponse;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.model.saga.SagaInstance;
import com.fiap.workshop.management.domain.repository.SagaInstanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FindSagaUseCaseTest {

    @Mock
    private SagaInstanceRepository sagaRepository;

    @InjectMocks
    private FindSagaUseCase useCase;

    @Test
    void shouldReturnTheLatestSagaOfTheOrder() {
        UUID orderId = UUID.randomUUID();
        SagaInstance saga = SagaInstance.start(orderId);
        when(sagaRepository.findLatestByServiceOrderId(orderId)).thenReturn(Optional.of(saga));

        SagaResponse response = useCase.findLatestByServiceOrderId(orderId);

        assertEquals(saga.getId(), response.id());
        assertEquals("GENERATE_BUDGET", response.step());
        assertEquals("RUNNING", response.status());
    }

    @Test
    void shouldFailWhenTheOrderHasNoSaga() {
        UUID orderId = UUID.randomUUID();
        when(sagaRepository.findLatestByServiceOrderId(orderId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> useCase.findLatestByServiceOrderId(orderId));
    }
}
