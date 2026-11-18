package com.fiap.workshop.management.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fiap.workshop.management.application.port.in.saga.HandleSagaEventInputPort;
import com.fiap.workshop.management.application.saga.event.BudgetCancelled;
import com.fiap.workshop.management.application.saga.event.BudgetGenerated;
import com.fiap.workshop.management.application.saga.event.ExecutionCompleted;
import com.fiap.workshop.management.application.saga.event.ExecutionEnqueueFailed;
import com.fiap.workshop.management.application.saga.event.ExecutionEnqueued;
import com.fiap.workshop.management.application.saga.event.PaymentApproved;
import com.fiap.workshop.management.application.saga.event.PaymentDeclined;
import com.fiap.workshop.management.application.saga.event.PaymentRefunded;
import com.fiap.workshop.management.application.saga.event.SagaEvent;
import com.fiap.workshop.management.application.saga.event.StockConsumed;
import com.fiap.workshop.management.application.saga.event.StockReleased;
import com.fiap.workshop.management.application.saga.event.StockReservationFailed;
import com.fiap.workshop.management.application.saga.event.StockReserved;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reads the answers of the other services from the {@code os-saga-events}
 * queue, which the {@code saga-events} topic feeds. A message that fails (bad
 * JSON, unknown type, a handler that throws) is not acknowledged: SQS delivers
 * it again and, after the queue's maximum receive count, moves it to the
 * dead-letter queue.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class SagaEventListener {

    private static final Logger log = LoggerFactory.getLogger(SagaEventListener.class);

    private final ObjectMapper objectMapper;
    private final HandleSagaEventInputPort handler;

    public SagaEventListener(ObjectMapper objectMapper, HandleSagaEventInputPort handler) {
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    @SqsListener("${app.messaging.events-queue}")
    public void onMessage(String body) throws JsonProcessingException {
        MessageEnvelope envelope = objectMapper.readValue(body, MessageEnvelope.class);
        MDC.put("sagaId", String.valueOf(envelope.sagaId()));
        try {
            log.info("Received {} for service order {}", envelope.type(), envelope.serviceOrderId());
            handler.handle(toEvent(envelope));
        } finally {
            MDC.remove("sagaId");
        }
    }

    private SagaEvent toEvent(MessageEnvelope envelope) throws JsonProcessingException {
        Class<? extends SagaEvent> type = switch (String.valueOf(envelope.type())) {
            case "BudgetGenerated" -> BudgetGenerated.class;
            case "StockReserved" -> StockReserved.class;
            case "StockReservationFailed" -> StockReservationFailed.class;
            case "PaymentApproved" -> PaymentApproved.class;
            case "PaymentDeclined" -> PaymentDeclined.class;
            case "ExecutionEnqueued" -> ExecutionEnqueued.class;
            case "ExecutionEnqueueFailed" -> ExecutionEnqueueFailed.class;
            case "ExecutionCompleted" -> ExecutionCompleted.class;
            case "StockConsumed" -> StockConsumed.class;
            case "StockReleased" -> StockReleased.class;
            case "BudgetCancelled" -> BudgetCancelled.class;
            case "PaymentRefunded" -> PaymentRefunded.class;
            default -> throw new IllegalArgumentException("Unknown saga event type: " + envelope.type());
        };
        ObjectNode fields = envelope.payload() instanceof ObjectNode payload
                ? payload.deepCopy() : objectMapper.createObjectNode();
        fields.put("messageId", String.valueOf(envelope.messageId()));
        fields.put("sagaId", String.valueOf(envelope.sagaId()));
        fields.put("serviceOrderId", String.valueOf(envelope.serviceOrderId()));
        return objectMapper.treeToValue(fields, type);
    }
}
