package com.fiap.workshop.management.application.usecase.auth;

import com.fiap.workshop.management.application.dto.auth.AuthRequest;
import com.fiap.workshop.management.application.dto.auth.AuthResponse;
import com.fiap.workshop.management.application.port.in.auth.LoginInputPort;
import com.fiap.workshop.management.application.port.out.UserAuthenticator;
import org.springframework.stereotype.Service;

@Service
public class LoginUseCase implements LoginInputPort {

    private final UserAuthenticator authenticator;

    public LoginUseCase(UserAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    public AuthResponse execute(AuthRequest request) {
        return new AuthResponse(authenticator.authenticate(request.cpf(), request.password()));
    }
}
