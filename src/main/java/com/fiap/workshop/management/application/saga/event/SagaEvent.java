package com.fiap.workshop.management.application.saga.event;

import java.util.UUID;

/**
 * An answer from one of the other services. The {@code messageId} identifies
 * the message itself, so the orchestrator can ignore a redelivery.
 */
public interface SagaEvent {

    UUID messageId();

    UUID sagaId();

    UUID serviceOrderId();
}
