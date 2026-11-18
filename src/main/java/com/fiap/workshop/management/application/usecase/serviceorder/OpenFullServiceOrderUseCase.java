package com.fiap.workshop.management.application.usecase.serviceorder;

import com.fiap.workshop.management.application.dto.customer.CreateCustomerCommand;
import com.fiap.workshop.management.application.dto.serviceorder.AddItemCommand;
import com.fiap.workshop.management.application.dto.serviceorder.OpenFullServiceOrderCommand;
import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.dto.serviceorder.VehicleIntakeCommand;
import com.fiap.workshop.management.application.port.in.serviceorder.OpenFullServiceOrderInputPort;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.model.catalog.ServiceCatalogItem;
import com.fiap.workshop.management.domain.model.customer.Customer;
import com.fiap.workshop.management.domain.model.customer.Document;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrderItem;
import com.fiap.workshop.management.domain.model.supply.SupplySnapshot;
import com.fiap.workshop.management.domain.model.vehicle.LicensePlate;
import com.fiap.workshop.management.domain.model.vehicle.Vehicle;
import com.fiap.workshop.management.domain.repository.CustomerRepository;
import com.fiap.workshop.management.domain.repository.ServiceCatalogRepository;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import com.fiap.workshop.management.domain.gateway.SupplyCatalog;
import com.fiap.workshop.management.domain.repository.VehicleRepository;
import com.fiap.workshop.management.domain.service.ServiceOrderDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OpenFullServiceOrderUseCase implements OpenFullServiceOrderInputPort {

    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceCatalogRepository serviceCatalogRepository;
    private final SupplyCatalog supplyCatalog;
    private final ServiceOrderDomainService serviceOrderDomainService;

    public OpenFullServiceOrderUseCase(CustomerRepository customerRepository,
                                        VehicleRepository vehicleRepository,
                                        ServiceOrderRepository serviceOrderRepository,
                                        ServiceCatalogRepository serviceCatalogRepository,
                                        SupplyCatalog supplyCatalog,
                                        ServiceOrderDomainService serviceOrderDomainService) {
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.supplyCatalog = supplyCatalog;
        this.serviceOrderDomainService = serviceOrderDomainService;
    }

    @Transactional
    public ServiceOrderResponse execute(OpenFullServiceOrderCommand command) {
        Customer customer = resolveCustomer(command.customer());
        Vehicle vehicle = resolveVehicle(command.vehicle(), customer.getId());

        int year = LocalDateTime.now().getYear();
        long sequence = serviceOrderRepository.countByYear(year) + 1;
        String osCode = serviceOrderDomainService.generateOsCode(year, sequence);
        ServiceOrder order = ServiceOrder.create(osCode, customer.getId(), vehicle.getId(), command.customerComments());

        if (command.items() != null) {
            for (AddItemCommand itemCommand : command.items()) {
                order.addItem(buildItem(order.getId(), itemCommand));
            }
        }

        return ServiceOrderResponse.from(serviceOrderRepository.save(order));
    }

    private Customer resolveCustomer(CreateCustomerCommand data) {
        Document document = new Document(data.documentNumber());
        return customerRepository.findByDocument(document.getValue())
                .map(existing -> {
                    existing.update(data.name(), data.phone(), data.email());
                    return customerRepository.save(existing);
                })
                .orElseGet(() -> customerRepository.save(Customer.create(data.name(), document, data.phone(), data.email())));
    }

    private Vehicle resolveVehicle(VehicleIntakeCommand data, UUID customerId) {
        LicensePlate plate = new LicensePlate(data.plate());
        return vehicleRepository.findByPlate(plate.getValue())
                .map(existing -> {
                    existing.update(data.brand(), data.model(), data.year(), data.color());
                    return vehicleRepository.save(existing);
                })
                .orElseGet(() -> vehicleRepository.save(
                        Vehicle.create(plate, data.brand(), data.model(), data.year(), data.color(), customerId)));
    }

    private ServiceOrderItem buildItem(UUID orderId, AddItemCommand itemCommand) {
        if (itemCommand.serviceCatalogItemId() != null) {
            ServiceCatalogItem catalogItem = serviceCatalogRepository.findById(itemCommand.serviceCatalogItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("ServiceCatalogItem", itemCommand.serviceCatalogItemId()));
            return ServiceOrderItem.createServiceItem(orderId, catalogItem, itemCommand.quantity());
        }
        if (itemCommand.supplyId() != null) {
            SupplySnapshot supply = supplyCatalog.findById(itemCommand.supplyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supply", itemCommand.supplyId()));
            return ServiceOrderItem.createSupplyItem(orderId, supply, itemCommand.quantity());
        }
        throw new IllegalArgumentException("Either serviceCatalogItemId or supplyId must be provided");
    }
}
