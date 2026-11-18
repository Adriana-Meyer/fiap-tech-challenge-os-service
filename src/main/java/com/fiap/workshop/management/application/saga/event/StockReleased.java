package com.fiap.workshop.management.application.saga.event;

import java.util.UUID;

/** From Inventory: compensation done, the stock is back. */
public record StockReleased(UUID messageId, UUID sagaId, UUID serviceOrderId) implements SagaEvent {}
