package com.fiap.workshop.management.domain.model.saga;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SagaInstance")
class SagaInstanceTest {

    private SagaInstance started() {
        return SagaInstance.start(UUID.randomUUID());
    }

    private SagaInstance atReserveStock() {
        SagaInstance saga = started();
        saga.budgetGenerated();
        saga.approved("pix");
        return saga;
    }

    private SagaInstance atProcessPayment() {
        SagaInstance saga = atReserveStock();
        saga.stockReserved();
        return saga;
    }

    private SagaInstance atEnqueueExecution() {
        SagaInstance saga = atProcessPayment();
        saga.paymentApproved();
        return saga;
    }

    @Test
    @DisplayName("should start running and waiting for the budget")
    void shouldStartRunningAndWaitingForTheBudget() {
        SagaInstance saga = started();

        assertEquals(SagaStatus.RUNNING, saga.getStatus());
        assertEquals(SagaStep.GENERATE_BUDGET, saga.getStep());
        assertTrue(saga.isWaitingFor(SagaStep.GENERATE_BUDGET));
        assertTrue(saga.isActive());
        assertTrue(saga.getPendingCompensations().isEmpty());
        assertNull(saga.getPaymentMethod());
    }

    @Test
    @DisplayName("should walk the whole forward path to COMPLETED")
    void shouldWalkTheWholeForwardPath() {
        SagaInstance saga = started();

        saga.budgetGenerated();
        assertEquals(SagaStep.AWAIT_APPROVAL, saga.getStep());
        saga.approved("credit-card");
        assertEquals(SagaStep.RESERVE_STOCK, saga.getStep());
        assertEquals("credit-card", saga.getPaymentMethod());
        saga.stockReserved();
        assertEquals(SagaStep.PROCESS_PAYMENT, saga.getStep());
        saga.paymentApproved();
        assertEquals(SagaStep.ENQUEUE_EXECUTION, saga.getStep());
        saga.executionEnqueued();
        assertEquals(SagaStep.EXECUTING, saga.getStep());
        saga.executionCompleted();
        assertEquals(SagaStep.CONSUME_STOCK, saga.getStep());
        saga.stockConsumed();

        assertEquals(SagaStatus.COMPLETED, saga.getStatus());
        assertFalse(saga.isActive());
    }

    @Test
    @DisplayName("should refuse a step that is out of order")
    void shouldRefuseAStepThatIsOutOfOrder() {
        SagaInstance saga = started();

        assertThrows(IllegalStateException.class, saga::paymentApproved);
        assertThrows(IllegalStateException.class, () -> saga.approved("pix"));
        assertThrows(IllegalStateException.class, saga::stockConsumed);
        assertEquals(SagaStep.GENERATE_BUDGET, saga.getStep());
    }

    @Test
    @DisplayName("should end as REJECTED when the customer rejects the budget")
    void shouldEndAsRejectedWhenTheCustomerRejectsTheBudget() {
        SagaInstance saga = started();
        saga.budgetGenerated();

        saga.rejected();

        assertEquals(SagaStatus.REJECTED, saga.getStatus());
        assertFalse(saga.isActive());
        assertFalse(saga.isWaitingFor(SagaStep.AWAIT_APPROVAL));
    }

    @Test
    @DisplayName("should only cancel the budget when the stock reservation fails")
    void shouldOnlyCancelTheBudgetWhenTheStockReservationFails() {
        SagaInstance saga = atReserveStock();

        Set<Compensation> compensations = saga.stockReservationFailed("Insufficient stock");

        assertEquals(Set.of(Compensation.CANCEL_BUDGET), compensations);
        assertEquals(SagaStatus.COMPENSATING, saga.getStatus());
        assertEquals("Insufficient stock", saga.getFailureReason());
        assertTrue(saga.isActive());
    }

    @Test
    @DisplayName("should release the stock and cancel the budget when the payment is declined")
    void shouldReleaseTheStockAndCancelTheBudgetWhenThePaymentIsDeclined() {
        Set<Compensation> compensations = atProcessPayment().paymentDeclined("declined");

        assertEquals(Set.of(Compensation.RELEASE_STOCK, Compensation.CANCEL_BUDGET), compensations);
    }

    @Test
    @DisplayName("should refund and release the stock when the order cannot be queued")
    void shouldRefundAndReleaseTheStockWhenTheOrderCannotBeQueued() {
        Set<Compensation> compensations = atEnqueueExecution().executionEnqueueFailed("no queue");

        assertEquals(Set.of(Compensation.REFUND_PAYMENT, Compensation.RELEASE_STOCK), compensations);
    }

    @Test
    @DisplayName("should become COMPENSATED only when the last compensation is acknowledged")
    void shouldBecomeCompensatedOnlyWhenTheLastCompensationIsAcknowledged() {
        SagaInstance saga = atProcessPayment();
        saga.paymentDeclined("declined");

        assertFalse(saga.compensationDone(Compensation.RELEASE_STOCK));
        assertEquals(SagaStatus.COMPENSATING, saga.getStatus());
        assertEquals(Set.of(Compensation.CANCEL_BUDGET), saga.getPendingCompensations());

        assertTrue(saga.compensationDone(Compensation.CANCEL_BUDGET));
        assertEquals(SagaStatus.COMPENSATED, saga.getStatus());
        assertFalse(saga.isActive());
    }

    @Test
    @DisplayName("should ignore an acknowledgement that is not pending or arrives outside a compensation")
    void shouldIgnoreAnAcknowledgementThatIsNotPending() {
        SagaInstance running = atReserveStock();
        assertFalse(running.compensationDone(Compensation.CANCEL_BUDGET));

        SagaInstance compensating = atProcessPayment();
        compensating.paymentDeclined("declined");
        assertFalse(compensating.compensationDone(Compensation.REFUND_PAYMENT));
        assertEquals(2, compensating.getPendingCompensations().size());
    }

    @Test
    @DisplayName("should not allow a failure at a step the saga is not in")
    void shouldNotAllowAFailureAtAStepTheSagaIsNotIn() {
        SagaInstance saga = atReserveStock();

        assertThrows(IllegalStateException.class, () -> saga.paymentDeclined("x"));
        assertThrows(IllegalStateException.class, () -> saga.executionEnqueueFailed("x"));
    }
}
