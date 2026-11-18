package com.fiap.workshop.management.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "service_order_items")
public class ServiceOrderItemJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "CHAR(36)", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_order_id", nullable = false)
    private ServiceOrderJpaEntity serviceOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_catalog_item_id")
    private ServiceCatalogItemJpaEntity serviceCatalogItem;

    @Column(name = "supply_id", columnDefinition = "CHAR(36)")
    private String supplyId;

    @Column(name = "supply_name")
    private String supplyName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    protected ServiceOrderItemJpaEntity() {}

    public ServiceOrderItemJpaEntity(String id, ServiceOrderJpaEntity serviceOrder,
                                      ServiceCatalogItemJpaEntity serviceCatalogItem,
                                      String supplyId, String supplyName, int quantity,
                                      BigDecimal unitPrice, BigDecimal subtotal) {
        this.id = id;
        this.serviceOrder = serviceOrder;
        this.serviceCatalogItem = serviceCatalogItem;
        this.supplyId = supplyId;
        this.supplyName = supplyName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    public String getId() { return id; }
    public ServiceOrderJpaEntity getServiceOrder() { return serviceOrder; }
    public ServiceCatalogItemJpaEntity getServiceCatalogItem() { return serviceCatalogItem; }
    public String getSupplyId() { return supplyId; }
    public String getSupplyName() { return supplyName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getSubtotal() { return subtotal; }
}
