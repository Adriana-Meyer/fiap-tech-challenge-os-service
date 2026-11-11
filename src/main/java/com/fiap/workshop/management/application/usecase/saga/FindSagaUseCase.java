package com.fiap.workshop.management.application.usecase.saga;

import com.fiap.workshop.management.application.dto.saga.SagaResponse;
import com.fiap.workshop.management.application.port.in.saga.FindSagaInputPort;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.repository.SagaInstanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FindSagaUseCase implements FindSagaInputPort {

    private final SagaInstanceRepository sagaRepository;

    public FindSagaUseCase(SagaInstanceRepository sagaRepository) {
        this.sagaRepository = sagaRepository;
    }

    @Transactional(readOnly = true)
    public SagaResponse findLatestByServiceOrderId(UUID serviceOrderId) {
        return sagaRepository.findLatestByServiceOrderId(serviceOrderId)
                .map(SagaResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Saga of service order", serviceOrderId));
    }
}
