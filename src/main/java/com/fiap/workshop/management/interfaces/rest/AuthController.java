package com.fiap.workshop.management.interfaces.rest;

import com.fiap.workshop.management.application.dto.auth.AuthRequest;
import com.fiap.workshop.management.application.dto.auth.AuthResponse;
import com.fiap.workshop.management.application.port.in.auth.LoginInputPort;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginInputPort loginUseCase;

    public AuthController(LoginInputPort loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        return loginUseCase.execute(request);
    }
}
