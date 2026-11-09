package com.fiap.workshop.management.application.saga.command;

import java.util.UUID;

/** To Inventory, compensation: give the reserved stock back. */
public record ReleaseStock(UUID sagaId, UUID serviceOrderId) implements SagaCommand {}
