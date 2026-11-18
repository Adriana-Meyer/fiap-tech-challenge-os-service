package com.fiap.workshop.management.application.saga.command;

import java.util.UUID;

/** A request the orchestrator sends to one of the other services. */
public interface SagaCommand {

    UUID sagaId();

    UUID serviceOrderId();
}
