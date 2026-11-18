package com.fiap.workshop.management.infrastructure.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fiap.workshop.management.domain.exception.SupplyCatalogUnavailableException;
import com.fiap.workshop.management.domain.gateway.SupplyCatalog;
import com.fiap.workshop.management.domain.model.shared.Money;
import com.fiap.workshop.management.domain.model.supply.SupplySnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Looks supplies up in the Inventory service over REST. The call carries the
 * token of the user adding the item, so Inventory sees who asked and applies
 * its own role rules; there is no service account to manage.
 */
@Component
public class InventorySupplyCatalog implements SupplyCatalog {

    private final RestClient restClient;

    public InventorySupplyCatalog(@Qualifier("inventoryRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<SupplySnapshot> findById(UUID supplyId) {
        String token = currentToken();
        try {
            SupplyPayload supply = restClient.get()
                    .uri("/api/v1/supplies/{id}", supplyId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(SupplyPayload.class);
            if (supply == null) {
                throw new SupplyCatalogUnavailableException("Inventory returned an empty answer for supply " + supplyId);
            }
            return Optional.of(new SupplySnapshot(supply.id(), supply.name(), Money.of(supply.unitPrice())));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new SupplyCatalogUnavailableException(
                    "The Inventory service could not be reached to look up supply " + supplyId, e);
        }
    }

    private static String currentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getCredentials() instanceof String token)) {
            throw new SupplyCatalogUnavailableException("There is no authenticated user whose token can be sent to Inventory");
        }
        return token;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SupplyPayload(UUID id, String name, BigDecimal unitPrice) {}
}
