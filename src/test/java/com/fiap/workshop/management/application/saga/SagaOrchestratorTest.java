package com.fiap.workshop.management.application.saga;

import com.fiap.workshop.management.application.port.out.SagaCommandPublisher;
import com.fiap.workshop.management.application.saga.event.BudgetCancelled;
import com.fiap.workshop.management.application.saga.event.PaymentApproved;
import com.fiap.workshop.management.application.saga.event.SagaEvent;
import com.fiap.workshop.management.application.saga.event.StockReserved;
import com.fiap.workshop.management.domain.exception.InvalidStatusTransitionException;
import com.fiap.workshop.management.domain.model.catalog.ServiceCatalogItem;
import com.fiap.workshop.management.domain.model.catalog.ServiceType;
import com.fiap.workshop.management.domain.model.saga.SagaInstance;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrderItem;
import com.fiap.workshop.management.domain.model.shared.Money;
import com.fiap.workshop.management.domain.repository.CustomerRepository;
import com.fiap.workshop.management.domain.repository.ProcessedMessageRepository;
import com.fiap.workshop.management.domain.repository.SagaInstanceRepository;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import com.fiap.workshop.management.domain.service.EstimateNotificationService;
import com.fiap.workshop.management.domain.service.MetricsPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The saga paths are covered end to end by the integration tests; these are the edges. */
@ExtendWith(MockitoExtension.class)
@DisplayName("SagaOrchestrator")
class SagaOrchestratorTest {

    @Mock
    private SagaInstanceRepository sagaRepository;
    @Mock
    private ProcessedMessageRepository processedMessages;
    @Mock
    private ServiceOrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private SagaCommandPublisher publisher;
    @Mock
    private EstimateNotificationService notificationService;
    @Mock
    private MetricsPublisher metricsPublisher;

    private SagaOrchestrator orchestrator;

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orchestrator = new SagaOrchestrator(sagaRepository, processedMessages, orderRepository,
                customerRepository, publisher, notificationService, metricsPublisher);
        lenient().when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ServiceOrder diagnosedOrder() {
        ServiceOrder order = ServiceOrder.create("OS-2026-00001", UUID.randomUUID(), UUID.randomUUID(), null);
        order.startDiagnosis();
        order.addItem(ServiceOrderItem.createServiceItem(order.getId(),
                ServiceCatalogItem.create("Oil Change", "d", ServiceType.MECHANICAL, Money.of(150)), 1));
        order.completeDiagnosis("notes");
        return order;
    }

    @Test
    @DisplayName("should ignore a message that was already processed")
    void shouldIgnoreAMessageThatWasAlreadyProcessed() {
        SagaEvent event = new StockReserved(UUID.randomUUID(), UUID.randomUUID(), orderId);
        when(processedMessages.markProcessed(event.messageId())).thenReturn(false);

        orchestrator.handle(event);

        verifyNoInteractions(sagaRepository, orderRepository, publisher);
    }

    @Test
    @DisplayName("should ignore a message of a saga that does not exist")
    void shouldIgnoreAMessageOfASagaThatDoesNotExist() {
        SagaEvent event = new StockReserved(UUID.randomUUID(), UUID.randomUUID(), orderId);
        when(processedMessages.markProcessed(event.messageId())).thenReturn(true);
        when(sagaRepository.findByIdForUpdate(event.sagaId())).thenReturn(Optional.empty());

        orchestrator.handle(event);

        verifyNoInteractions(orderRepository, publisher);
    }

    @Test
    @DisplayName("should ignore a message of a type it does not know")
    void shouldIgnoreAMessageOfATypeItDoesNotKnow() {
        SagaInstance saga = SagaInstance.start(orderId);
        UUID messageId = UUID.randomUUID();
        SagaEvent unknown = new SagaEvent() {
            public UUID messageId() { return messageId; }
            public UUID sagaId() { return saga.getId(); }
            public UUID serviceOrderId() { return orderId; }
        };
        when(processedMessages.markProcessed(unknown.messageId())).thenReturn(true);
        when(sagaRepository.findByIdForUpdate(saga.getId())).thenReturn(Optional.of(saga));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(diagnosedOrder()));

        orchestrator.handle(unknown);

        verifyNoInteractions(publisher);
        verify(sagaRepository, never()).save(any());
    }

    @Test
    @DisplayName("should ignore a compensation acknowledgement the saga is not waiting for")
    void shouldIgnoreACompensationAcknowledgementTheSagaIsNotWaitingFor() {
        SagaInstance saga = SagaInstance.start(orderId);
        SagaEvent event = new BudgetCancelled(UUID.randomUUID(), saga.getId(), orderId);
        when(processedMessages.markProcessed(event.messageId())).thenReturn(true);
        when(sagaRepository.findByIdForUpdate(saga.getId())).thenReturn(Optional.of(saga));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(diagnosedOrder()));

        orchestrator.handle(event);

        verify(sagaRepository, never()).save(any());
    }

    @Test
    @DisplayName("should ignore an answer for a step the saga has not reached")
    void shouldIgnoreAnAnswerForAStepTheSagaHasNotReached() {
        SagaInstance saga = SagaInstance.start(orderId);
        SagaEvent event = new PaymentApproved(UUID.randomUUID(), saga.getId(), orderId);
        when(processedMessages.markProcessed(event.messageId())).thenReturn(true);
        when(sagaRepository.findByIdForUpdate(saga.getId())).thenReturn(Optional.of(saga));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(diagnosedOrder()));

        orchestrator.handle(event);

        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("should refuse to start a second saga while one is active")
    void shouldRefuseToStartASecondSagaWhileOneIsActive() {
        ServiceOrder order = diagnosedOrder();
        when(sagaRepository.findLatestByServiceOrderId(order.getId()))
                .thenReturn(Optional.of(SagaInstance.start(order.getId())));

        assertThrows(InvalidStatusTransitionException.class, () -> orchestrator.requestBudget(order));
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("should refuse to approve or reject an order with no estimate waiting")
    void shouldRefuseToApproveOrRejectAnOrderWithNoEstimateWaiting() {
        ServiceOrder order = diagnosedOrder();
        when(sagaRepository.findLatestByServiceOrderId(order.getId())).thenReturn(Optional.empty());

        assertThrows(InvalidStatusTransitionException.class, () -> orchestrator.approve(order, "pix"));
        assertThrows(InvalidStatusTransitionException.class, () -> orchestrator.reject(order));
        verifyNoInteractions(publisher);
    }
}
