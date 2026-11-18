package com.fiap.workshop.management.application.port.in.auth;

import com.fiap.workshop.management.application.dto.auth.AuthRequest;
import com.fiap.workshop.management.application.dto.auth.AuthResponse;

public interface LoginInputPort {
    AuthResponse execute(AuthRequest request);
}
