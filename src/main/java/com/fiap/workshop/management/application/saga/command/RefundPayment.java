package com.fiap.workshop.management.application.saga.command;

import java.util.UUID;

/** To Billing, compensation: refund the charge (this also cancels the budget). */
public record RefundPayment(UUID sagaId, UUID serviceOrderId) implements SagaCommand {}
