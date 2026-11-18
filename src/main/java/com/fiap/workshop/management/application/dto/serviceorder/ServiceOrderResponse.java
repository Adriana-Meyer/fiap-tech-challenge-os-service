package com.fiap.workshop.management.application.dto.serviceorder;

import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ServiceOrderResponse(
        UUID id,
        String osCode,
        String status,
        UUID customerId,
        UUID vehicleId,
        BigDecimal totalAmount,
        String diagnosisNotes,
        String customerComments,
        List<ServiceOrderItemResponse> items,
        LocalDateTime receivedAt,
        LocalDateTime diagnosisStartedAt,
        LocalDateTime waitingApprovalAt,
        LocalDateTime executionStartedAt,
        LocalDateTime executionFinishedAt,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record ServiceOrderItemResponse(
            UUID id,
            UUID serviceCatalogItemId,
            String serviceName,
            UUID supplyId,
            String supplyName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
        public static ServiceOrderItemResponse from(ServiceOrderItem item) {
            return new ServiceOrderItemResponse(
                    item.getId(),
                    item.getService() != null ? item.getService().getId() : null,
                    item.getService() != null ? item.getService().getName() : null,
                    item.getSupplyId(),
                    item.getSupplyName(),
                    item.getQuantity(),
                    item.getUnitPrice().getAmount(),
                    item.getSubtotal().getAmount()
            );
        }
    }

    public static ServiceOrderResponse from(ServiceOrder order) {
        return new ServiceOrderResponse(
                order.getId(),
                order.getOsCode(),
                order.getStatus().name(),
                order.getCustomerId(),
                order.getVehicleId(),
                order.getTotalAmount().getAmount(),
                order.getDiagnosisNotes(),
                order.getCustomerComments(),
                order.getItems().stream().map(ServiceOrderItemResponse::from).toList(),
                order.getReceivedAt(),
                order.getDiagnosisStartedAt(),
                order.getWaitingApprovalAt(),
                order.getExecutionStartedAt(),
                order.getExecutionFinishedAt(),
                order.getDeliveredAt(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
