package com.fiap.workshop.management.domain.model.serviceorder;

import com.fiap.workshop.management.domain.exception.InvalidStatusTransitionException;
import com.fiap.workshop.management.domain.model.catalog.ServiceCatalogItem;
import com.fiap.workshop.management.domain.model.catalog.ServiceType;
import com.fiap.workshop.management.domain.model.shared.Money;
import com.fiap.workshop.management.domain.model.supply.SupplySnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ServiceOrder")
class ServiceOrderTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();

    private ServiceOrder newOrder() {
        return ServiceOrder.create("OS-2026-00001", CUSTOMER_ID, VEHICLE_ID, "Test comments");
    }

    private ServiceOrder inDiagnosis() {
        ServiceOrder order = newOrder();
        order.startDiagnosis();
        return order;
    }

    private ServiceOrder diagnosed() {
        ServiceOrder order = inDiagnosis();
        order.addItem(ServiceOrderItem.createServiceItem(order.getId(), catalogItem(150.00), 2));
        order.completeDiagnosis("Diagnosis notes");
        return order;
    }

    private ServiceOrder waitingApproval() {
        ServiceOrder order = diagnosed();
        order.markBudgetReady(Money.of(300.00));
        return order;
    }

    private ServiceOrder inExecution() {
        ServiceOrder order = waitingApproval();
        order.startExecution();
        return order;
    }

    private ServiceCatalogItem catalogItem(double price) {
        return ServiceCatalogItem.create("Oil Change", "desc", ServiceType.MECHANICAL, Money.of(price));
    }

    @Test
    @DisplayName("should initialize with RECEIVED status and zero total when created")
    void shouldInitializeWithReceivedStatusAndZeroTotalWhenCreated() {
        ServiceOrder order = newOrder();

        assertEquals(ServiceOrderStatus.RECEIVED, order.getStatus());
        assertEquals(Money.zero(), order.getTotalAmount());
        assertEquals("OS-2026-00001", order.getOsCode());
        assertEquals(CUSTOMER_ID, order.getCustomerId());
        assertEquals(VEHICLE_ID, order.getVehicleId());
        assertNotNull(order.getId());
        assertNotNull(order.getReceivedAt());
        assertNotNull(order.getCreatedAt());
        assertTrue(order.getItems().isEmpty());
    }

    @Test
    @DisplayName("should transition to IN_DIAGNOSIS when startDiagnosis is called")
    void shouldTransitionToInDiagnosisWhenStartDiagnosisIsCalled() {
        ServiceOrder order = newOrder();
        order.startDiagnosis();

        assertEquals(ServiceOrderStatus.IN_DIAGNOSIS, order.getStatus());
        assertNotNull(order.getDiagnosisStartedAt());
    }

    @Test
    @DisplayName("should throw InvalidStatusTransitionException when startDiagnosis is called and status is not RECEIVED")
    void shouldThrowWhenStartDiagnosisCalledAndStatusIsNotReceived() {
        ServiceOrder order = inDiagnosis();
        assertThrows(InvalidStatusTransitionException.class, order::startDiagnosis);
    }

    @Test
    @DisplayName("should record the notes and stay IN_DIAGNOSIS when completeDiagnosis is called")
    void shouldRecordNotesAndStayInDiagnosisWhenCompleteDiagnosisIsCalled() {
        ServiceOrder order = diagnosed();

        assertEquals(ServiceOrderStatus.IN_DIAGNOSIS, order.getStatus());
        assertEquals("Diagnosis notes", order.getDiagnosisNotes());
        assertNull(order.getWaitingApprovalAt());
        assertEquals(Money.zero(), order.getTotalAmount());
    }

    @Test
    @DisplayName("should throw InvalidStatusTransitionException when completeDiagnosis is called and status is not IN_DIAGNOSIS")
    void shouldThrowWhenCompleteDiagnosisCalledAndStatusIsNotInDiagnosis() {
        ServiceOrder order = newOrder();
        assertThrows(InvalidStatusTransitionException.class, () -> order.completeDiagnosis("notes"));
    }

    @Test
    @DisplayName("should require at least one service when completeDiagnosis is called")
    void shouldRequireAtLeastOneServiceWhenCompleteDiagnosisIsCalled() {
        ServiceOrder empty = inDiagnosis();
        assertThrows(IllegalArgumentException.class, () -> empty.completeDiagnosis("notes"));

        ServiceOrder onlySupplies = inDiagnosis();
        onlySupplies.addItem(ServiceOrderItem.createSupplyItem(onlySupplies.getId(),
                new SupplySnapshot(UUID.randomUUID(), "Oil filter", Money.of(35.50)), 1));
        assertThrows(IllegalArgumentException.class, () -> onlySupplies.completeDiagnosis("notes"));
    }

    @Test
    @DisplayName("should move to WAITING_APPROVAL with the total from Billing when markBudgetReady is called")
    void shouldMoveToWaitingApprovalWithTheTotalFromBillingWhenMarkBudgetReadyIsCalled() {
        ServiceOrder order = diagnosed();
        order.markBudgetReady(Money.of(300.00));

        assertEquals(ServiceOrderStatus.WAITING_APPROVAL, order.getStatus());
        assertEquals(Money.of(300.00), order.getTotalAmount());
        assertNotNull(order.getWaitingApprovalAt());
    }

    @Test
    @DisplayName("should throw InvalidStatusTransitionException when markBudgetReady is called and status is not IN_DIAGNOSIS")
    void shouldThrowWhenMarkBudgetReadyCalledAndStatusIsNotInDiagnosis() {
        ServiceOrder order = waitingApproval();
        assertThrows(InvalidStatusTransitionException.class, () -> order.markBudgetReady(Money.of(1)));
    }

    @Test
    @DisplayName("should accept ensureAwaitingApproval only when WAITING_APPROVAL")
    void shouldAcceptEnsureAwaitingApprovalOnlyWhenWaitingApproval() {
        assertDoesNotThrow(waitingApproval()::ensureAwaitingApproval);
        assertThrows(InvalidStatusTransitionException.class, inDiagnosis()::ensureAwaitingApproval);
    }

    @Test
    @DisplayName("should transition to IN_EXECUTION when startExecution is called")
    void shouldTransitionToInExecutionWhenStartExecutionIsCalled() {
        ServiceOrder order = waitingApproval();
        order.startExecution();

        assertEquals(ServiceOrderStatus.IN_EXECUTION, order.getStatus());
        assertNotNull(order.getExecutionStartedAt());
    }

    @Test
    @DisplayName("should throw InvalidStatusTransitionException when startExecution is called and status is not WAITING_APPROVAL")
    void shouldThrowWhenStartExecutionCalledAndStatusIsNotWaitingApproval() {
        ServiceOrder order = inDiagnosis();
        assertThrows(InvalidStatusTransitionException.class, order::startExecution);
    }

    @Test
    @DisplayName("should transition to CANCELLED when cancel is called on an order waiting approval")
    void shouldTransitionToCancelledWhenCancelIsCalled() {
        ServiceOrder order = waitingApproval();
        order.cancel();

        assertEquals(ServiceOrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    @DisplayName("should throw InvalidStatusTransitionException when cancel is called and status is not WAITING_APPROVAL")
    void shouldThrowWhenCancelCalledAndStatusIsNotWaitingApproval() {
        assertThrows(InvalidStatusTransitionException.class, inExecution()::cancel);
        assertThrows(InvalidStatusTransitionException.class, inDiagnosis()::cancel);
    }

    @Test
    @DisplayName("should transition back to IN_DIAGNOSIS and clear waitingApprovalAt when rejectEstimate is called")
    void shouldTransitionBackToInDiagnosisAndClearWaitingApprovalAtWhenRejectEstimateIsCalled() {
        ServiceOrder order = waitingApproval();
        order.rejectEstimate();

        assertEquals(ServiceOrderStatus.IN_DIAGNOSIS, order.getStatus());
        assertNull(order.getWaitingApprovalAt());
    }

    @Test
    @DisplayName("should throw InvalidStatusTransitionException when rejectEstimate is called and status is not WAITING_APPROVAL")
    void shouldThrowWhenRejectEstimateCalledAndStatusIsNotWaitingApproval() {
        ServiceOrder order = inDiagnosis();
        assertThrows(InvalidStatusTransitionException.class, order::rejectEstimate);
    }

    @Test
    @DisplayName("should transition to FINISHED when finishExecution is called")
    void shouldTransitionToFinishedWhenFinishExecutionIsCalled() {
        ServiceOrder order = inExecution();
        order.finishExecution();

        assertEquals(ServiceOrderStatus.FINISHED, order.getStatus());
        assertNotNull(order.getExecutionFinishedAt());
    }

    @Test
    @DisplayName("should throw InvalidStatusTransitionException when finishExecution is called and status is not IN_EXECUTION")
    void shouldThrowWhenFinishExecutionCalledAndStatusIsNotInExecution() {
        ServiceOrder order = waitingApproval();
        assertThrows(InvalidStatusTransitionException.class, order::finishExecution);
    }

    @Test
    @DisplayName("should transition to DELIVERED when deliver is called")
    void shouldTransitionToDeliveredWhenDeliverIsCalled() {
        ServiceOrder order = inExecution();
        order.finishExecution();
        order.deliver();

        assertEquals(ServiceOrderStatus.DELIVERED, order.getStatus());
        assertNotNull(order.getDeliveredAt());
    }

    @Test
    @DisplayName("should throw InvalidStatusTransitionException when deliver is called and status is not FINISHED")
    void shouldThrowWhenDeliverCalledAndStatusIsNotFinished() {
        ServiceOrder order = inExecution();
        assertThrows(InvalidStatusTransitionException.class, order::deliver);
    }

    @Test
    @DisplayName("should add item to list when addItem is called and status is IN_DIAGNOSIS")
    void shouldAddItemToListWhenAddItemCalledAndStatusIsInDiagnosis() {
        ServiceOrder order = inDiagnosis();
        order.addItem(ServiceOrderItem.createServiceItem(order.getId(), catalogItem(100.00), 1));
        assertEquals(1, order.getItems().size());
    }

    @Test
    @DisplayName("should add item to list when addItem is called and status is RECEIVED")
    void shouldAddItemToListWhenAddItemCalledAndStatusIsReceived() {
        ServiceOrder order = newOrder();
        order.addItem(ServiceOrderItem.createServiceItem(order.getId(), catalogItem(100.00), 1));
        assertEquals(1, order.getItems().size());
    }

    @Test
    @DisplayName("should throw InvalidStatusTransitionException when addItem is called and status is not RECEIVED or IN_DIAGNOSIS")
    void shouldThrowWhenAddItemCalledAndStatusIsNotReceivedOrInDiagnosis() {
        ServiceOrder order = waitingApproval();
        ServiceOrderItem item = ServiceOrderItem.createServiceItem(order.getId(), catalogItem(100.00), 1);
        assertThrows(InvalidStatusTransitionException.class, () -> order.addItem(item));
    }

    @Test
    @DisplayName("should remove item from list when removeItem is called and status is IN_DIAGNOSIS")
    void shouldRemoveItemFromListWhenRemoveItemCalledAndStatusIsInDiagnosis() {
        ServiceOrder order = inDiagnosis();
        ServiceOrderItem item = ServiceOrderItem.createServiceItem(order.getId(), catalogItem(100.00), 1);
        order.addItem(item);
        order.removeItem(item.getId());
        assertTrue(order.getItems().isEmpty());
    }

    @Test
    @DisplayName("should throw InvalidStatusTransitionException when removeItem is called and status is not IN_DIAGNOSIS")
    void shouldThrowWhenRemoveItemCalledAndStatusIsNotInDiagnosis() {
        ServiceOrder order = newOrder();
        assertThrows(InvalidStatusTransitionException.class, () -> order.removeItem(UUID.randomUUID()));
    }

    @Test
    @DisplayName("should return unmodifiable list when getItems is called")
    void shouldReturnUnmodifiableListWhenGetItemsIsCalled() {
        ServiceOrder order = inDiagnosis();
        assertThrows(UnsupportedOperationException.class, () -> order.getItems().add(null));
    }
}
