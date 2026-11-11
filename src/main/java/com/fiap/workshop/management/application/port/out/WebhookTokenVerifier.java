package com.fiap.workshop.management.application.port.out;

public interface WebhookTokenVerifier {

    /** @throws com.fiap.workshop.management.domain.exception.InvalidWebhookTokenException when the token is missing or wrong */
    void verify(String token);
}
