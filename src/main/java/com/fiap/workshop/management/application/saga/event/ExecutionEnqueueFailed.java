package com.fiap.workshop.management.application.saga.event;

import java.util.UUID;

/** From Execution: the order could not be queued. */
public record ExecutionEnqueueFailed(UUID messageId, UUID sagaId, UUID serviceOrderId, String reason) implements SagaEvent {}
