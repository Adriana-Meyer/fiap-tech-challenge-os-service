package com.fiap.workshop.management.application.saga.event;

import java.util.UUID;

/** From Inventory: the supplies are reserved. */
public record StockReserved(UUID messageId, UUID sagaId, UUID serviceOrderId) implements SagaEvent {}
