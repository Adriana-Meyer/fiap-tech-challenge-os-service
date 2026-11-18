package com.fiap.workshop.management.application.usecase.serviceorder;

import com.fiap.workshop.management.application.dto.serviceorder.CompleteDiagnosisCommand;
import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.port.in.serviceorder.CompleteDiagnosisInputPort;
import com.fiap.workshop.management.application.saga.SagaOrchestrator;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records the diagnosis and asks Billing for the budget. The order only moves
 * to WAITING_APPROVAL when Billing answers (see {@link SagaOrchestrator}).
 */
@Service
public class CompleteDiagnosisUseCase implements CompleteDiagnosisInputPort {

    private final ServiceOrderRepository serviceOrderRepository;
    private final SagaOrchestrator sagaOrchestrator;

    public CompleteDiagnosisUseCase(ServiceOrderRepository serviceOrderRepository,
                                    SagaOrchestrator sagaOrchestrator) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID id, CompleteDiagnosisCommand command) {
        ServiceOrder order = serviceOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrder", id));
        order.completeDiagnosis(command.diagnosisNotes());
        ServiceOrder saved = serviceOrderRepository.save(order);
        sagaOrchestrator.requestBudget(saved);
        return ServiceOrderResponse.from(saved);
    }
}
