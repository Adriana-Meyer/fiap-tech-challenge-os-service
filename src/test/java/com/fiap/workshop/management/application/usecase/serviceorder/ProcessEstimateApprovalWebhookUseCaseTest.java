package com.fiap.workshop.management.application.usecase.serviceorder;

import com.fiap.workshop.management.application.dto.serviceorder.EstimateApprovalWebhookCommand;
import com.fiap.workshop.management.application.dto.serviceorder.EstimateDecision;
import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.port.in.serviceorder.ApproveEstimateInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.RejectEstimateInputPort;
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
@DisplayName("ProcessEstimateApprovalWebhookUseCase")
class ProcessEstimateApprovalWebhookUseCaseTest {

    @Mock
    private ServiceOrderRepository serviceOrderRepository;

    @Mock
    private ApproveEstimateInputPort approveEstimateUseCase;

    @Mock
    private RejectEstimateInputPort rejectEstimateUseCase;

    private ProcessEstimateApprovalWebhookUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ProcessEstimateApprovalWebhookUseCase(
                serviceOrderRepository, approveEstimateUseCase, rejectEstimateUseCase);
    }

    private ServiceOrder orderWithId() {
        return ServiceOrder.create("OS-2026-00001", UUID.randomUUID(), UUID.randomUUID(), null);
    }

    @Test
    @DisplayName("should delegate to approve when decision is APPROVED")
    void shouldDelegateToApproveWhenDecisionIsApproved() {
        ServiceOrder order = orderWithId();
        ServiceOrderResponse expected = mock(ServiceOrderResponse.class);
        when(serviceOrderRepository.findByOsCode("OS-2026-00001")).thenReturn(Optional.of(order));
        when(approveEstimateUseCase.execute(order.getId(), "pix")).thenReturn(expected);

        ServiceOrderResponse response = useCase.execute(
                new EstimateApprovalWebhookCommand("OS-2026-00001", EstimateDecision.APPROVED, "pix"));

        assertEquals(expected, response);
        verify(approveEstimateUseCase).execute(order.getId(), "pix");
        verify(rejectEstimateUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("should delegate to reject when decision is REJECTED")
    void shouldDelegateToRejectWhenDecisionIsRejected() {
        ServiceOrder order = orderWithId();
        ServiceOrderResponse expected = mock(ServiceOrderResponse.class);
        when(serviceOrderRepository.findByOsCode("OS-2026-00001")).thenReturn(Optional.of(order));
        when(rejectEstimateUseCase.execute(order.getId())).thenReturn(expected);

        ServiceOrderResponse response = useCase.execute(
                new EstimateApprovalWebhookCommand("OS-2026-00001", EstimateDecision.REJECTED, null));

        assertEquals(expected, response);
        verify(rejectEstimateUseCase).execute(order.getId());
        verify(approveEstimateUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when osCode is unknown")
    void shouldThrowResourceNotFoundExceptionWhenOsCodeIsUnknown() {
        when(serviceOrderRepository.findByOsCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(
                new EstimateApprovalWebhookCommand("UNKNOWN", EstimateDecision.APPROVED, null)));
        verifyNoInteractions(approveEstimateUseCase, rejectEstimateUseCase);
    }
}
