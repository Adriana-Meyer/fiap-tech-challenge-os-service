package com.fiap.workshop.management.application.saga.event;

import java.util.UUID;

/** From Execution: every service of the order is done. */
public record ExecutionCompleted(UUID messageId, UUID sagaId, UUID serviceOrderId) implements SagaEvent {}
