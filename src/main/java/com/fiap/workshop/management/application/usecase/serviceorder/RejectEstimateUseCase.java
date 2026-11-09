package com.fiap.workshop.management.application.usecase.serviceorder;

import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.port.in.serviceorder.RejectEstimateInputPort;
import com.fiap.workshop.management.application.saga.SagaOrchestrator;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RejectEstimateUseCase implements RejectEstimateInputPort {

    private final ServiceOrderRepository serviceOrderRepository;
    private final SagaOrchestrator sagaOrchestrator;

    public RejectEstimateUseCase(ServiceOrderRepository serviceOrderRepository,
                                 SagaOrchestrator sagaOrchestrator) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID id) {
        ServiceOrder order = serviceOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrder", id));
        order.rejectEstimate();
        sagaOrchestrator.reject(order);
        return ServiceOrderResponse.from(serviceOrderRepository.save(order));
    }
}
