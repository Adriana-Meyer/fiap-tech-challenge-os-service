package com.fiap.workshop.management.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(UserDetailsService userDetailsService, JwtAuthenticationFilter jwtAuthFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tracking/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()

                        // Webhooks — external integrations, authenticated via X-Webhook-Token (checked in-controller), not JWT
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").permitAll()

                        // Service Orders
                        .requestMatchers(HttpMethod.POST, "/api/v1/service-orders").hasAnyRole("CONSULTANT", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/service-orders/full").hasAnyRole("CONSULTANT", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/service-orders").hasAnyRole("CONSULTANT", "MECHANIC", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/service-orders/**").hasAnyRole("CONSULTANT", "MECHANIC", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/service-orders/*/items").hasAnyRole("MECHANIC", "CONSULTANT", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/service-orders/*/items/*").hasAnyRole("MECHANIC", "CONSULTANT", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/service-orders/*/diagnosis/start").hasAnyRole("MECHANIC", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/service-orders/*/diagnosis/complete").hasAnyRole("MECHANIC", "CONSULTANT", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/service-orders/*/estimate/approve").hasAnyRole("CONSULTANT", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/service-orders/*/estimate/reject").hasAnyRole("CONSULTANT", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/service-orders/*/deliver").hasAnyRole("CONSULTANT", "ADMIN")

                        // Customers (GET /customers/{id}/vehicles covered by /customers/**)
                        .requestMatchers(HttpMethod.POST, "/api/v1/customers").hasAnyRole("CONSULTANT", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers/**").hasAnyRole("CONSULTANT", "MECHANIC", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/customers/**").hasAnyRole("CONSULTANT", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/customers/**").hasRole("ADMIN")

                        // Vehicles
                        .requestMatchers(HttpMethod.POST, "/api/v1/vehicles").hasAnyRole("CONSULTANT", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/vehicles/**").hasAnyRole("CONSULTANT", "MECHANIC", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/vehicles/**").hasAnyRole("CONSULTANT", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/vehicles/**").hasRole("ADMIN")

                        // Service Catalog
                        .requestMatchers(HttpMethod.POST, "/api/v1/services").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/services/**").hasAnyRole("CONSULTANT", "MECHANIC", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/services/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/services/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpStatus.UNAUTHORIZED.value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpStatus.FORBIDDEN.value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                        })
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
