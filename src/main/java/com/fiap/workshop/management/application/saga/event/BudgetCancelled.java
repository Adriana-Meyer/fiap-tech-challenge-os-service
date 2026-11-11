package com.fiap.workshop.management.application.saga.event;

import java.util.UUID;

/** From Billing: compensation done, the budget is cancelled. */
public record BudgetCancelled(UUID messageId, UUID sagaId, UUID serviceOrderId) implements SagaEvent {}
