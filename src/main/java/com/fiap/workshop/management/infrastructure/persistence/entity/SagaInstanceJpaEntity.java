package com.fiap.workshop.management.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "saga_instance")
public class SagaInstanceJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "CHAR(36)", nullable = false)
    private String id;

    @Column(name = "service_order_id", columnDefinition = "CHAR(36)", nullable = false)
    private String serviceOrderId;

    @Column(name = "step", length = 30, nullable = false)
    private String step;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "pending_compensations", length = 100)
    private String pendingCompensations;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SagaInstanceJpaEntity() {}

    public SagaInstanceJpaEntity(String id, String serviceOrderId, String step, String status,
                                 String paymentMethod, String pendingCompensations, String failureReason,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.serviceOrderId = serviceOrderId;
        this.step = step;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.pendingCompensations = pendingCompensations;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getServiceOrderId() { return serviceOrderId; }
    public String getStep() { return step; }
    public String getStatus() { return status; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getPendingCompensations() { return pendingCompensations; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
