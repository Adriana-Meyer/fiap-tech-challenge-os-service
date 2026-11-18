package com.fiap.workshop.management.application.usecase.serviceorder;

import com.fiap.workshop.management.application.dto.customer.CreateCustomerCommand;
import com.fiap.workshop.management.application.dto.serviceorder.AddItemCommand;
import com.fiap.workshop.management.application.dto.serviceorder.OpenFullServiceOrderCommand;
import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.dto.serviceorder.VehicleIntakeCommand;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.gateway.SupplyCatalog;
import com.fiap.workshop.management.domain.model.catalog.ServiceCatalogItem;
import com.fiap.workshop.management.domain.model.catalog.ServiceType;
import com.fiap.workshop.management.domain.model.customer.Customer;
import com.fiap.workshop.management.domain.model.customer.Document;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.model.shared.Money;
import com.fiap.workshop.management.domain.model.supply.SupplySnapshot;
import com.fiap.workshop.management.domain.model.vehicle.LicensePlate;
import com.fiap.workshop.management.domain.model.vehicle.Vehicle;
import com.fiap.workshop.management.domain.repository.CustomerRepository;
import com.fiap.workshop.management.domain.repository.ServiceCatalogRepository;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import com.fiap.workshop.management.domain.repository.VehicleRepository;
import com.fiap.workshop.management.domain.service.ServiceOrderDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpenFullServiceOrderUseCase")
class OpenFullServiceOrderUseCaseTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private ServiceOrderRepository serviceOrderRepository;
    @Mock
    private ServiceCatalogRepository serviceCatalogRepository;
    @Mock
    private SupplyCatalog supplyCatalog;

    private OpenFullServiceOrderUseCase useCase;

    private final CreateCustomerCommand customerData =
            new CreateCustomerCommand("Carlos Lima", "52998224725", "11977776666", "carlos@test.com");
    private final VehicleIntakeCommand vehicleData =
            new VehicleIntakeCommand("QWE4455", "Fiat", "Uno", 2018, "White");

    @BeforeEach
    void setUp() {
        useCase = new OpenFullServiceOrderUseCase(customerRepository, vehicleRepository, serviceOrderRepository,
                serviceCatalogRepository, supplyCatalog, new ServiceOrderDomainService());
        lenient().when(serviceOrderRepository.countByYear(anyInt())).thenReturn(0L);
        lenient().when(serviceOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("should create new customer and vehicle when neither exists")
    void shouldCreateNewCustomerAndVehicleWhenNeitherExists() {
        when(customerRepository.findByDocument("52998224725")).thenReturn(Optional.empty());
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleRepository.findByPlate("QWE4455")).thenReturn(Optional.empty());
        when(vehicleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceOrderResponse response = useCase.execute(
                new OpenFullServiceOrderCommand(customerData, vehicleData, "Retrovisor quebrado", null));

        assertNotNull(response);
        verify(customerRepository).save(argThat(c -> c.getName().equals("Carlos Lima")));
        verify(vehicleRepository).save(argThat(v -> v.getBrand().equals("Fiat")));
    }

    @Test
    @DisplayName("should reuse existing customer by document and update other fields")
    void shouldReuseExistingCustomerByDocumentAndUpdateOtherFields() {
        Customer existing = new Customer(UUID.randomUUID(), "Old Name", new Document("52998224725"),
                "11900000000", "old@test.com", null);
        when(customerRepository.findByDocument("52998224725")).thenReturn(Optional.of(existing));
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleRepository.findByPlate("QWE4455")).thenReturn(Optional.empty());
        when(vehicleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(new OpenFullServiceOrderCommand(customerData, vehicleData, null, null));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        Customer saved = captor.getValue();
        assertEquals("Carlos Lima", saved.getName());
        assertEquals("carlos@test.com", saved.getEmail());
        assertEquals("52998224725", saved.getDocument().getValue());
    }

    @Test
    @DisplayName("should reuse existing vehicle by plate and update other fields, keeping customer association")
    void shouldReuseExistingVehicleByPlateAndUpdateOtherFields() {
        UUID customerId = UUID.randomUUID();
        Customer existingCustomer = new Customer(customerId, "Carlos Lima", new Document("52998224725"),
                "11977776666", "carlos@test.com", null);
        Vehicle existingVehicle = new Vehicle(UUID.randomUUID(), new LicensePlate("QWE4455"),
                "Old Brand", "Old Model", 2000, "Black", customerId);
        when(customerRepository.findByDocument("52998224725")).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleRepository.findByPlate("QWE4455")).thenReturn(Optional.of(existingVehicle));
        when(vehicleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(new OpenFullServiceOrderCommand(customerData, vehicleData, null, null));

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        Vehicle saved = captor.getValue();
        assertEquals("Fiat", saved.getBrand());
        assertEquals("Uno", saved.getModel());
        assertEquals("QWE-4455", saved.getPlate().formatted());
    }

    @Test
    @DisplayName("should attach initial items resolved from the service catalog and supply repositories")
    void shouldAttachInitialItemsResolvedFromCatalogAndSupply() {
        when(customerRepository.findByDocument(anyString())).thenReturn(Optional.empty());
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleRepository.findByPlate(anyString())).thenReturn(Optional.empty());
        when(vehicleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UUID catalogItemId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        ServiceCatalogItem catalogItem = new ServiceCatalogItem(catalogItemId, "Mirror replacement", "desc",
                ServiceType.BODYWORK, Money.of(120.00), true);
        SupplySnapshot supply = new SupplySnapshot(supplyId, "Side mirror", Money.of(80.00));
        when(serviceCatalogRepository.findById(catalogItemId)).thenReturn(Optional.of(catalogItem));
        when(supplyCatalog.findById(supplyId)).thenReturn(Optional.of(supply));

        List<AddItemCommand> items = List.of(
                new AddItemCommand(catalogItemId, null, 1),
                new AddItemCommand(null, supplyId, 1));

        ArgumentCaptor<ServiceOrder> captor = ArgumentCaptor.forClass(ServiceOrder.class);
        useCase.execute(new OpenFullServiceOrderCommand(customerData, vehicleData, "Retrovisor quebrado", items));

        verify(serviceOrderRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getItems().size());
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when an item references an unknown catalog item")
    void shouldThrowResourceNotFoundExceptionWhenItemReferencesUnknownCatalogItem() {
        when(customerRepository.findByDocument(anyString())).thenReturn(Optional.empty());
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleRepository.findByPlate(anyString())).thenReturn(Optional.empty());
        when(vehicleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UUID unknownId = UUID.randomUUID();
        when(serviceCatalogRepository.findById(unknownId)).thenReturn(Optional.empty());
        List<AddItemCommand> items = List.of(new AddItemCommand(unknownId, null, 1));

        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(
                new OpenFullServiceOrderCommand(customerData, vehicleData, null, items)));
        verify(serviceOrderRepository, never()).save(any());
    }
}
