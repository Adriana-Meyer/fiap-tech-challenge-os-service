package com.fiap.workshop.management.application.saga.event;

import java.util.UUID;

/** From Billing: the charge was approved. */
public record PaymentApproved(UUID messageId, UUID sagaId, UUID serviceOrderId) implements SagaEvent {}
