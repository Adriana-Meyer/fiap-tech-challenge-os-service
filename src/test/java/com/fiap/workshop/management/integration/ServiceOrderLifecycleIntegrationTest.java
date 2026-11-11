package com.fiap.workshop.management.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fiap.workshop.management.application.saga.command.ConsumeStock;
import com.fiap.workshop.management.application.saga.command.EnqueueExecution;
import com.fiap.workshop.management.application.saga.command.GenerateBudget;
import com.fiap.workshop.management.application.saga.command.ProcessPayment;
import com.fiap.workshop.management.application.saga.command.ReserveStock;
import com.fiap.workshop.management.application.saga.event.BudgetGenerated;
import com.fiap.workshop.management.application.saga.event.ExecutionCompleted;
import com.fiap.workshop.management.application.saga.event.ExecutionEnqueued;
import com.fiap.workshop.management.application.saga.event.PaymentApproved;
import com.fiap.workshop.management.application.saga.event.StockConsumed;
import com.fiap.workshop.management.application.saga.event.StockReserved;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Service Order Lifecycle Integration")
class ServiceOrderLifecycleIntegrationTest extends ServiceOrderFlowSupport {

    @Test
    @DisplayName("should take an order from diagnosis to delivery through the saga when every service answers")
    void shouldTakeAnOrderFromDiagnosisToDeliveryThroughTheSaga() throws Exception {
        UUID orderId = orderInDiagnosisWithItems();

        // [1] diagnosis -> Billing calculates the budget; the order waits meanwhile
        UUID sagaId = completeDiagnosis(orderId);
        assertEquals("IN_DIAGNOSIS", orderOf(orderId).get("status").asText());
        GenerateBudget budgetRequest = publisher.last(GenerateBudget.class);
        assertEquals(sagaId, budgetRequest.sagaId());
        assertEquals(2, budgetRequest.items().size());
        // The order does not guarantee the order of the items, so look them up by name.
        GenerateBudget.Line oilChange = line(budgetRequest, "Oil Change");
        GenerateBudget.Line oilFilter = line(budgetRequest, "Oil Filter");
        assertEquals(0, new BigDecimal("150.00").compareTo(oilChange.unitPrice()));
        assertEquals(0, new BigDecimal("25.00").compareTo(oilFilter.unitPrice()));
        assertEquals(2, oilFilter.quantity());

        answer(new BudgetGenerated(UUID.randomUUID(), sagaId, orderId, new BigDecimal("200.00")));
        JsonNode waiting = orderOf(orderId);
        assertEquals("WAITING_APPROVAL", waiting.get("status").asText());
        assertEquals(0, new BigDecimal("200.00").compareTo(waiting.get("totalAmount").decimalValue()));
        assertEquals("AWAIT_APPROVAL", sagaOf(orderId).get("step").asText());

        // [2] approval -> Inventory reserves the supplies; the order is still waiting
        performPatch("/api/v1/service-orders/" + orderId + "/estimate/approve",
                "{\"paymentMethod\":\"pix\"}", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_APPROVAL"));
        ReserveStock reserve = publisher.last(ReserveStock.class);
        assertEquals(1, reserve.items().size());
        assertEquals(2, reserve.items().get(0).quantity());

        // [3] stock reserved -> Billing charges with the chosen method
        answer(new StockReserved(UUID.randomUUID(), sagaId, orderId));
        assertEquals("pix", publisher.last(ProcessPayment.class).paymentMethod());

        // [4] payment approved -> Execution queues only the services
        answer(new PaymentApproved(UUID.randomUUID(), sagaId, orderId));
        EnqueueExecution enqueue = publisher.last(EnqueueExecution.class);
        assertEquals(1, enqueue.tasks().size());
        assertEquals("Oil Change", enqueue.tasks().get(0).serviceName());
        assertEquals("MECHANICAL", enqueue.tasks().get(0).serviceType());

        answer(new ExecutionEnqueued(UUID.randomUUID(), sagaId, orderId));
        JsonNode inExecution = orderOf(orderId);
        assertEquals("IN_EXECUTION", inExecution.get("status").asText());
        assertTrue(inExecution.hasNonNull("executionStartedAt"));

        // the mechanics finish -> order FINISHED, Inventory consumes the reservation (pivot)
        answer(new ExecutionCompleted(UUID.randomUUID(), sagaId, orderId));
        assertEquals("FINISHED", orderOf(orderId).get("status").asText());
        publisher.last(ConsumeStock.class);

        answer(new StockConsumed(UUID.randomUUID(), sagaId, orderId));
        JsonNode saga = sagaOf(orderId);
        assertEquals("COMPLETED", saga.get("status").asText());

        performPatch("/api/v1/service-orders/" + orderId + "/deliver", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty());
    }

    private static GenerateBudget.Line line(GenerateBudget request, String description) {
        return request.items().stream().filter(l -> l.description().equals(description)).findFirst()
                .orElseThrow(() -> new AssertionError("No line " + description + " in " + request.items()));
    }

    @Test
    @DisplayName("should keep a snapshot of the supply on the order item")
    void shouldKeepASnapshotOfTheSupplyOnTheOrderItem() throws Exception {
        UUID orderId = orderInDiagnosisWithItems();

        JsonNode items = orderOf(orderId).get("items");
        JsonNode supplyItem = items.get(0).hasNonNull("supplyId") ? items.get(0) : items.get(1);

        assertEquals("b2000000-0000-0000-0000-000000000001", supplyItem.get("supplyId").asText());
        assertEquals("Oil Filter", supplyItem.get("supplyName").asText());
        assertEquals(0, new BigDecimal("25.00").compareTo(supplyItem.get("unitPrice").decimalValue()));
        assertEquals(0, new BigDecimal("50.00").compareTo(supplyItem.get("subtotal").decimalValue()));
    }

    @Test
    @DisplayName("should answer 404 when the supply is unknown to Inventory")
    void shouldAnswer404WhenTheSupplyIsUnknownToInventory() throws Exception {
        UUID orderId = createServiceOrder();
        performPost("/api/v1/service-orders/" + orderId + "/items",
                "{\"supplyId\":\"%s\",\"quantity\":1}".formatted(UUID.randomUUID()), adminToken)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should return the order to IN_DIAGNOSIS when the estimate is rejected and allow a new budget")
    void shouldReturnTheOrderToInDiagnosisWhenTheEstimateIsRejected() throws Exception {
        UUID orderId = orderInDiagnosisWithItems();
        UUID firstSaga = completeDiagnosis(orderId);
        answer(new BudgetGenerated(UUID.randomUUID(), firstSaga, orderId, new BigDecimal("200.00")));

        performPatch("/api/v1/service-orders/" + orderId + "/estimate/reject", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_DIAGNOSIS"));
        assertEquals("REJECTED", sagaOf(orderId).get("status").asText());

        UUID secondSaga = completeDiagnosis(orderId);
        assertTrue(!firstSaga.equals(secondSaga));
        assertEquals("GENERATE_BUDGET", sagaOf(orderId).get("step").asText());
    }

    @Test
    @DisplayName("should not complete a diagnosis with no service to perform")
    void shouldNotCompleteADiagnosisWithNoServiceToPerform() throws Exception {
        UUID orderId = createServiceOrder();
        patchOk("/api/v1/service-orders/" + orderId + "/diagnosis/start", mechanicToken);

        performPost("/api/v1/service-orders/" + orderId + "/diagnosis/complete",
                "{\"diagnosisNotes\":\"Nothing to do\"}", mechanicToken)
                .andExpect(status().isBadRequest());
        assertTrue(publisher.commands().isEmpty());
    }

    @Test
    @DisplayName("should list all service orders when multiple orders exist")
    void shouldListAllServiceOrdersWhenMultipleOrdersExist() throws Exception {
        createServiceOrder();
        createServiceOrder();

        mockMvc.perform(get("/api/v1/service-orders").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("should return 400 when completing diagnosis with blank notes")
    void shouldReturn400WhenCompletingDiagnosisWithBlankNotes() throws Exception {
        UUID orderId = createServiceOrder();
        performPatch("/api/v1/service-orders/" + orderId + "/diagnosis/start", mechanicToken);

        performPost("/api/v1/service-orders/" + orderId + "/diagnosis/complete",
                "{\"diagnosisNotes\":\"\"}", mechanicToken)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return tracking info without authentication")
    void shouldReturnTrackingInfoWithoutAuthentication() throws Exception {
        UUID orderId = createServiceOrder();
        String osCode = orderOf(orderId).get("osCode").asText();

        mockMvc.perform(get("/api/v1/tracking/" + osCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.osCode").value(osCode))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    @DisplayName("should no longer expose the endpoints that moved to other services")
    void shouldNoLongerExposeTheEndpointsThatMovedToOtherServices() throws Exception {
        UUID orderId = createServiceOrder();
        mockMvc.perform(get("/api/v1/supplies").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/service-orders/analytics/avg-execution-time")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is4xxClientError());
        performPatch("/api/v1/service-orders/" + orderId + "/items/" + UUID.randomUUID() + "/start", mechanicToken)
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("should issue tokens carrying the role claim the other services rely on")
    void shouldIssueTokensCarryingTheRoleClaim() {
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(
                        "test-secret-key-for-unit-tests-must-be-at-least-64-characters-long-ok"
                                .getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(mechanicToken).getPayload();

        assertEquals("MECHANIC", claims.get("role", String.class));
        assertEquals("66633399901", claims.getSubject());
    }
}
