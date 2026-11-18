package com.fiap.workshop.management.application.usecase.auth;

import com.fiap.workshop.management.application.dto.auth.AuthRequest;
import com.fiap.workshop.management.application.dto.auth.AuthResponse;
import com.fiap.workshop.management.application.port.out.UserAuthenticator;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginUseCaseTest {

    private final UserAuthenticator authenticator = mock(UserAuthenticator.class);
    private final LoginUseCase useCase = new LoginUseCase(authenticator);

    @Test
    void shouldReturnTheTokenIssuedForValidCredentials() {
        when(authenticator.authenticate("52998224725", "pass")).thenReturn("jwt-token");

        AuthResponse response = useCase.execute(new AuthRequest("52998224725", "pass"));

        assertEquals("jwt-token", response.token());
    }

    @Test
    void shouldPropagateBadCredentials() {
        when(authenticator.authenticate("52998224725", "wrong")).thenThrow(new BadCredentialsException("bad"));

        assertThrows(BadCredentialsException.class, () -> useCase.execute(new AuthRequest("52998224725", "wrong")));
    }
}
