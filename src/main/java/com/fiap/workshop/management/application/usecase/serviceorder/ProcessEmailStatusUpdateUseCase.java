package com.fiap.workshop.management.application.usecase.serviceorder;

import com.fiap.workshop.management.application.dto.serviceorder.CompleteDiagnosisCommand;
import com.fiap.workshop.management.application.dto.serviceorder.EmailStatusUpdateCommand;
import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.port.in.serviceorder.ApproveEstimateInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.CompleteDiagnosisInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.DeliverServiceOrderInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.ProcessEmailStatusUpdateInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.RejectEstimateInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.StartDiagnosisInputPort;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProcessEmailStatusUpdateUseCase implements ProcessEmailStatusUpdateInputPort {

    private final ServiceOrderRepository serviceOrderRepository;
    private final StartDiagnosisInputPort startDiagnosisUseCase;
    private final CompleteDiagnosisInputPort completeDiagnosisUseCase;
    private final ApproveEstimateInputPort approveEstimateUseCase;
    private final RejectEstimateInputPort rejectEstimateUseCase;
    private final DeliverServiceOrderInputPort deliverServiceOrderUseCase;

    public ProcessEmailStatusUpdateUseCase(ServiceOrderRepository serviceOrderRepository,
                                            StartDiagnosisInputPort startDiagnosisUseCase,
                                            CompleteDiagnosisInputPort completeDiagnosisUseCase,
                                            ApproveEstimateInputPort approveEstimateUseCase,
                                            RejectEstimateInputPort rejectEstimateUseCase,
                                            DeliverServiceOrderInputPort deliverServiceOrderUseCase) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.startDiagnosisUseCase = startDiagnosisUseCase;
        this.completeDiagnosisUseCase = completeDiagnosisUseCase;
        this.approveEstimateUseCase = approveEstimateUseCase;
        this.rejectEstimateUseCase = rejectEstimateUseCase;
        this.deliverServiceOrderUseCase = deliverServiceOrderUseCase;
    }

    @Transactional
    public ServiceOrderResponse execute(EmailStatusUpdateCommand command) {
        UUID id = serviceOrderRepository.findByOsCode(command.osCode())
                .map(ServiceOrder::getId)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrder", command.osCode()));

        return switch (command.subject()) {
            case START_DIAGNOSIS -> startDiagnosisUseCase.execute(id);
            case COMPLETE_DIAGNOSIS -> {
                if (command.body() == null || command.body().isBlank()) {
                    throw new IllegalArgumentException("body is required when subject=COMPLETE_DIAGNOSIS");
                }
                yield completeDiagnosisUseCase.execute(id, new CompleteDiagnosisCommand(command.body()));
            }
            case APPROVE_ESTIMATE -> approveEstimateUseCase.execute(id, null);
            case REJECT_ESTIMATE -> rejectEstimateUseCase.execute(id);
            case DELIVER -> deliverServiceOrderUseCase.execute(id);
        };
    }
}
