package com.fiap.workshop.management.application.usecase.serviceorder;

import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.port.in.serviceorder.ApproveEstimateInputPort;
import com.fiap.workshop.management.application.saga.SagaOrchestrator;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records the customer's approval and starts the fulfillment saga. The order
 * stays WAITING_APPROVAL until stock, payment and execution are all in place;
 * the saga state ({@code GET /service-orders/{id}/saga}) shows where it is.
 */
@Service
public class ApproveEstimateUseCase implements ApproveEstimateInputPort {

    static final String DEFAULT_PAYMENT_METHOD = "credit-card";

    private final ServiceOrderRepository serviceOrderRepository;
    private final SagaOrchestrator sagaOrchestrator;

    public ApproveEstimateUseCase(ServiceOrderRepository serviceOrderRepository,
                                  SagaOrchestrator sagaOrchestrator) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID id, String paymentMethod) {
        ServiceOrder order = serviceOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrder", id));
        order.ensureAwaitingApproval();
        sagaOrchestrator.approve(order,
                paymentMethod == null || paymentMethod.isBlank() ? DEFAULT_PAYMENT_METHOD : paymentMethod);
        return ServiceOrderResponse.from(order);
    }
}
