package com.fiap.workshop.management.application.usecase.serviceorder;

import com.fiap.workshop.management.application.dto.serviceorder.EstimateApprovalWebhookCommand;
import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.port.in.serviceorder.ApproveEstimateInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.ProcessEstimateApprovalWebhookInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.RejectEstimateInputPort;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessEstimateApprovalWebhookUseCase implements ProcessEstimateApprovalWebhookInputPort {

    private final ServiceOrderRepository serviceOrderRepository;
    private final ApproveEstimateInputPort approveEstimateUseCase;
    private final RejectEstimateInputPort rejectEstimateUseCase;

    public ProcessEstimateApprovalWebhookUseCase(ServiceOrderRepository serviceOrderRepository,
                                                  ApproveEstimateInputPort approveEstimateUseCase,
                                                  RejectEstimateInputPort rejectEstimateUseCase) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.approveEstimateUseCase = approveEstimateUseCase;
        this.rejectEstimateUseCase = rejectEstimateUseCase;
    }

    @Transactional
    public ServiceOrderResponse execute(EstimateApprovalWebhookCommand command) {
        ServiceOrder order = serviceOrderRepository.findByOsCode(command.osCode())
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrder", command.osCode()));

        return switch (command.decision()) {
            case APPROVED -> approveEstimateUseCase.execute(order.getId(), command.paymentMethod());
            case REJECTED -> rejectEstimateUseCase.execute(order.getId());
        };
    }
}
