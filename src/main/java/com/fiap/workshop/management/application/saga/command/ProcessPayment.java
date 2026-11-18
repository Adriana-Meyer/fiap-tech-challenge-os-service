package com.fiap.workshop.management.application.saga.command;

import java.util.UUID;

/** To Billing: charge the approved budget. */
public record ProcessPayment(UUID sagaId, UUID serviceOrderId, String paymentMethod) implements SagaCommand {}
