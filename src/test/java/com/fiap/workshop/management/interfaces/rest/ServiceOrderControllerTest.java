package com.fiap.workshop.management.interfaces.rest;

import com.fiap.workshop.management.application.port.in.saga.FindSagaInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.AddItemToServiceOrderInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.ApproveEstimateInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.CompleteDiagnosisInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.CreateServiceOrderInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.DeliverServiceOrderInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.FindServiceOrderInputPort;
import com.fiap.workshop.management.application.dto.customer.CreateCustomerCommand;
import com.fiap.workshop.management.application.dto.saga.SagaResponse;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.exception.SupplyCatalogUnavailableException;
import com.fiap.workshop.management.application.dto.serviceorder.OpenFullServiceOrderCommand;
import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.dto.serviceorder.VehicleIntakeCommand;
import com.fiap.workshop.management.application.port.in.serviceorder.OpenFullServiceOrderInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.RejectEstimateInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.RemoveItemFromServiceOrderInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.StartDiagnosisInputPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.workshop.management.infrastructure.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ServiceOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class ServiceOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreateServiceOrderInputPort createUseCase;
    @MockBean
    private FindServiceOrderInputPort findUseCase;
    @MockBean
    private StartDiagnosisInputPort startDiagnosisUseCase;
    @MockBean
    private AddItemToServiceOrderInputPort addItemUseCase;
    @MockBean
    private RemoveItemFromServiceOrderInputPort removeItemUseCase;
    @MockBean
    private CompleteDiagnosisInputPort completeDiagnosisUseCase;
    @MockBean
    private ApproveEstimateInputPort approveEstimateUseCase;
    @MockBean
    private RejectEstimateInputPort rejectEstimateUseCase;
    @MockBean
    private DeliverServiceOrderInputPort deliverUseCase;
    @MockBean
    private OpenFullServiceOrderInputPort openFullUseCase;
    @MockBean
    private FindSagaInputPort findSagaUseCase;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void shouldListExcludingCompletedByDefault() throws Exception {
        when(findUseCase.findAll(false)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/service-orders"))
                .andExpect(status().isOk());

        verify(findUseCase).findAll(false);
    }

    @Test
    void shouldListIncludingCompletedWhenRequested() throws Exception {
        when(findUseCase.findAll(true)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/service-orders").param("includeCompleted", "true"))
                .andExpect(status().isOk());

        verify(findUseCase).findAll(true);
    }

    @Test
    void shouldOpenFullServiceOrder() throws Exception {
        ServiceOrderResponse response = new ServiceOrderResponse(UUID.randomUUID(), "OS-2026-00001", "RECEIVED",
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, null, null, List.of(),
                null, null, null, null, null, null, null, null);
        when(openFullUseCase.execute(any())).thenReturn(response);

        OpenFullServiceOrderCommand command = new OpenFullServiceOrderCommand(
                new CreateCustomerCommand("Carlos Lima", "52998224725", "11977776666", "carlos@test.com"),
                new VehicleIntakeCommand("QWE4455", "Fiat", "Uno", 2018, "White"),
                "Retrovisor quebrado",
                null);

        mockMvc.perform(post("/api/v1/service-orders/full")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isCreated());
    }

    private ServiceOrderResponse sampleResponse(String status) {
        return new ServiceOrderResponse(UUID.randomUUID(), "OS-2026-00001", status,
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, null, null, List.of(),
                null, null, null, null, null, null, null, null);
    }

    @Test
    void shouldApproveWithTheChosenPaymentMethod() throws Exception {
        UUID id = UUID.randomUUID();
        when(approveEstimateUseCase.execute(id, "pix")).thenReturn(sampleResponse("WAITING_APPROVAL"));

        mockMvc.perform(patch("/api/v1/service-orders/{id}/estimate/approve", id)
                        .contentType("application/json")
                        .content("{\"paymentMethod\":\"pix\"}"))
                .andExpect(status().isOk());

        verify(approveEstimateUseCase).execute(id, "pix");
    }

    @Test
    void shouldApproveWithoutABodyUsingTheDefaultPaymentMethod() throws Exception {
        UUID id = UUID.randomUUID();
        when(approveEstimateUseCase.execute(id, null)).thenReturn(sampleResponse("WAITING_APPROVAL"));

        mockMvc.perform(patch("/api/v1/service-orders/{id}/estimate/approve", id))
                .andExpect(status().isOk());

        verify(approveEstimateUseCase).execute(id, null);
    }

    @Test
    void shouldReturnTheSagaOfAnOrder() throws Exception {
        UUID id = UUID.randomUUID();
        when(findSagaUseCase.findLatestByServiceOrderId(id)).thenReturn(new SagaResponse(UUID.randomUUID(), id,
                "PROCESS_PAYMENT", "RUNNING", "pix", List.of(), null, LocalDateTime.now(), LocalDateTime.now()));

        mockMvc.perform(get("/api/v1/service-orders/{id}/saga", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("PROCESS_PAYMENT"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void shouldAnswer404WhenTheOrderHasNoSaga() throws Exception {
        UUID id = UUID.randomUUID();
        when(findSagaUseCase.findLatestByServiceOrderId(id)).thenThrow(new ResourceNotFoundException("Saga of service order", id));

        mockMvc.perform(get("/api/v1/service-orders/{id}/saga", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAnswer503WhenInventoryIsUnavailableWhileAddingAnItem() throws Exception {
        UUID id = UUID.randomUUID();
        when(addItemUseCase.execute(eq(id), any()))
                .thenThrow(new SupplyCatalogUnavailableException("Inventory is down"));

        mockMvc.perform(post("/api/v1/service-orders/{id}/items", id)
                        .contentType("application/json")
                        .content("{\"supplyId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Inventory is down"));
    }
}
