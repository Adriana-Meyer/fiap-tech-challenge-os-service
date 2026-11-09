package com.fiap.workshop.management.application.saga.event;

import java.util.UUID;

/** From Inventory: the reservation was consumed (last step of the saga). */
public record StockConsumed(UUID messageId, UUID sagaId, UUID serviceOrderId) implements SagaEvent {}
