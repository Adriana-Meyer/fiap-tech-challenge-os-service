package com.fiap.workshop.management.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fiap.workshop.management.application.port.out.SagaCommandPublisher;
import com.fiap.workshop.management.application.saga.command.CancelBudget;
import com.fiap.workshop.management.application.saga.command.ConsumeStock;
import com.fiap.workshop.management.application.saga.command.EnqueueExecution;
import com.fiap.workshop.management.application.saga.command.GenerateBudget;
import com.fiap.workshop.management.application.saga.command.ProcessPayment;
import com.fiap.workshop.management.application.saga.command.RefundPayment;
import com.fiap.workshop.management.application.saga.command.ReleaseStock;
import com.fiap.workshop.management.application.saga.command.ReserveStock;
import com.fiap.workshop.management.application.saga.command.SagaCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Sends each command to the queue of the service that executes it, wrapped in
 * a {@link MessageEnvelope}. The send is synchronous and its failure is thrown,
 * so the transaction of the caller rolls back and the saga never gets ahead of
 * what was actually sent.
 */
@Component
public class SqsSagaCommandPublisher implements SagaCommandPublisher {

    private static final List<String> ENVELOPE_FIELDS = List.of("sagaId", "serviceOrderId");

    private final SqsAsyncClient sqs;
    private final ObjectMapper objectMapper;
    private final String billingQueue;
    private final String inventoryQueue;
    private final String executionQueue;
    private final ConcurrentMap<String, String> queueUrls = new ConcurrentHashMap<>();

    public SqsSagaCommandPublisher(SqsAsyncClient sqs, ObjectMapper objectMapper,
                                   @Value("${app.messaging.billing-commands-queue:billing-commands}") String billingQueue,
                                   @Value("${app.messaging.inventory-commands-queue:inventory-commands}") String inventoryQueue,
                                   @Value("${app.messaging.execution-commands-queue:execution-commands}") String executionQueue) {
        this.sqs = sqs;
        this.objectMapper = objectMapper;
        this.billingQueue = billingQueue;
        this.inventoryQueue = inventoryQueue;
        this.executionQueue = executionQueue;
    }

    @Override
    public void publish(SagaCommand command) {
        String type = command.getClass().getSimpleName();
        String queue = queueFor(command);
        ObjectNode payload = objectMapper.valueToTree(command);
        payload.remove(ENVELOPE_FIELDS);
        try {
            String body = objectMapper.writeValueAsString(new MessageEnvelope(
                    UUID.randomUUID(), command.sagaId(), command.serviceOrderId(), type, payload));
            sqs.sendMessage(request -> request.queueUrl(queueUrl(queue)).messageBody(body)).join();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + type, e);
        } catch (CompletionException e) {
            throw new IllegalStateException("Could not send " + type + " to queue " + queue
                    + " for service order " + command.serviceOrderId(), e.getCause());
        }
    }

    private String queueFor(SagaCommand command) {
        if (command instanceof GenerateBudget || command instanceof ProcessPayment
                || command instanceof CancelBudget || command instanceof RefundPayment) {
            return billingQueue;
        }
        if (command instanceof ReserveStock || command instanceof ReleaseStock || command instanceof ConsumeStock) {
            return inventoryQueue;
        }
        if (command instanceof EnqueueExecution) {
            return executionQueue;
        }
        throw new IllegalArgumentException("No queue for saga command " + command.getClass().getName());
    }

    /** Resolved once per queue; a queue that does not exist fails the send (the queues belong to Terraform). */
    private String queueUrl(String queueName) {
        return queueUrls.computeIfAbsent(queueName,
                name -> sqs.getQueueUrl(request -> request.queueName(name)).join().queueUrl());
    }
}
