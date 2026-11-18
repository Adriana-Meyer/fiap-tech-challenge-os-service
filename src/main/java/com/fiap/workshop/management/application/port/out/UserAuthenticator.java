package com.fiap.workshop.management.application.port.out;

/** Checks a user's credentials and issues the token the other services will trust. */
public interface UserAuthenticator {

    /**
     * @return a signed token carrying the user's CPF and role
     * @throws org.springframework.security.authentication.BadCredentialsException when the credentials are wrong
     */
    String authenticate(String cpf, String password);
}
