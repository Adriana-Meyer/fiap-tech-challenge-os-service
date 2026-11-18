package com.fiap.workshop.management.application.saga.event;

import java.util.UUID;

/** From Inventory: the reservation was refused, normally for lack of stock. */
public record StockReservationFailed(UUID messageId, UUID sagaId, UUID serviceOrderId, String reason) implements SagaEvent {}
