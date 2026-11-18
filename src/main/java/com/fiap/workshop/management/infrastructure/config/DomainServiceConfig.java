package com.fiap.workshop.management.infrastructure.config;

import com.fiap.workshop.management.domain.service.ServiceOrderDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public ServiceOrderDomainService serviceOrderDomainService() {
        return new ServiceOrderDomainService();
    }
}
