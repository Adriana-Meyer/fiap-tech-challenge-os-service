package com.fiap.workshop.management.application.port.in.webhook;

public interface AuthorizeWebhookInputPort {

    /** @throws com.fiap.workshop.management.domain.exception.InvalidWebhookTokenException when the token is missing or wrong */
    void authorize(String token);
}
