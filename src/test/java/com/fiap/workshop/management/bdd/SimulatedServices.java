package com.fiap.workshop.management.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Billing, Inventory and Execution as the saga sees them: each one listens on
 * its command queue, does what the real service does (a decline for the
 * payment method {@code mock-decline}, a refusal when the stock is short) and
 * answers through the {@code saga-events} topic. It keeps the state the real
 * services keep in their databases, so a scenario can check what each one did.
 */
public class SimulatedServices implements AutoCloseable {

    private final SqsClient sqs;
    private final SnsClient sns;
    private final String topicArn;
    private final ObjectMapper objectMapper;
    private final ExecutorService listeners = Executors.newFixedThreadPool(3);
    private volatile boolean running = true;

    // Inventory
    private final Map<UUID, Integer> stock = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Integer>> reserved = new ConcurrentHashMap<>();
    private final Map<UUID, String> reservations = new ConcurrentHashMap<>();
    // Billing
    private final Map<UUID, String> budgets = new ConcurrentHashMap<>();
    private final Map<UUID, String> payments = new ConcurrentHashMap<>();
    // Execution
    private final Map<UUID, UUID> executions = new ConcurrentHashMap<>();
    private volatile boolean executionQueueBroken;

    private final List<String> received = new CopyOnWriteArrayList<>();

    public SimulatedServices(SqsClient sqs, SnsClient sns, String topicArn, ObjectMapper objectMapper,
                             String billingQueue, String inventoryQueue, String executionQueue) {
        this.sqs = sqs;
        this.sns = sns;
        this.topicArn = topicArn;
        this.objectMapper = objectMapper;
        for (String queue : List.of(billingQueue, inventoryQueue, executionQueue)) {
            listeners.submit(() -> listen(queue));
        }
    }

    // ---- what a scenario sets up and checks -------------------------------------

    /** A clean slate for the next scenario. */
    public void reset() {
        stock.clear();
        reserved.clear();
        reservations.clear();
        budgets.clear();
        payments.clear();
        executions.clear();
        received.clear();
        executionQueueBroken = false;
    }

    public void setStock(UUID supplyId, int quantity) {
        stock.put(supplyId, quantity);
    }

    public int stockOf(UUID supplyId) {
        return stock.getOrDefault(supplyId, 0);
    }

    public void breakExecutionQueue() {
        executionQueueBroken = true;
    }

    public String reservationOf(UUID orderId) {
        return reservations.get(orderId);
    }

    public String budgetOf(UUID orderId) {
        return budgets.get(orderId);
    }

    public String paymentOf(UUID orderId) {
        return payments.get(orderId);
    }

    public long commandsOf(String type, UUID orderId) {
        return received.stream().filter((type + ":" + orderId)::equals).count();
    }

    /** The mechanics finish the last task: Execution tells the saga. */
    public void completeExecution(UUID orderId) {
        UUID sagaId = executions.get(orderId);
        if (sagaId == null) {
            throw new AssertionError("Execution never queued order " + orderId);
        }
        publish(UUID.randomUUID(), sagaId, orderId, "ExecutionCompleted", "{}");
    }

    // ---- the services ------------------------------------------------------

    private void listen(String queueUrl) {
        while (running) {
            try {
                for (Message message : sqs.receiveMessage(r -> r.queueUrl(queueUrl)
                        .waitTimeSeconds(1).maxNumberOfMessages(10)).messages()) {
                    handle(objectMapper.readTree(message.body()));
                    sqs.deleteMessage(r -> r.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("Simulated service failed on " + queueUrl + ": " + e);
                }
            }
        }
    }

    private void handle(JsonNode command) {
        String type = command.get("type").asText();
        UUID commandId = UUID.fromString(command.get("messageId").asText());
        UUID sagaId = UUID.fromString(command.get("sagaId").asText());
        UUID orderId = UUID.fromString(command.get("serviceOrderId").asText());
        JsonNode payload = command.get("payload");
        received.add(type + ":" + orderId);

        // A redelivered command gets the same answer (same messageId), as in the real services.
        UUID answerId = UUID.nameUUIDFromBytes((commandId + type).getBytes(StandardCharsets.UTF_8));
        switch (type) {
            case "GenerateBudget" -> {
                BigDecimal total = BigDecimal.ZERO;
                for (JsonNode line : payload.get("items")) {
                    total = total.add(line.get("unitPrice").decimalValue().multiply(line.get("quantity").decimalValue()));
                }
                budgets.put(orderId, "GENERATED");
                publish(answerId, sagaId, orderId, "BudgetGenerated", "{\"totalAmount\":" + total + "}");
            }
            case "ProcessPayment" -> {
                if ("mock-decline".equals(payload.get("paymentMethod").asText())) {
                    payments.put(orderId, "DECLINED");
                    publish(answerId, sagaId, orderId, "PaymentDeclined",
                            "{\"reason\":\"Declined by payment method mock-decline\"}");
                } else {
                    payments.put(orderId, "APPROVED");
                    budgets.put(orderId, "PAID");
                    publish(answerId, sagaId, orderId, "PaymentApproved", "{}");
                }
            }
            case "CancelBudget" -> {
                budgets.put(orderId, "CANCELLED");
                publish(answerId, sagaId, orderId, "BudgetCancelled", "{}");
            }
            case "RefundPayment" -> {
                payments.put(orderId, "REFUNDED");
                budgets.put(orderId, "CANCELLED");
                publish(answerId, sagaId, orderId, "PaymentRefunded", "{}");
            }
            case "ReserveStock" -> reserveStock(answerId, sagaId, orderId, payload);
            case "ReleaseStock" -> {
                reserved.getOrDefault(orderId, Map.of()).forEach((supply, quantity) -> stock.merge(supply, quantity, Integer::sum));
                reservations.put(orderId, "RELEASED");
                publish(answerId, sagaId, orderId, "StockReleased", "{}");
            }
            case "ConsumeStock" -> {
                reservations.put(orderId, "CONSUMED");
                publish(answerId, sagaId, orderId, "StockConsumed", "{}");
            }
            case "EnqueueExecution" -> {
                if (executionQueueBroken) {
                    publish(answerId, sagaId, orderId, "ExecutionEnqueueFailed", "{\"reason\":\"Queue unavailable\"}");
                } else {
                    executions.put(orderId, sagaId);
                    publish(answerId, sagaId, orderId, "ExecutionEnqueued", "{}");
                }
            }
            default -> throw new IllegalArgumentException("Unknown command " + type);
        }
    }

    /** All or nothing: a reservation that does not fit reserves none of its lines. */
    private void reserveStock(UUID answerId, UUID sagaId, UUID orderId, JsonNode payload) {
        Map<UUID, Integer> wanted = new ConcurrentHashMap<>();
        for (JsonNode line : payload.get("items")) {
            wanted.merge(UUID.fromString(line.get("supplyId").asText()), line.get("quantity").asInt(), Integer::sum);
        }
        boolean fits = wanted.entrySet().stream().allMatch(e -> stockOf(e.getKey()) >= e.getValue());
        if (!fits) {
            publish(answerId, sagaId, orderId, "StockReservationFailed", "{\"reason\":\"Insufficient stock\"}");
            return;
        }
        wanted.forEach((supply, quantity) -> stock.merge(supply, -quantity, Integer::sum));
        reserved.put(orderId, wanted);
        reservations.put(orderId, "RESERVED");
        publish(answerId, sagaId, orderId, "StockReserved", "{}");
    }

    private void publish(UUID messageId, UUID sagaId, UUID orderId, String type, String payload) {
        String body = "{\"messageId\":\"%s\",\"sagaId\":\"%s\",\"serviceOrderId\":\"%s\",\"type\":\"%s\",\"payload\":%s}"
                .formatted(messageId, sagaId, orderId, type, payload);
        sns.publish(r -> r.topicArn(topicArn).message(body).messageAttributes(Map.of("type",
                MessageAttributeValue.builder().dataType("String").stringValue(type).build())));
    }

    @Override
    public void close() {
        running = false;
        listeners.shutdownNow();
    }
}
