package com.fiap.workshop.management.application.usecase.serviceorder;

import com.fiap.workshop.management.application.dto.serviceorder.CompleteDiagnosisCommand;
import com.fiap.workshop.management.application.dto.serviceorder.EmailStatusUpdateCommand;
import com.fiap.workshop.management.application.dto.serviceorder.EmailStatusUpdateSubject;
import com.fiap.workshop.management.application.port.in.serviceorder.ApproveEstimateInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.CompleteDiagnosisInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.DeliverServiceOrderInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.RejectEstimateInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.StartDiagnosisInputPort;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessEmailStatusUpdateUseCase")
class ProcessEmailStatusUpdateUseCaseTest {

    @Mock
    private ServiceOrderRepository serviceOrderRepository;
    @Mock
    private StartDiagnosisInputPort startDiagnosisUseCase;
    @Mock
    private CompleteDiagnosisInputPort completeDiagnosisUseCase;
    @Mock
    private ApproveEstimateInputPort approveEstimateUseCase;
    @Mock
    private RejectEstimateInputPort rejectEstimateUseCase;
    @Mock
    private DeliverServiceOrderInputPort deliverServiceOrderUseCase;

    private ProcessEmailStatusUpdateUseCase useCase;
    private ServiceOrder order;

    @BeforeEach
    void setUp() {
        useCase = new ProcessEmailStatusUpdateUseCase(serviceOrderRepository, startDiagnosisUseCase,
                completeDiagnosisUseCase, approveEstimateUseCase, rejectEstimateUseCase, deliverServiceOrderUseCase);
        order = ServiceOrder.create("OS-2026-00001", UUID.randomUUID(), UUID.randomUUID(), null);
    }

    @Test
    @DisplayName("should dispatch to startDiagnosis for START_DIAGNOSIS subject")
    void shouldDispatchToStartDiagnosis() {
        when(serviceOrderRepository.findByOsCode("OS-2026-00001")).thenReturn(Optional.of(order));

        useCase.execute(new EmailStatusUpdateCommand("OS-2026-00001", EmailStatusUpdateSubject.START_DIAGNOSIS, null));

        verify(startDiagnosisUseCase).execute(order.getId());
    }

    @Test
    @DisplayName("should dispatch to completeDiagnosis with body for COMPLETE_DIAGNOSIS subject")
    void shouldDispatchToCompleteDiagnosisWithBody() {
        when(serviceOrderRepository.findByOsCode("OS-2026-00001")).thenReturn(Optional.of(order));

        useCase.execute(new EmailStatusUpdateCommand(
                "OS-2026-00001", EmailStatusUpdateSubject.COMPLETE_DIAGNOSIS, "Brake pads worn out"));

        verify(completeDiagnosisUseCase).execute(order.getId(), new CompleteDiagnosisCommand("Brake pads worn out"));
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when COMPLETE_DIAGNOSIS has blank body")
    void shouldThrowIllegalArgumentExceptionWhenCompleteDiagnosisHasBlankBody() {
        when(serviceOrderRepository.findByOsCode("OS-2026-00001")).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(
                new EmailStatusUpdateCommand("OS-2026-00001", EmailStatusUpdateSubject.COMPLETE_DIAGNOSIS, " ")));
        verifyNoInteractions(completeDiagnosisUseCase);
    }

    @Test
    @DisplayName("should dispatch to approveEstimate for APPROVE_ESTIMATE subject")
    void shouldDispatchToApproveEstimate() {
        when(serviceOrderRepository.findByOsCode("OS-2026-00001")).thenReturn(Optional.of(order));

        useCase.execute(new EmailStatusUpdateCommand("OS-2026-00001", EmailStatusUpdateSubject.APPROVE_ESTIMATE, null));

        verify(approveEstimateUseCase).execute(order.getId(), null);
    }

    @Test
    @DisplayName("should dispatch to rejectEstimate for REJECT_ESTIMATE subject")
    void shouldDispatchToRejectEstimate() {
        when(serviceOrderRepository.findByOsCode("OS-2026-00001")).thenReturn(Optional.of(order));

        useCase.execute(new EmailStatusUpdateCommand("OS-2026-00001", EmailStatusUpdateSubject.REJECT_ESTIMATE, null));

        verify(rejectEstimateUseCase).execute(order.getId());
    }

    @Test
    @DisplayName("should dispatch to deliver for DELIVER subject")
    void shouldDispatchToDeliver() {
        when(serviceOrderRepository.findByOsCode("OS-2026-00001")).thenReturn(Optional.of(order));

        useCase.execute(new EmailStatusUpdateCommand("OS-2026-00001", EmailStatusUpdateSubject.DELIVER, null));

        verify(deliverServiceOrderUseCase).execute(order.getId());
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when osCode is unknown")
    void shouldThrowResourceNotFoundExceptionWhenOsCodeIsUnknown() {
        when(serviceOrderRepository.findByOsCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(
                new EmailStatusUpdateCommand("UNKNOWN", EmailStatusUpdateSubject.DELIVER, null)));
    }
}
