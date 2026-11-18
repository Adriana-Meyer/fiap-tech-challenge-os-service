package com.fiap.workshop.management.application.port.out;

import com.fiap.workshop.management.application.saga.command.SagaCommand;

/**
 * Sends a saga command to the service that executes it. A failure must be
 * thrown, not swallowed: the caller's transaction then rolls back and the
 * saga state never gets ahead of what was actually sent.
 */
public interface SagaCommandPublisher {

    void publish(SagaCommand command);
}
