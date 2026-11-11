package com.fiap.workshop.management.domain.model.serviceorder;

import com.fiap.workshop.management.domain.model.catalog.ServiceCatalogItem;
import com.fiap.workshop.management.domain.model.shared.Money;
import com.fiap.workshop.management.domain.model.supply.SupplySnapshot;

import java.util.UUID;

/** A service from the catalog, or a supply (snapshot of what Inventory returned). */
public class ServiceOrderItem {

    private UUID id;
    private UUID serviceOrderId;
    private ServiceCatalogItem service;
    private UUID supplyId;
    private String supplyName;
    private int quantity;
    private Money unitPrice;
    private Money subtotal;

    public ServiceOrderItem(UUID id, UUID serviceOrderId, ServiceCatalogItem service, UUID supplyId,
                            String supplyName, int quantity, Money unitPrice, Money subtotal) {
        this.id = id;
        this.serviceOrderId = serviceOrderId;
        this.service = service;
        this.supplyId = supplyId;
        this.supplyName = supplyName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    public static ServiceOrderItem createServiceItem(UUID serviceOrderId, ServiceCatalogItem service, int quantity) {
        Money unitPrice = service.getBasePrice();
        return new ServiceOrderItem(UUID.randomUUID(), serviceOrderId, service, null, null,
                quantity, unitPrice, unitPrice.multiply(quantity));
    }

    public static ServiceOrderItem createSupplyItem(UUID serviceOrderId, SupplySnapshot supply, int quantity) {
        return new ServiceOrderItem(UUID.randomUUID(), serviceOrderId, null, supply.id(), supply.name(),
                quantity, supply.unitPrice(), supply.unitPrice().multiply(quantity));
    }

    public boolean isService() {
        return service != null;
    }

    public boolean isSupply() {
        return supplyId != null;
    }

    /** Name shown to the customer and sent to Billing, whichever the kind of item. */
    public String getDescription() {
        return isService() ? service.getName() : supplyName;
    }

    public UUID getId() { return id; }
    public UUID getServiceOrderId() { return serviceOrderId; }
    public ServiceCatalogItem getService() { return service; }
    public UUID getSupplyId() { return supplyId; }
    public String getSupplyName() { return supplyName; }
    public int getQuantity() { return quantity; }
    public Money getUnitPrice() { return unitPrice; }
    public Money getSubtotal() { return subtotal; }
}
