package com.fiap.workshop.management.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.workshop.management.application.port.in.saga.HandleSagaEventInputPort;
import com.fiap.workshop.management.application.saga.event.BudgetCancelled;
import com.fiap.workshop.management.application.saga.event.BudgetGenerated;
import com.fiap.workshop.management.application.saga.event.ExecutionCompleted;
import com.fiap.workshop.management.application.saga.event.ExecutionEnqueueFailed;
import com.fiap.workshop.management.application.saga.event.ExecutionEnqueued;
import com.fiap.workshop.management.application.saga.event.PaymentApproved;
import com.fiap.workshop.management.application.saga.event.PaymentDeclined;
import com.fiap.workshop.management.application.saga.event.PaymentRefunded;
import com.fiap.workshop.management.application.saga.event.SagaEvent;
import com.fiap.workshop.management.application.saga.event.StockConsumed;
import com.fiap.workshop.management.application.saga.event.StockReleased;
import com.fiap.workshop.management.application.saga.event.StockReservationFailed;
import com.fiap.workshop.management.application.saga.event.StockReserved;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SagaEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final HandleSagaEventInputPort handler = mock(HandleSagaEventInputPort.class);
    private final SagaEventListener listener = new SagaEventListener(objectMapper, handler);

    private final UUID messageId = UUID.randomUUID();
    private final UUID sagaId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private String envelope(String type, String payload) {
        return """
                {"messageId":"%s","sagaId":"%s","serviceOrderId":"%s","type":"%s","payload":%s}
                """.formatted(messageId, sagaId, orderId, type, payload);
    }

    private SagaEvent handled() {
        ArgumentCaptor<SagaEvent> captor = ArgumentCaptor.forClass(SagaEvent.class);
        verify(handler).handle(captor.capture());
        SagaEvent event = captor.getValue();
        assertEquals(messageId, event.messageId());
        assertEquals(sagaId, event.sagaId());
        assertEquals(orderId, event.serviceOrderId());
        clearInvocations(handler);
        return event;
    }

    @Test
    void shouldReadTheEventsThatCarryData() throws Exception {
        listener.onMessage(envelope("BudgetGenerated", "{\"totalAmount\":235.50}"));
        assertEquals(0, new BigDecimal("235.50").compareTo(((BudgetGenerated) handled()).totalAmount()));

        listener.onMessage(envelope("StockReservationFailed", "{\"reason\":\"no stock\"}"));
        assertEquals("no stock", ((StockReservationFailed) handled()).reason());

        listener.onMessage(envelope("PaymentDeclined", "{\"reason\":\"declined\"}"));
        assertEquals("declined", ((PaymentDeclined) handled()).reason());

        listener.onMessage(envelope("ExecutionEnqueueFailed", "{\"reason\":\"no tasks\"}"));
        assertEquals("no tasks", ((ExecutionEnqueueFailed) handled()).reason());
    }

    @Test
    void shouldReadTheEventsWithoutDataWhateverThePayloadLooksLike() throws Exception {
        listener.onMessage(envelope("StockReserved", "{}"));
        assertEquals(StockReserved.class, handled().getClass());
        listener.onMessage(envelope("PaymentApproved", "null"));
        assertEquals(PaymentApproved.class, handled().getClass());
        listener.onMessage(envelope("ExecutionEnqueued", "{}"));
        assertEquals(ExecutionEnqueued.class, handled().getClass());
        listener.onMessage(envelope("ExecutionCompleted", "{}"));
        assertEquals(ExecutionCompleted.class, handled().getClass());
        listener.onMessage(envelope("StockConsumed", "{}"));
        assertEquals(StockConsumed.class, handled().getClass());
        listener.onMessage(envelope("StockReleased", "{}"));
        assertEquals(StockReleased.class, handled().getClass());
        listener.onMessage(envelope("BudgetCancelled", "{}"));
        assertEquals(BudgetCancelled.class, handled().getClass());
        listener.onMessage(envelope("PaymentRefunded", "{}"));
        assertEquals(PaymentRefunded.class, handled().getClass());
    }

    @Test
    void shouldFailOnAnUnknownTypeSoTheMessageGoesToTheDeadLetterQueue() {
        assertThrows(IllegalArgumentException.class, () -> listener.onMessage(envelope("GenerateBudget", "{}")));
        verifyNoInteractions(handler);
    }

    @Test
    void shouldFailOnAMessageThatIsNotJson() {
        assertThrows(JsonProcessingException.class, () -> listener.onMessage("not json"));
        verifyNoInteractions(handler);
    }

    @Test
    void shouldFailWhenTheHandlerFailsSoTheMessageIsRetried() {
        doThrow(new IllegalStateException("db down")).when(handler).handle(any());

        assertThrows(IllegalStateException.class, () -> listener.onMessage(envelope("StockReserved", "{}")));
    }
}
