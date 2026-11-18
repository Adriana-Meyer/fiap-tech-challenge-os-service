package com.fiap.workshop.management.domain.model.saga;

/** What the saga is waiting for next. */
public enum SagaStep {
    /** Billing is calculating the budget. */
    GENERATE_BUDGET,
    /** The budget is ready; the customer has to approve or reject it. */
    AWAIT_APPROVAL,
    /** Inventory is reserving the supplies. */
    RESERVE_STOCK,
    /** Billing is charging the customer. */
    PROCESS_PAYMENT,
    /** Execution is queueing the services for the mechanics. */
    ENQUEUE_EXECUTION,
    /** The mechanics are working; Execution will report when done. */
    EXECUTING,
    /** Inventory is turning the reservation into consumption (the pivot step: it is never undone). */
    CONSUME_STOCK
}
