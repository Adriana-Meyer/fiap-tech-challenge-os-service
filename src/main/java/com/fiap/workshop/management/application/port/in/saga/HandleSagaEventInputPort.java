package com.fiap.workshop.management.application.port.in.saga;

import com.fiap.workshop.management.application.saga.event.SagaEvent;

public interface HandleSagaEventInputPort {

    /** Moves the saga with an answer from another service. Idempotent per message id. */
    void handle(SagaEvent event);
}
