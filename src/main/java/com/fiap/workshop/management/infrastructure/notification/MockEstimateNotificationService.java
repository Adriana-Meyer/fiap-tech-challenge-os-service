package com.fiap.workshop.management.infrastructure.notification;

import com.fiap.workshop.management.domain.model.customer.Customer;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrderItem;
import com.fiap.workshop.management.domain.service.EstimateNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MockEstimateNotificationService implements EstimateNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MockEstimateNotificationService.class);

    @Override
    public void notifyEstimateReady(ServiceOrder order, Customer customer) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========================================");
        sb.append("\n[MOCK NOTIFICATION] Estimate ready");
        sb.append("\n----------------------------------------");
        sb.append("\nOS Code  : ").append(order.getOsCode());
        sb.append("\nCustomer : ").append(customer.getName());
        sb.append("\nContact  : ").append(customer.getEmail());
        sb.append("\n----------------------------------------");
        sb.append("\nItems:");

        for (ServiceOrderItem item : order.getItems()) {
            String itemName = item.getDescription();
            String itemType = item.isService() ? "Service" : "Supply";
            sb.append(String.format("\n  [%s] %s — qty: %d x R$ %.2f = R$ %.2f",
                    itemType,
                    itemName,
                    item.getQuantity(),
                    item.getUnitPrice().getAmount().doubleValue(),
                    item.getSubtotal().getAmount().doubleValue()));
        }

        sb.append("\n----------------------------------------");
        sb.append(String.format("\nTotal Amount : R$ %.2f", order.getTotalAmount().getAmount().doubleValue()));
        sb.append("\n========================================");

        log.info(sb.toString());
    }
}
