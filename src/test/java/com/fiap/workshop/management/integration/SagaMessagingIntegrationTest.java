package com.fiap.workshop.management.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The OS service against the real topology, on LocalStack: it sends commands
 * to the queues of Billing, Inventory and Execution and reads the answers from
 * {@code os-saga-events}, which the {@code saga-events} topic feeds. The three
 * other services are played by the test: it reads their command queues and
 * publishes their answers to the topic, as they do.
 */
@TestPropertySource(properties = "test.recording-publisher=false")
@DisplayName("Saga messaging (integration)")
class SagaMessagingIntegrationTest extends ServiceOrderFlowSupport {

    @SuppressWarnings("resource")
    static final LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8.1"))
                    .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS);

    private static SqsClient sqs;
    private static SnsClient sns;
    private static String topicArn;
    private static String billingUrl;
    private static String inventoryUrl;
    private static String executionUrl;
    private static String deadLettersUrl;

    static {
        localstack.start();
        createTopology();
    }

    /** What the infrastructure repositories will create. */
    private static void createTopology() {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
        Region region = Region.of(localstack.getRegion());
        sqs = SqsClient.builder().endpointOverride(localstack.getEndpoint())
                .credentialsProvider(credentials).region(region).build();
        sns = SnsClient.builder().endpointOverride(localstack.getEndpoint())
                .credentialsProvider(credentials).region(region).build();

        billingUrl = sqs.createQueue(r -> r.queueName("billing-commands")).queueUrl();
        inventoryUrl = sqs.createQueue(r -> r.queueName("inventory-commands")).queueUrl();
        executionUrl = sqs.createQueue(r -> r.queueName("execution-commands")).queueUrl();

        deadLettersUrl = sqs.createQueue(r -> r.queueName("os-saga-events-dlq")).queueUrl();
        String deadLettersArn = arnOf(deadLettersUrl);
        String eventsUrl = sqs.createQueue(r -> r.queueName("os-saga-events").attributes(Map.of(
                QueueAttributeName.VISIBILITY_TIMEOUT, "2",
                QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"2\"}".formatted(deadLettersArn)))).queueUrl();

        topicArn = sns.createTopic(r -> r.name("saga-events")).topicArn();
        sns.subscribe(r -> r.topicArn(topicArn).protocol("sqs").endpoint(arnOf(eventsUrl))
                .attributes(Map.of("RawMessageDelivery", "true")));
    }

    private static String arnOf(String queueUrl) {
        return sqs.getQueueAttributes(r -> r.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.messaging.enabled", () -> "true");
        registry.add("spring.cloud.aws.sqs.endpoint", () -> localstack.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", localstack::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
    }

    private final List<JsonNode> commands = new ArrayList<>();

    // ---- the other services, played by the test ----------------------------

    /** Waits for a command of this type about this order on one of the services' queues. */
    private JsonNode awaitCommand(String queueUrl, UUID orderId, String type) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            for (JsonNode command : commands) {
                if (matches(command, orderId, type)) {
                    return command;
                }
            }
            for (Message message : sqs.receiveMessage(r -> r.queueUrl(queueUrl).waitTimeSeconds(2)
                    .maxNumberOfMessages(10)).messages()) {
                commands.add(objectMapper.readTree(message.body()));
                sqs.deleteMessage(r -> r.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            }
        }
        throw new AssertionError("No " + type + " for " + orderId + " within 30s; received " + commands);
    }

    private static boolean matches(JsonNode command, UUID orderId, String type) {
        return orderId.toString().equals(command.get("serviceOrderId").asText())
                && type.equals(command.get("type").asText());
    }

    private long commandsSent(UUID orderId, String type) {
        return commands.stream().filter(c -> matches(c, orderId, type)).count();
    }

    /** One of the services answers, through the topic. */
    private void reply(UUID messageId, UUID sagaId, UUID orderId, String type, String payload) throws Exception {
        String body = """
                {"messageId":"%s","sagaId":"%s","serviceOrderId":"%s","type":"%s","payload":%s}
                """.formatted(messageId, sagaId, orderId, type, payload);
        sns.publish(r -> r.topicArn(topicArn).message(body).messageAttributes(Map.of("type",
                MessageAttributeValue.builder().dataType("String").stringValue(type).build())));
    }

    private void reply(UUID sagaId, UUID orderId, String type, String payload) throws Exception {
        reply(UUID.randomUUID(), sagaId, orderId, type, payload);
    }

    private void eventually(String what, BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Timed out waiting for: " + what);
    }

    private boolean orderIs(UUID orderId, String status) {
        try {
            return status.equals(orderOf(orderId).get("status").asText());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean sagaIs(UUID orderId, String status) {
        try {
            return status.equals(sagaOf(orderId).get("status").asText());
        } catch (Exception e) {
            return false;
        }
    }

    // ---- tests ------------------------------------------------------------

    @Test
    @DisplayName("should take an order from diagnosis to a completed saga through the real queues")
    void shouldTakeAnOrderFromDiagnosisToACompletedSagaThroughTheRealQueues() throws Exception {
        UUID orderId = orderInDiagnosisWithItems();

        // [1] the diagnosis reaches Billing's queue
        UUID sagaId = completeDiagnosis(orderId);
        JsonNode budgetRequest = awaitCommand(billingUrl, orderId, "GenerateBudget");
        assertEquals(sagaId.toString(), budgetRequest.get("sagaId").asText());
        JsonNode items = budgetRequest.get("payload").get("items");
        assertEquals(2, items.size());
        assertTrue(items.toString().contains("\"description\":\"Oil Change\""));
        assertTrue(items.toString().contains("\"description\":\"Oil Filter\""));
        assertTrue(budgetRequest.get("payload").path("sagaId").isMissingNode());

        reply(sagaId, orderId, "BudgetGenerated", "{\"totalAmount\":200.00}");
        eventually("WAITING_APPROVAL", () -> orderIs(orderId, "WAITING_APPROVAL"));
        assertEquals(0, new BigDecimal("200.00").compareTo(orderOf(orderId).get("totalAmount").decimalValue()));

        // [2] approval: Inventory is asked for the supplies
        performPatch("/api/v1/service-orders/" + orderId + "/estimate/approve",
                "{\"paymentMethod\":\"pix\"}", adminToken).andExpect(status().isOk());
        JsonNode reserve = awaitCommand(inventoryUrl, orderId, "ReserveStock");
        assertEquals(1, reserve.get("payload").get("items").size());
        assertEquals(2, reserve.get("payload").get("items").get(0).get("quantity").asInt());
        assertEquals("b2000000-0000-0000-0000-000000000001",
                reserve.get("payload").get("items").get(0).get("supplyId").asText());

        // [3] reserved: Billing is asked to charge with the chosen method
        reply(sagaId, orderId, "StockReserved", "{}");
        assertEquals("pix", awaitCommand(billingUrl, orderId, "ProcessPayment").get("payload").get("paymentMethod").asText());

        // [4] paid: Execution is asked to queue only the services
        reply(sagaId, orderId, "PaymentApproved", "{}");
        JsonNode enqueue = awaitCommand(executionUrl, orderId, "EnqueueExecution");
        assertEquals(1, enqueue.get("payload").get("tasks").size());
        assertEquals("Oil Change", enqueue.get("payload").get("tasks").get(0).get("serviceName").asText());
        assertEquals("MECHANICAL", enqueue.get("payload").get("tasks").get(0).get("serviceType").asText());

        reply(sagaId, orderId, "ExecutionEnqueued", "{}");
        eventually("IN_EXECUTION", () -> orderIs(orderId, "IN_EXECUTION"));

        // the mechanics finish: the order is FINISHED and Inventory is told to consume (pivot)
        reply(sagaId, orderId, "ExecutionCompleted", "{}");
        eventually("FINISHED", () -> orderIs(orderId, "FINISHED"));
        awaitCommand(inventoryUrl, orderId, "ConsumeStock");

        reply(sagaId, orderId, "StockConsumed", "{}");
        eventually("saga COMPLETED", () -> sagaIs(orderId, "COMPLETED"));
    }

    @Test
    @DisplayName("should ask Inventory and Billing to undo their steps when the payment is declined, and finish when they confirm")
    void shouldAskInventoryAndBillingToUndoTheirStepsWhenThePaymentIsDeclined() throws Exception {
        UUID orderId = orderInDiagnosisWithItems();
        UUID sagaId = completeDiagnosis(orderId);
        awaitCommand(billingUrl, orderId, "GenerateBudget");
        reply(sagaId, orderId, "BudgetGenerated", "{\"totalAmount\":200.00}");
        eventually("WAITING_APPROVAL", () -> orderIs(orderId, "WAITING_APPROVAL"));
        performPatch("/api/v1/service-orders/" + orderId + "/estimate/approve",
                "{\"paymentMethod\":\"mock-decline\"}", adminToken).andExpect(status().isOk());
        awaitCommand(inventoryUrl, orderId, "ReserveStock");
        reply(sagaId, orderId, "StockReserved", "{}");
        awaitCommand(billingUrl, orderId, "ProcessPayment");

        reply(sagaId, orderId, "PaymentDeclined", "{\"reason\":\"Declined by payment method mock-decline\"}");

        eventually("CANCELLED", () -> orderIs(orderId, "CANCELLED"));
        awaitCommand(inventoryUrl, orderId, "ReleaseStock");
        awaitCommand(billingUrl, orderId, "CancelBudget");
        assertEquals("COMPENSATING", sagaOf(orderId).get("status").asText());

        reply(sagaId, orderId, "StockReleased", "{}");
        reply(sagaId, orderId, "BudgetCancelled", "{}");
        eventually("saga COMPENSATED", () -> sagaIs(orderId, "COMPENSATED"));
    }

    @Test
    @DisplayName("should handle a message delivered twice only once")
    void shouldHandleAMessageDeliveredTwiceOnlyOnce() throws Exception {
        UUID orderId = orderInDiagnosisWithItems();
        UUID sagaId = completeDiagnosis(orderId);
        awaitCommand(billingUrl, orderId, "GenerateBudget");
        reply(sagaId, orderId, "BudgetGenerated", "{\"totalAmount\":200.00}");
        eventually("WAITING_APPROVAL", () -> orderIs(orderId, "WAITING_APPROVAL"));
        performPatch("/api/v1/service-orders/" + orderId + "/estimate/approve", adminToken).andExpect(status().isOk());
        awaitCommand(inventoryUrl, orderId, "ReserveStock");

        UUID messageId = UUID.randomUUID();
        reply(messageId, sagaId, orderId, "StockReserved", "{}");
        reply(messageId, sagaId, orderId, "StockReserved", "{}");
        awaitCommand(billingUrl, orderId, "ProcessPayment");
        Thread.sleep(4_000);
        for (Message message : sqs.receiveMessage(r -> r.queueUrl(billingUrl).waitTimeSeconds(2)
                .maxNumberOfMessages(10)).messages()) {
            commands.add(objectMapper.readTree(message.body()));
        }

        assertEquals(1, commandsSent(orderId, "ProcessPayment"));
        assertEquals("PROCESS_PAYMENT", sagaOf(orderId).get("step").asText());
    }

    @Test
    @DisplayName("should move a message it cannot handle to the dead-letter queue")
    void shouldMoveAMessageItCannotHandleToTheDeadLetterQueue() throws Exception {
        UUID orderId = UUID.randomUUID();

        reply(UUID.randomUUID(), orderId, "NotAnEvent", "{}");

        long deadline = System.currentTimeMillis() + 45_000;
        List<Message> dead = List.of();
        while (dead.isEmpty() && System.currentTimeMillis() < deadline) {
            dead = sqs.receiveMessage(r -> r.queueUrl(deadLettersUrl).waitTimeSeconds(2)).messages();
        }

        assertEquals(1, dead.size());
        assertTrue(dead.get(0).body().contains(orderId.toString()));
    }
}
