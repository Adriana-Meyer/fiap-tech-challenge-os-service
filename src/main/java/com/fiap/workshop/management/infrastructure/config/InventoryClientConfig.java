package com.fiap.workshop.management.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class InventoryClientConfig {

    /** Short timeouts: the Inventory lookup runs while a person waits for the item to be added. */
    @Bean
    public RestClient inventoryRestClient(
            RestClient.Builder builder,
            @Value("${app.inventory.base-url:http://localhost:8081}") String baseUrl,
            @Value("${app.inventory.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.inventory.read-timeout-ms:3000}") long readTimeoutMs) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        return builder
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
