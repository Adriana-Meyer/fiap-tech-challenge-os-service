package com.fiap.workshop.management.application.saga.event;

import java.util.UUID;

/** From Execution: the services are in the mechanics' queue. */
public record ExecutionEnqueued(UUID messageId, UUID sagaId, UUID serviceOrderId) implements SagaEvent {}
