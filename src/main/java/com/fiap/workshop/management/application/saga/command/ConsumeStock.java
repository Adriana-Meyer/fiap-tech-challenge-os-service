package com.fiap.workshop.management.application.saga.command;

import java.util.UUID;

/** To Inventory: turn the reservation into consumption once the work is done (pivot, never undone). */
public record ConsumeStock(UUID sagaId, UUID serviceOrderId) implements SagaCommand {}
