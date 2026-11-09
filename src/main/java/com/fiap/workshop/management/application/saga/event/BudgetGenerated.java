package com.fiap.workshop.management.application.saga.event;

import java.math.BigDecimal;
import java.util.UUID;

/** From Billing: the budget is calculated. */
public record BudgetGenerated(UUID messageId, UUID sagaId, UUID serviceOrderId, BigDecimal totalAmount)
        implements SagaEvent {}
