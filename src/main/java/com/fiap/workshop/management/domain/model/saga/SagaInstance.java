package com.fiap.workshop.management.domain.model.saga;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * State of the orchestrated saga of one service order, from the budget request
 * to the consumption of the stock. The orchestrator moves it with the answers
 * of the other services; every method states the step it requires and moves to
 * the next, so the order of the saga lives here and not in the handlers.
 *
 * <pre>
 * GENERATE_BUDGET → AWAIT_APPROVAL → RESERVE_STOCK → PROCESS_PAYMENT
 *                 → ENQUEUE_EXECUTION → EXECUTING → CONSUME_STOCK → COMPLETED
 * </pre>
 *
 * A failure in RESERVE_STOCK, PROCESS_PAYMENT or ENQUEUE_EXECUTION starts the
 * compensations of what had already succeeded.
 */
public class SagaInstance {

    private final UUID id;
    private final UUID serviceOrderId;
    private final LocalDateTime createdAt;
    private SagaStep step;
    private SagaStatus status;
    private String paymentMethod;
    private final Set<Compensation> pendingCompensations;
    private String failureReason;
    private LocalDateTime updatedAt;

    public SagaInstance(UUID id, UUID serviceOrderId, SagaStep step, SagaStatus status, String paymentMethod,
                        Set<Compensation> pendingCompensations, String failureReason,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.serviceOrderId = serviceOrderId;
        this.step = step;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.pendingCompensations = pendingCompensations.isEmpty()
                ? EnumSet.noneOf(Compensation.class) : EnumSet.copyOf(pendingCompensations);
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static SagaInstance start(UUID serviceOrderId) {
        LocalDateTime now = LocalDateTime.now();
        return new SagaInstance(UUID.randomUUID(), serviceOrderId, SagaStep.GENERATE_BUDGET,
                SagaStatus.RUNNING, null, Set.of(), null, now, now);
    }

    /** True while the saga is running and is waiting for exactly this step. */
    public boolean isWaitingFor(SagaStep expected) {
        return status == SagaStatus.RUNNING && step == expected;
    }

    /** A saga that still owns the order: nothing else may start for it. */
    public boolean isActive() {
        return status == SagaStatus.RUNNING || status == SagaStatus.COMPENSATING;
    }

    public void budgetGenerated() {
        advance(SagaStep.GENERATE_BUDGET, SagaStep.AWAIT_APPROVAL);
    }

    public void approved(String chosenPaymentMethod) {
        advance(SagaStep.AWAIT_APPROVAL, SagaStep.RESERVE_STOCK);
        this.paymentMethod = chosenPaymentMethod;
    }

    public void rejected() {
        require(SagaStep.AWAIT_APPROVAL);
        this.status = SagaStatus.REJECTED;
        touch();
    }

    public void stockReserved() {
        advance(SagaStep.RESERVE_STOCK, SagaStep.PROCESS_PAYMENT);
    }

    public void paymentApproved() {
        advance(SagaStep.PROCESS_PAYMENT, SagaStep.ENQUEUE_EXECUTION);
    }

    public void executionEnqueued() {
        advance(SagaStep.ENQUEUE_EXECUTION, SagaStep.EXECUTING);
    }

    public void executionCompleted() {
        advance(SagaStep.EXECUTING, SagaStep.CONSUME_STOCK);
    }

    public void stockConsumed() {
        require(SagaStep.CONSUME_STOCK);
        this.status = SagaStatus.COMPLETED;
        touch();
    }

    /** Nothing was reserved yet; only the budget has to be cancelled. */
    public Set<Compensation> stockReservationFailed(String reason) {
        require(SagaStep.RESERVE_STOCK);
        return compensate(reason, Compensation.CANCEL_BUDGET);
    }

    public Set<Compensation> paymentDeclined(String reason) {
        require(SagaStep.PROCESS_PAYMENT);
        return compensate(reason, Compensation.RELEASE_STOCK, Compensation.CANCEL_BUDGET);
    }

    /** Billing's refund also cancels the budget, so only the refund and the stock are listed. */
    public Set<Compensation> executionEnqueueFailed(String reason) {
        require(SagaStep.ENQUEUE_EXECUTION);
        return compensate(reason, Compensation.REFUND_PAYMENT, Compensation.RELEASE_STOCK);
    }

    /** @return true when this acknowledgement was the last pending one and the saga is now COMPENSATED */
    public boolean compensationDone(Compensation compensation) {
        if (status != SagaStatus.COMPENSATING || !pendingCompensations.remove(compensation)) {
            return false;
        }
        if (pendingCompensations.isEmpty()) {
            this.status = SagaStatus.COMPENSATED;
        }
        touch();
        return status == SagaStatus.COMPENSATED;
    }

    private Set<Compensation> compensate(String reason, Compensation first, Compensation... others) {
        this.status = SagaStatus.COMPENSATING;
        this.failureReason = reason;
        this.pendingCompensations.add(first);
        this.pendingCompensations.addAll(Set.of(others));
        touch();
        return EnumSet.copyOf(pendingCompensations);
    }

    private void advance(SagaStep from, SagaStep to) {
        require(from);
        this.step = to;
        touch();
    }

    private void require(SagaStep expected) {
        if (!isWaitingFor(expected)) {
            throw new IllegalStateException(
                    "Saga " + id + " is " + status + " at " + step + ", expected RUNNING at " + expected);
        }
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getServiceOrderId() { return serviceOrderId; }
    public SagaStep getStep() { return step; }
    public SagaStatus getStatus() { return status; }
    public String getPaymentMethod() { return paymentMethod; }
    public Set<Compensation> getPendingCompensations() { return EnumSet.copyOf(pendingCompensations); }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
