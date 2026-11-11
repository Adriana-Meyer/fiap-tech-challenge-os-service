package com.fiap.workshop.management.application.usecase.webhook;

import com.fiap.workshop.management.application.port.out.WebhookTokenVerifier;
import com.fiap.workshop.management.domain.exception.InvalidWebhookTokenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class AuthorizeWebhookUseCaseTest {

    private final WebhookTokenVerifier verifier = mock(WebhookTokenVerifier.class);
    private final AuthorizeWebhookUseCase useCase = new AuthorizeWebhookUseCase(verifier);

    @Test
    void shouldAcceptATokenTheVerifierAccepts() {
        assertDoesNotThrow(() -> useCase.authorize("good"));
    }

    @Test
    void shouldRejectATokenTheVerifierRejects() {
        doThrow(new InvalidWebhookTokenException("bad")).when(verifier).verify("bad");

        assertThrows(InvalidWebhookTokenException.class, () -> useCase.authorize("bad"));
    }
}
