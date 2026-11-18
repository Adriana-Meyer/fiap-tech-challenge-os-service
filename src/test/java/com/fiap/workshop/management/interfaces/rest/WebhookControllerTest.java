package com.fiap.workshop.management.interfaces.rest;

import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.port.in.serviceorder.ProcessEmailStatusUpdateInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.ProcessEstimateApprovalWebhookInputPort;
import com.fiap.workshop.management.domain.exception.InvalidWebhookTokenException;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.infrastructure.security.JwtAuthenticationFilter;
import com.fiap.workshop.management.application.port.in.webhook.AuthorizeWebhookInputPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthorizeWebhookInputPort authorizeWebhook;

    @MockBean
    private ProcessEstimateApprovalWebhookInputPort estimateApprovalUseCase;

    @MockBean
    private ProcessEmailStatusUpdateInputPort emailStatusUpdateUseCase;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private ServiceOrderResponse sampleResponse() {
        return new ServiceOrderResponse(UUID.randomUUID(), "OS-2026-00001", "IN_EXECUTION",
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, null, null, List.of(),
                null, null, null, null, null, null, null, null);
    }

    @Test
    void shouldApproveEstimateWithValidToken() throws Exception {
        when(estimateApprovalUseCase.execute(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/webhooks/estimate-approval")
                        .header("X-Webhook-Token", "valid-token")
                        .contentType("application/json")
                        .content("{\"osCode\":\"OS-2026-00001\",\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn401WhenWebhookTokenIsInvalid() throws Exception {
        doThrow(new InvalidWebhookTokenException("Invalid or missing webhook token"))
                .when(authorizeWebhook).authorize("wrong-token");

        mockMvc.perform(post("/api/v1/webhooks/estimate-approval")
                        .header("X-Webhook-Token", "wrong-token")
                        .contentType("application/json")
                        .content("{\"osCode\":\"OS-2026-00001\",\"decision\":\"APPROVED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenWebhookTokenHeaderIsMissing() throws Exception {
        doThrow(new InvalidWebhookTokenException("Invalid or missing webhook token"))
                .when(authorizeWebhook).authorize(null);

        mockMvc.perform(post("/api/v1/webhooks/estimate-approval")
                        .contentType("application/json")
                        .content("{\"osCode\":\"OS-2026-00001\",\"decision\":\"APPROVED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn404WhenOsCodeIsUnknown() throws Exception {
        when(estimateApprovalUseCase.execute(any()))
                .thenThrow(new ResourceNotFoundException("ServiceOrder", "UNKNOWN"));

        mockMvc.perform(post("/api/v1/webhooks/estimate-approval")
                        .header("X-Webhook-Token", "valid-token")
                        .contentType("application/json")
                        .content("{\"osCode\":\"UNKNOWN\",\"decision\":\"APPROVED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400WhenDecisionIsMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/estimate-approval")
                        .header("X-Webhook-Token", "valid-token")
                        .contentType("application/json")
                        .content("{\"osCode\":\"OS-2026-00001\",\"decision\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldProcessEmailStatusUpdateWithValidToken() throws Exception {
        when(emailStatusUpdateUseCase.execute(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/webhooks/email-status-update")
                        .header("X-Webhook-Token", "valid-token")
                        .contentType("application/json")
                        .content("{\"osCode\":\"OS-2026-00001\",\"subject\":\"DELIVER\"}"))
                .andExpect(status().isOk());
    }
}
