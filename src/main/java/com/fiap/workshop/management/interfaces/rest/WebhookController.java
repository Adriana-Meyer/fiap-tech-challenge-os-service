package com.fiap.workshop.management.interfaces.rest;

import com.fiap.workshop.management.application.dto.serviceorder.EmailStatusUpdateCommand;
import com.fiap.workshop.management.application.dto.serviceorder.EstimateApprovalWebhookCommand;
import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.port.in.serviceorder.ProcessEmailStatusUpdateInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.ProcessEstimateApprovalWebhookInputPort;
import com.fiap.workshop.management.application.port.in.webhook.AuthorizeWebhookInputPort;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final AuthorizeWebhookInputPort authorizeWebhook;
    private final ProcessEstimateApprovalWebhookInputPort estimateApprovalUseCase;
    private final ProcessEmailStatusUpdateInputPort emailStatusUpdateUseCase;

    public WebhookController(AuthorizeWebhookInputPort authorizeWebhook,
                              ProcessEstimateApprovalWebhookInputPort estimateApprovalUseCase,
                              ProcessEmailStatusUpdateInputPort emailStatusUpdateUseCase) {
        this.authorizeWebhook = authorizeWebhook;
        this.estimateApprovalUseCase = estimateApprovalUseCase;
        this.emailStatusUpdateUseCase = emailStatusUpdateUseCase;
    }

    @PostMapping("/estimate-approval")
    public ServiceOrderResponse estimateApproval(
            @RequestHeader(value = "X-Webhook-Token", required = false) String token,
            @Valid @RequestBody EstimateApprovalWebhookCommand command) {
        authorizeWebhook.authorize(token);
        return estimateApprovalUseCase.execute(command);
    }

    @PostMapping("/email-status-update")
    public ServiceOrderResponse emailStatusUpdate(
            @RequestHeader(value = "X-Webhook-Token", required = false) String token,
            @Valid @RequestBody EmailStatusUpdateCommand command) {
        authorizeWebhook.authorize(token);
        return emailStatusUpdateUseCase.execute(command);
    }
}
