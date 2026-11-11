package com.fiap.workshop.management.domain.model.saga;

public enum SagaStatus {
    RUNNING,
    /** Every step ran and was acknowledged. */
    COMPLETED,
    /** The customer rejected the budget; the order went back to diagnosis. */
    REJECTED,
    /** A step failed and the compensations were sent, not all acknowledged yet. */
    COMPENSATING,
    /** A step failed and every compensation was acknowledged. */
    COMPENSATED
}
