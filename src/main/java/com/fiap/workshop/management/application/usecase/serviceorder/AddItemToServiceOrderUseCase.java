package com.fiap.workshop.management.application.usecase.serviceorder;

import com.fiap.workshop.management.application.dto.serviceorder.AddItemCommand;
import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.port.in.serviceorder.AddItemToServiceOrderInputPort;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrderItem;
import com.fiap.workshop.management.domain.repository.ServiceCatalogRepository;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import com.fiap.workshop.management.domain.gateway.SupplyCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AddItemToServiceOrderUseCase implements AddItemToServiceOrderInputPort {

    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceCatalogRepository serviceCatalogRepository;
    private final SupplyCatalog supplyCatalog;

    public AddItemToServiceOrderUseCase(ServiceOrderRepository serviceOrderRepository,
                                         ServiceCatalogRepository serviceCatalogRepository,
                                         SupplyCatalog supplyCatalog) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.supplyCatalog = supplyCatalog;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, AddItemCommand command) {
        if (command.serviceCatalogItemId() == null && command.supplyId() == null) {
            throw new IllegalArgumentException("Either serviceCatalogItemId or supplyId must be provided");
        }
        ServiceOrder order = serviceOrderRepository.findById(serviceOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrder", serviceOrderId));

        ServiceOrderItem item;
        if (command.serviceCatalogItemId() != null) {
            var catalogItem = serviceCatalogRepository.findById(command.serviceCatalogItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("ServiceCatalogItem", command.serviceCatalogItemId()));
            item = ServiceOrderItem.createServiceItem(serviceOrderId, catalogItem, command.quantity());
        } else {
            var supply = supplyCatalog.findById(command.supplyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supply", command.supplyId()));
            item = ServiceOrderItem.createSupplyItem(serviceOrderId, supply, command.quantity());
        }

        order.addItem(item);
        return ServiceOrderResponse.from(serviceOrderRepository.save(order));
    }
}
