package com.fiap.workshop.management.application.saga.event;

import java.util.UUID;

/** From Billing: compensation done, the charge is refunded. */
public record PaymentRefunded(UUID messageId, UUID sagaId, UUID serviceOrderId) implements SagaEvent {}
