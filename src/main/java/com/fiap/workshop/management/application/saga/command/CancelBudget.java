package com.fiap.workshop.management.application.saga.command;

import java.util.UUID;

/** To Billing, compensation: cancel the budget of an order that will not be executed. */
public record CancelBudget(UUID sagaId, UUID serviceOrderId) implements SagaCommand {}
