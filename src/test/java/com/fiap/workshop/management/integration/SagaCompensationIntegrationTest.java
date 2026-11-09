package com.fiap.workshop.management.integration;

import com.fiap.workshop.management.application.saga.command.CancelBudget;
import com.fiap.workshop.management.application.saga.command.EnqueueExecution;
import com.fiap.workshop.management.application.saga.command.ProcessPayment;
import com.fiap.workshop.management.application.saga.command.RefundPayment;
import com.fiap.workshop.management.application.saga.command.ReleaseStock;
import com.fiap.workshop.management.application.saga.command.ReserveStock;
import com.fiap.workshop.management.application.saga.command.SagaCommand;
import com.fiap.workshop.management.application.saga.event.BudgetCancelled;
import com.fiap.workshop.management.application.saga.event.BudgetGenerated;
import com.fiap.workshop.management.application.saga.event.ExecutionEnqueueFailed;
import com.fiap.workshop.management.application.saga.event.PaymentApproved;
import com.fiap.workshop.management.application.saga.event.PaymentDeclined;
import com.fiap.workshop.management.application.saga.event.PaymentRefunded;
import com.fiap.workshop.management.application.saga.event.StockReleased;
import com.fiap.workshop.management.application.saga.event.StockReservationFailed;
import com.fiap.workshop.management.application.saga.event.StockReserved;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Saga compensation Integration")
class SagaCompensationIntegrationTest extends ServiceOrderFlowSupport {

    /** An approved order whose reservation request is out, waiting for Inventory. */
    private UUID approvedOrder() throws Exception {
        UUID orderId = orderInDiagnosisWithItems();
        UUID sagaId = completeDiagnosis(orderId);
        answer(new BudgetGenerated(UUID.randomUUID(), sagaId, orderId, new BigDecimal("200.00")));
        performPatch("/api/v1/service-orders/" + orderId + "/estimate/approve", adminToken)
                .andExpect(status().isOk());
        publisher.reset();
        return orderId;
    }

    private List<Class<?>> sentTypes() {
        return publisher.commands().stream().<Class<?>>map(SagaCommand::getClass).toList();
    }

    @Test
    @DisplayName("should cancel the order and the budget when the stock is insufficient")
    void shouldCancelTheOrderAndTheBudgetWhenTheStockIsInsufficient() throws Exception {
        UUID orderId = approvedOrder();
        UUID sagaId = sagaId(orderId);

        answer(new StockReservationFailed(UUID.randomUUID(), sagaId, orderId, "Insufficient stock"));

        assertEquals("CANCELLED", orderOf(orderId).get("status").asText());
        JsonNode saga = sagaOf(orderId);
        assertEquals("COMPENSATING", saga.get("status").asText());
        assertEquals("Insufficient stock", saga.get("failureReason").asText());
        assertEquals(List.<Class<?>>of(CancelBudget.class), sentTypes());

        answer(new BudgetCancelled(UUID.randomUUID(), sagaId, orderId));
        assertEquals("COMPENSATED", sagaOf(orderId).get("status").asText());
    }

    @Test
    @DisplayName("should release the stock and cancel the budget when the payment is declined")
    void shouldReleaseTheStockAndCancelTheBudgetWhenThePaymentIsDeclined() throws Exception {
        UUID orderId = approvedOrder();
        UUID sagaId = sagaId(orderId);
        answer(new StockReserved(UUID.randomUUID(), sagaId, orderId));
        publisher.reset();

        answer(new PaymentDeclined(UUID.randomUUID(), sagaId, orderId, "Declined by payment method mock-decline"));

        assertEquals("CANCELLED", orderOf(orderId).get("status").asText());
        assertEquals(2, publisher.commands().size());
        assertTrue(sentTypes().containsAll(List.of(ReleaseStock.class, CancelBudget.class)));
        assertEquals("COMPENSATING", sagaOf(orderId).get("status").asText());

        answer(new StockReleased(UUID.randomUUID(), sagaId, orderId));
        assertEquals("COMPENSATING", sagaOf(orderId).get("status").asText());
        assertEquals(List.of("CANCEL_BUDGET"), pendingOf(orderId));

        answer(new BudgetCancelled(UUID.randomUUID(), sagaId, orderId));
        assertEquals("COMPENSATED", sagaOf(orderId).get("status").asText());
        assertEquals(List.of(), pendingOf(orderId));
    }

    @Test
    @DisplayName("should refund the payment and release the stock when the order cannot be queued")
    void shouldRefundThePaymentAndReleaseTheStockWhenTheOrderCannotBeQueued() throws Exception {
        UUID orderId = approvedOrder();
        UUID sagaId = sagaId(orderId);
        answer(new StockReserved(UUID.randomUUID(), sagaId, orderId));
        answer(new PaymentApproved(UUID.randomUUID(), sagaId, orderId));
        publisher.last(EnqueueExecution.class);
        publisher.reset();

        answer(new ExecutionEnqueueFailed(UUID.randomUUID(), sagaId, orderId, "Queue unavailable"));

        assertEquals("CANCELLED", orderOf(orderId).get("status").asText());
        assertTrue(sentTypes().containsAll(List.of(RefundPayment.class, ReleaseStock.class)));
        assertEquals(2, publisher.commands().size());

        answer(new PaymentRefunded(UUID.randomUUID(), sagaId, orderId));
        answer(new StockReleased(UUID.randomUUID(), sagaId, orderId));
        assertEquals("COMPENSATED", sagaOf(orderId).get("status").asText());
    }

    @Test
    @DisplayName("should ignore a message delivered twice")
    void shouldIgnoreAMessageDeliveredTwice() throws Exception {
        UUID orderId = approvedOrder();
        UUID sagaId = sagaId(orderId);
        StockReserved reserved = new StockReserved(UUID.randomUUID(), sagaId, orderId);

        answer(reserved);
        answer(reserved);

        assertEquals(1, publisher.commands().stream().filter(ProcessPayment.class::isInstance).count());
        assertEquals("PROCESS_PAYMENT", sagaOf(orderId).get("step").asText());
    }

    @Test
    @DisplayName("should ignore an answer that does not fit the step the saga is waiting for")
    void shouldIgnoreAnAnswerThatDoesNotFitTheCurrentStep() throws Exception {
        UUID orderId = approvedOrder();
        UUID sagaId = sagaId(orderId);

        answer(new PaymentApproved(UUID.randomUUID(), sagaId, orderId));

        assertTrue(publisher.commands().isEmpty());
        assertEquals("RESERVE_STOCK", sagaOf(orderId).get("step").asText());
    }

    @Test
    @DisplayName("should ignore an answer for a saga that does not exist")
    void shouldIgnoreAnAnswerForASagaThatDoesNotExist() throws Exception {
        UUID orderId = approvedOrder();

        answer(new StockReserved(UUID.randomUUID(), UUID.randomUUID(), orderId));

        assertTrue(publisher.commands().isEmpty());
        assertEquals("RESERVE_STOCK", sagaOf(orderId).get("step").asText());
    }

    @Test
    @DisplayName("should refuse a second approval while the first is being fulfilled")
    void shouldRefuseASecondApprovalWhileTheFirstIsBeingFulfilled() throws Exception {
        UUID orderId = approvedOrder();

        performPatch("/api/v1/service-orders/" + orderId + "/estimate/approve", adminToken)
                .andExpect(status().isUnprocessableEntity());
        assertTrue(publisher.commands().stream().noneMatch(ReserveStock.class::isInstance));
    }

    @Test
    @DisplayName("should refuse to complete the diagnosis again while a budget is being calculated")
    void shouldRefuseToCompleteTheDiagnosisAgainWhileABudgetIsBeingCalculated() throws Exception {
        UUID orderId = orderInDiagnosisWithItems();
        completeDiagnosis(orderId);
        publisher.reset();

        performPost("/api/v1/service-orders/" + orderId + "/diagnosis/complete",
                "{\"diagnosisNotes\":\"Again\"}", mechanicToken)
                .andExpect(status().isUnprocessableEntity());
        assertTrue(publisher.commands().isEmpty());
    }

    @Test
    @DisplayName("should refuse to approve an order that has no estimate waiting")
    void shouldRefuseToApproveAnOrderThatHasNoEstimateWaiting() throws Exception {
        UUID orderId = orderInDiagnosisWithItems();

        performPatch("/api/v1/service-orders/" + orderId + "/estimate/approve", adminToken)
                .andExpect(status().isUnprocessableEntity());
    }

    private List<String> pendingOf(UUID orderId) throws Exception {
        List<String> pending = new java.util.ArrayList<>();
        sagaOf(orderId).get("pendingCompensations").forEach(n -> pending.add(n.asText()));
        return pending;
    }
}
