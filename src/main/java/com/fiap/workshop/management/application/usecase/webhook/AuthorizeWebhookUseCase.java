package com.fiap.workshop.management.application.usecase.webhook;

import com.fiap.workshop.management.application.port.in.webhook.AuthorizeWebhookInputPort;
import com.fiap.workshop.management.application.port.out.WebhookTokenVerifier;
import org.springframework.stereotype.Service;

@Service
public class AuthorizeWebhookUseCase implements AuthorizeWebhookInputPort {

    private final WebhookTokenVerifier verifier;

    public AuthorizeWebhookUseCase(WebhookTokenVerifier verifier) {
        this.verifier = verifier;
    }

    public void authorize(String token) {
        verifier.verify(token);
    }
}
