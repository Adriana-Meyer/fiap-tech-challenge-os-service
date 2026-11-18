package com.fiap.workshop.management.integration;

import com.fiap.workshop.management.application.saga.event.BudgetCancelled;
import com.fiap.workshop.management.application.saga.event.BudgetGenerated;
import com.fiap.workshop.management.application.saga.event.PaymentDeclined;
import com.fiap.workshop.management.application.saga.event.StockReleased;
import com.fiap.workshop.management.application.saga.event.StockReserved;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Saga concurrency Integration")
class SagaConcurrencyIntegrationTest extends ServiceOrderFlowSupport {

    @RepeatedTest(5)
    @DisplayName("should not lose an acknowledgement when two compensations are acknowledged at the same time")
    void shouldNotLoseAnAcknowledgementWhenTwoCompensationsAreAcknowledgedAtTheSameTime() throws Exception {
        UUID orderId = orderInDiagnosisWithItems();
        UUID sagaId = completeDiagnosis(orderId);
        answer(new BudgetGenerated(UUID.randomUUID(), sagaId, orderId, new BigDecimal("200.00")));
        performPatch("/api/v1/service-orders/" + orderId + "/estimate/approve", adminToken).andReturn();
        answer(new StockReserved(UUID.randomUUID(), sagaId, orderId));
        answer(new PaymentDeclined(UUID.randomUUID(), sagaId, orderId, "declined"));
        assertEquals("COMPENSATING", sagaOf(orderId).get("status").asText());

        // Inventory and Billing acknowledge their undo at the same instant.
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> released = pool.submit(() -> {
                await(start);
                answer(new StockReleased(UUID.randomUUID(), sagaId, orderId));
            });
            Future<?> cancelled = pool.submit(() -> {
                await(start);
                answer(new BudgetCancelled(UUID.randomUUID(), sagaId, orderId));
            });
            start.countDown();
            released.get();
            cancelled.get();
        } finally {
            pool.shutdownNow();
        }

        assertEquals("COMPENSATED", sagaOf(orderId).get("status").asText());
        assertEquals(0, sagaOf(orderId).get("pendingCompensations").size());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
