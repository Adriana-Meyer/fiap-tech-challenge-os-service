package com.fiap.workshop.management.application.usecase.serviceorder;

import com.fiap.workshop.management.application.dto.serviceorder.AddItemCommand;
import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.exception.SupplyCatalogUnavailableException;
import com.fiap.workshop.management.domain.gateway.SupplyCatalog;
import com.fiap.workshop.management.domain.model.catalog.ServiceCatalogItem;
import com.fiap.workshop.management.domain.model.catalog.ServiceType;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.model.shared.Money;
import com.fiap.workshop.management.domain.model.supply.SupplySnapshot;
import com.fiap.workshop.management.domain.repository.ServiceCatalogRepository;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddItemToServiceOrderUseCase")
class AddItemToServiceOrderUseCaseTest {

    @Mock
    private ServiceOrderRepository serviceOrderRepository;
    @Mock
    private ServiceCatalogRepository serviceCatalogRepository;
    @Mock
    private SupplyCatalog supplyCatalog;

    private AddItemToServiceOrderUseCase useCase;
    private ServiceOrder order;

    @BeforeEach
    void setUp() {
        useCase = new AddItemToServiceOrderUseCase(serviceOrderRepository, serviceCatalogRepository, supplyCatalog);
        order = ServiceOrder.create("OS-2026-00001", UUID.randomUUID(), UUID.randomUUID(), null);
        order.startDiagnosis();
        org.mockito.Mockito.lenient().when(serviceOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        org.mockito.Mockito.lenient().when(serviceOrderRepository.save(any(ServiceOrder.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("should add a supply as a snapshot taken from Inventory")
    void shouldAddASupplyAsASnapshot() {
        UUID supplyId = UUID.randomUUID();
        when(supplyCatalog.findById(supplyId))
                .thenReturn(Optional.of(new SupplySnapshot(supplyId, "Oil Filter", Money.of(25.00))));

        ServiceOrderResponse response = useCase.execute(order.getId(), new AddItemCommand(null, supplyId, 2));

        ServiceOrderResponse.ServiceOrderItemResponse item = response.items().get(0);
        assertEquals(supplyId, item.supplyId());
        assertEquals("Oil Filter", item.supplyName());
        assertEquals(0, item.subtotal().compareTo(java.math.BigDecimal.valueOf(50)));
    }

    @Test
    @DisplayName("should add a service from the catalog")
    void shouldAddAServiceFromTheCatalog() {
        UUID serviceId = UUID.randomUUID();
        when(serviceCatalogRepository.findById(serviceId)).thenReturn(Optional.of(new ServiceCatalogItem(
                serviceId, "Oil Change", "d", ServiceType.MECHANICAL, Money.of(150), true)));

        ServiceOrderResponse response = useCase.execute(order.getId(), new AddItemCommand(serviceId, null, 1));

        assertEquals("Oil Change", response.items().get(0).serviceName());
    }

    @Test
    @DisplayName("should fail with not found when Inventory does not know the supply")
    void shouldFailWithNotFoundWhenInventoryDoesNotKnowTheSupply() {
        UUID supplyId = UUID.randomUUID();
        when(supplyCatalog.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> useCase.execute(order.getId(), new AddItemCommand(null, supplyId, 1)));
        verify(serviceOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("should let the unavailability of Inventory propagate and save nothing")
    void shouldLetTheUnavailabilityOfInventoryPropagate() {
        UUID supplyId = UUID.randomUUID();
        when(supplyCatalog.findById(supplyId)).thenThrow(new SupplyCatalogUnavailableException("down"));

        assertThrows(SupplyCatalogUnavailableException.class,
                () -> useCase.execute(order.getId(), new AddItemCommand(null, supplyId, 1)));
        verify(serviceOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("should require a service or a supply")
    void shouldRequireAServiceOrASupply() {
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(order.getId(), new AddItemCommand(null, null, 1)));
    }
}
