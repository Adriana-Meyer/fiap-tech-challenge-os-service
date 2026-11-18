package com.fiap.workshop.management.domain.model.saga;

/** An undo action owned by one of the other services. */
public enum Compensation {
    CANCEL_BUDGET,
    RELEASE_STOCK,
    REFUND_PAYMENT
}
