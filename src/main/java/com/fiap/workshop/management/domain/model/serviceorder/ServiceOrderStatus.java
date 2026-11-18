package com.fiap.workshop.management.domain.model.serviceorder;

public enum ServiceOrderStatus {
    RECEIVED,
    IN_DIAGNOSIS,
    WAITING_APPROVAL,
    IN_EXECUTION,
    FINISHED,
    DELIVERED,
    /** The saga could not complete (no stock, payment declined, execution not queued) and was compensated. */
    CANCELLED
}
