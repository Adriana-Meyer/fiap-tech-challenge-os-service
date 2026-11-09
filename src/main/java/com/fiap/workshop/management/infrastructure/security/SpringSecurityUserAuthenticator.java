package com.fiap.workshop.management.infrastructure.security;

import com.fiap.workshop.management.application.port.out.UserAuthenticator;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityUserAuthenticator implements UserAuthenticator {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public SpringSecurityUserAuthenticator(AuthenticationManager authenticationManager,
                                           UserDetailsService userDetailsService,
                                           JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    @Override
    public String authenticate(String cpf, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(cpf, password));
        UserDetails userDetails = userDetailsService.loadUserByUsername(cpf);
        return jwtService.generateToken(userDetails);
    }
}
