package com.fiap.workshop.management.domain.model.serviceorder;

import com.fiap.workshop.management.domain.exception.InvalidStatusTransitionException;
import com.fiap.workshop.management.domain.model.shared.Money;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ServiceOrder {

    private UUID id;
    private String osCode;
    private ServiceOrderStatus status;
    private UUID customerId;
    private UUID vehicleId;
    private List<ServiceOrderItem> items;
    private Money totalAmount;
    private String diagnosisNotes;
    private String customerComments;
    private LocalDateTime receivedAt;
    private LocalDateTime diagnosisStartedAt;
    private LocalDateTime waitingApprovalAt;
    private LocalDateTime executionStartedAt;
    private LocalDateTime executionFinishedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ServiceOrder(UUID id, String osCode, ServiceOrderStatus status, UUID customerId, UUID vehicleId,
                        List<ServiceOrderItem> items, Money totalAmount, String diagnosisNotes,
                        String customerComments, LocalDateTime receivedAt, LocalDateTime diagnosisStartedAt,
                        LocalDateTime waitingApprovalAt, LocalDateTime executionStartedAt,
                        LocalDateTime executionFinishedAt, LocalDateTime deliveredAt,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.osCode = osCode;
        this.status = status;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.totalAmount = totalAmount;
        this.diagnosisNotes = diagnosisNotes;
        this.customerComments = customerComments;
        this.receivedAt = receivedAt;
        this.diagnosisStartedAt = diagnosisStartedAt;
        this.waitingApprovalAt = waitingApprovalAt;
        this.executionStartedAt = executionStartedAt;
        this.executionFinishedAt = executionFinishedAt;
        this.deliveredAt = deliveredAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ServiceOrder create(String osCode, UUID customerId, UUID vehicleId, String customerComments) {
        LocalDateTime now = LocalDateTime.now();
        return new ServiceOrder(
                UUID.randomUUID(), osCode, ServiceOrderStatus.RECEIVED,
                customerId, vehicleId, new ArrayList<>(), Money.zero(),
                null, customerComments, now, null, null, null, null, null, now, now);
    }

    public void startDiagnosis() {
        requireStatus(ServiceOrderStatus.RECEIVED, ServiceOrderStatus.IN_DIAGNOSIS);
        this.status = ServiceOrderStatus.IN_DIAGNOSIS;
        this.diagnosisStartedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Records the diagnosis. The order stays IN_DIAGNOSIS until Billing answers
     * with the budget (see {@link #markBudgetReady}).
     */
    public void completeDiagnosis(String notes) {
        requireStatus(ServiceOrderStatus.IN_DIAGNOSIS, ServiceOrderStatus.WAITING_APPROVAL);
        if (items.stream().noneMatch(ServiceOrderItem::isService)) {
            throw new IllegalArgumentException("A diagnosis needs at least one service to be performed");
        }
        this.diagnosisNotes = notes;
        this.updatedAt = LocalDateTime.now();
    }

    /** The budget total comes from Billing, which owns the calculation. */
    public void markBudgetReady(Money total) {
        requireStatus(ServiceOrderStatus.IN_DIAGNOSIS, ServiceOrderStatus.WAITING_APPROVAL);
        this.totalAmount = total;
        this.status = ServiceOrderStatus.WAITING_APPROVAL;
        this.waitingApprovalAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void ensureAwaitingApproval() {
        requireStatus(ServiceOrderStatus.WAITING_APPROVAL, ServiceOrderStatus.IN_EXECUTION);
    }

    /** The saga has stocked, charged and queued the order: work can begin. */
    public void startExecution() {
        requireStatus(ServiceOrderStatus.WAITING_APPROVAL, ServiceOrderStatus.IN_EXECUTION);
        this.status = ServiceOrderStatus.IN_EXECUTION;
        this.executionStartedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /** A saga step failed after the customer approved; the saga undoes what it had done. */
    public void cancel() {
        requireStatus(ServiceOrderStatus.WAITING_APPROVAL, ServiceOrderStatus.CANCELLED);
        this.status = ServiceOrderStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    public void rejectEstimate() {
        requireStatus(ServiceOrderStatus.WAITING_APPROVAL, ServiceOrderStatus.IN_DIAGNOSIS);
        this.status = ServiceOrderStatus.IN_DIAGNOSIS;
        this.waitingApprovalAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void finishExecution() {
        requireStatus(ServiceOrderStatus.IN_EXECUTION, ServiceOrderStatus.FINISHED);
        this.status = ServiceOrderStatus.FINISHED;
        this.executionFinishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void deliver() {
        requireStatus(ServiceOrderStatus.FINISHED, ServiceOrderStatus.DELIVERED);
        this.status = ServiceOrderStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void addItem(ServiceOrderItem item) {
        if (this.status != ServiceOrderStatus.RECEIVED && this.status != ServiceOrderStatus.IN_DIAGNOSIS) {
            throw new InvalidStatusTransitionException("Items can only be added when the order is RECEIVED or IN_DIAGNOSIS");
        }
        this.items.add(item);
        this.updatedAt = LocalDateTime.now();
    }

    public void removeItem(UUID itemId) {
        if (this.status != ServiceOrderStatus.IN_DIAGNOSIS) {
            throw new InvalidStatusTransitionException("Items can only be removed when the order is IN_DIAGNOSIS");
        }
        this.items.removeIf(item -> item.getId().equals(itemId));
        this.updatedAt = LocalDateTime.now();
    }

    private void requireStatus(ServiceOrderStatus required, ServiceOrderStatus target) {
        if (this.status != required) {
            throw new InvalidStatusTransitionException(this.status, target);
        }
    }

    public UUID getId() { return id; }
    public String getOsCode() { return osCode; }
    public ServiceOrderStatus getStatus() { return status; }
    public UUID getCustomerId() { return customerId; }
    public UUID getVehicleId() { return vehicleId; }
    public List<ServiceOrderItem> getItems() { return List.copyOf(items); }
    public Money getTotalAmount() { return totalAmount; }
    public String getDiagnosisNotes() { return diagnosisNotes; }
    public String getCustomerComments() { return customerComments; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public LocalDateTime getDiagnosisStartedAt() { return diagnosisStartedAt; }
    public LocalDateTime getWaitingApprovalAt() { return waitingApprovalAt; }
    public LocalDateTime getExecutionStartedAt() { return executionStartedAt; }
    public LocalDateTime getExecutionFinishedAt() { return executionFinishedAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
