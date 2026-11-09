package com.fiap.workshop.management.infrastructure.messaging;

import com.fiap.workshop.management.application.port.out.SagaCommandPublisher;
import com.fiap.workshop.management.application.saga.command.SagaCommand;
import org.springframework.stereotype.Component;

/**
 * Placeholder until the SQS adapter exists. It fails loudly, so a request that
 * needs the saga returns an error and rolls back, instead of leaving an order
 * waiting forever for an answer that will never come. The adapter that sends
 * to the queues replaces this class.
 */
@Component
public class UnavailableSagaCommandPublisher implements SagaCommandPublisher {

    @Override
    public void publish(SagaCommand command) {
        throw new IllegalStateException("No messaging adapter is configured to send "
                + command.getClass().getSimpleName() + " for service order " + command.serviceOrderId());
    }
}
