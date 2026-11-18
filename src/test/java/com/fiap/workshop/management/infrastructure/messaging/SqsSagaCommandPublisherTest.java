package com.fiap.workshop.management.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.workshop.management.application.saga.command.CancelBudget;
import com.fiap.workshop.management.application.saga.command.ConsumeStock;
import com.fiap.workshop.management.application.saga.command.EnqueueExecution;
import com.fiap.workshop.management.application.saga.command.GenerateBudget;
import com.fiap.workshop.management.application.saga.command.ProcessPayment;
import com.fiap.workshop.management.application.saga.command.RefundPayment;
import com.fiap.workshop.management.application.saga.command.ReleaseStock;
import com.fiap.workshop.management.application.saga.command.ReserveStock;
import com.fiap.workshop.management.application.saga.command.SagaCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqsSagaCommandPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SqsAsyncClient sqs = mock(SqsAsyncClient.class);
    private final SqsSagaCommandPublisher publisher =
            new SqsSagaCommandPublisher(sqs, objectMapper, "billing-commands", "inventory-commands", "execution-commands");

    private final UUID sagaId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(sqs.getQueueUrl(any(Consumer.class))).thenAnswer(invocation -> {
            GetQueueUrlRequest.Builder builder = GetQueueUrlRequest.builder();
            ((Consumer<GetQueueUrlRequest.Builder>) invocation.getArgument(0)).accept(builder);
            return CompletableFuture.completedFuture(GetQueueUrlResponse.builder()
                    .queueUrl("http://sqs/" + builder.build().queueName()).build());
        });
        when(sqs.sendMessage(any(Consumer.class))).thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));
    }

    @SuppressWarnings("unchecked")
    private List<SendMessageRequest> sent(int count) {
        ArgumentCaptor<Consumer<SendMessageRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqs, times(count)).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(consumer -> {
            SendMessageRequest.Builder builder = SendMessageRequest.builder();
            consumer.accept(builder);
            return builder.build();
        }).toList();
    }

    private void assertGoesTo(SagaCommand command, String queue, String type) throws Exception {
        org.mockito.Mockito.clearInvocations(sqs);
        publisher.publish(command);
        SendMessageRequest request = sent(1).get(0);
        JsonNode body = objectMapper.readTree(request.messageBody());
        assertEquals("http://sqs/" + queue, request.queueUrl());
        assertEquals(type, body.get("type").asText());
        assertEquals(sagaId.toString(), body.get("sagaId").asText());
        assertEquals(orderId.toString(), body.get("serviceOrderId").asText());
        assertFalse(body.get("payload").has("sagaId"));
        assertFalse(body.get("payload").has("serviceOrderId"));
        UUID.fromString(body.get("messageId").asText());
    }

    @Test
    void shouldSendEachCommandToTheQueueOfTheServiceThatExecutesIt() throws Exception {
        assertGoesTo(new GenerateBudget(sagaId, orderId, List.of()), "billing-commands", "GenerateBudget");
        assertGoesTo(new ProcessPayment(sagaId, orderId, "pix"), "billing-commands", "ProcessPayment");
        assertGoesTo(new CancelBudget(sagaId, orderId), "billing-commands", "CancelBudget");
        assertGoesTo(new RefundPayment(sagaId, orderId), "billing-commands", "RefundPayment");
        assertGoesTo(new ReserveStock(sagaId, orderId, List.of()), "inventory-commands", "ReserveStock");
        assertGoesTo(new ReleaseStock(sagaId, orderId), "inventory-commands", "ReleaseStock");
        assertGoesTo(new ConsumeStock(sagaId, orderId), "inventory-commands", "ConsumeStock");
        assertGoesTo(new EnqueueExecution(sagaId, orderId, List.of()), "execution-commands", "EnqueueExecution");
    }

    @Test
    void shouldPutOnlyTheCommandFieldsInThePayload() throws Exception {
        UUID supplyId = UUID.randomUUID();
        publisher.publish(new GenerateBudget(sagaId, orderId,
                List.of(new GenerateBudget.Line("Labor", new BigDecimal("100.00"), 2))));
        publisher.publish(new ReserveStock(sagaId, orderId, List.of(new ReserveStock.Line(supplyId, 3))));
        publisher.publish(new ProcessPayment(sagaId, orderId, "pix"));

        List<SendMessageRequest> requests = sent(3);
        JsonNode budget = objectMapper.readTree(requests.get(0).messageBody()).get("payload");
        assertEquals("Labor", budget.get("items").get(0).get("description").asText());
        assertEquals(0, new BigDecimal("100.00").compareTo(budget.get("items").get(0).get("unitPrice").decimalValue()));
        assertEquals(2, budget.get("items").get(0).get("quantity").asInt());
        JsonNode stock = objectMapper.readTree(requests.get(1).messageBody()).get("payload");
        assertEquals(supplyId.toString(), stock.get("items").get(0).get("supplyId").asText());
        assertEquals("pix", objectMapper.readTree(requests.get(2).messageBody()).get("payload").get("paymentMethod").asText());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldResolveEachQueueUrlOnlyOnce() {
        publisher.publish(new CancelBudget(sagaId, orderId));
        publisher.publish(new RefundPayment(sagaId, orderId));
        publisher.publish(new ReleaseStock(sagaId, orderId));
        sent(3); // the request builders run here, which is when the queue url is looked up

        verify(sqs, times(2)).getQueueUrl(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowWhenTheSendFailsSoTheCallersTransactionRollsBack() {
        when(sqs.sendMessage(any(Consumer.class))).thenReturn(CompletableFuture.failedFuture(
                SqsException.builder().message("boom").build()));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> publisher.publish(new CancelBudget(sagaId, orderId)));

        assertTrue(e.getMessage().contains("CancelBudget"));
        assertTrue(e.getMessage().contains("billing-commands"));
        assertTrue(e.getMessage().contains(orderId.toString()));
    }

    @Test
    void shouldRefuseACommandWithNoQueue() {
        SagaCommand unknown = new SagaCommand() {
            public UUID sagaId() { return sagaId; }
            public UUID serviceOrderId() { return orderId; }
        };

        assertThrows(IllegalArgumentException.class, () -> publisher.publish(unknown));
    }
}
