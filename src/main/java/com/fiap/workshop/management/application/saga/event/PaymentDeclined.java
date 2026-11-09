package com.fiap.workshop.management.application.saga.event;

import java.util.UUID;

/** From Billing: the charge was declined. */
public record PaymentDeclined(UUID messageId, UUID sagaId, UUID serviceOrderId, String reason) implements SagaEvent {}
