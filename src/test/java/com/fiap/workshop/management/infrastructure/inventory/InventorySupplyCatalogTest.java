package com.fiap.workshop.management.infrastructure.inventory;

import com.fiap.workshop.management.domain.exception.SupplyCatalogUnavailableException;
import com.fiap.workshop.management.domain.model.shared.Money;
import com.fiap.workshop.management.domain.model.supply.SupplySnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("InventorySupplyCatalog")
class InventorySupplyCatalogTest {

    private final UUID supplyId = UUID.fromString("b2000000-0000-0000-0000-000000000001");
    private MockRestServiceServer server;
    private InventorySupplyCatalog catalog;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://inventory");
        server = MockRestServiceServer.bindTo(builder).build();
        catalog = new InventorySupplyCatalog(builder.build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "mechanic", "the-user-jwt", List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should return the snapshot of a supply, forwarding the caller's token")
    void shouldReturnTheSnapshotForwardingTheCallersToken() {
        server.expect(requestTo("http://inventory/api/v1/supplies/" + supplyId))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer the-user-jwt"))
                .andRespond(withSuccess("""
                        {"id":"%s","code":"P001","name":"Oil Filter","description":"d","type":"PART",
                         "unitPrice":25.50,"stockQuantity":10,"minimumStock":5,"belowMinimum":false}
                        """.formatted(supplyId), MediaType.APPLICATION_JSON));

        Optional<SupplySnapshot> supply = catalog.findById(supplyId);

        assertTrue(supply.isPresent());
        assertEquals(supplyId, supply.get().id());
        assertEquals("Oil Filter", supply.get().name());
        assertEquals(Money.of(25.50), supply.get().unitPrice());
        server.verify();
    }

    @Test
    @DisplayName("should return empty when Inventory does not know the supply")
    void shouldReturnEmptyWhenInventoryDoesNotKnowTheSupply() {
        server.expect(requestTo("http://inventory/api/v1/supplies/" + supplyId))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(catalog.findById(supplyId).isEmpty());
    }

    @Test
    @DisplayName("should report Inventory unavailable when it answers with a server error")
    void shouldReportUnavailableOnAServerError() {
        server.expect(requestTo("http://inventory/api/v1/supplies/" + supplyId))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(SupplyCatalogUnavailableException.class, () -> catalog.findById(supplyId));
    }

    @Test
    @DisplayName("should report Inventory unavailable when it refuses the token")
    void shouldReportUnavailableWhenItRefusesTheToken() {
        server.expect(requestTo("http://inventory/api/v1/supplies/" + supplyId))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(SupplyCatalogUnavailableException.class, () -> catalog.findById(supplyId));
    }

    @Test
    @DisplayName("should report Inventory unavailable when the connection fails or times out")
    void shouldReportUnavailableOnConnectionFailure() {
        server.expect(requestTo("http://inventory/api/v1/supplies/" + supplyId))
                .andRespond(withException(new IOException("Read timed out")));

        SupplyCatalogUnavailableException e =
                assertThrows(SupplyCatalogUnavailableException.class, () -> catalog.findById(supplyId));
        assertTrue(e.getMessage().contains(supplyId.toString()));
    }

    @Test
    @DisplayName("should report Inventory unavailable when it answers with an empty body")
    void shouldReportUnavailableOnAnEmptyBody() {
        server.expect(requestTo("http://inventory/api/v1/supplies/" + supplyId))
                .andRespond(withSuccess());

        assertThrows(SupplyCatalogUnavailableException.class, () -> catalog.findById(supplyId));
    }

    @Test
    @DisplayName("should not call Inventory when there is no authenticated user to forward")
    void shouldNotCallInventoryWithoutAnAuthenticatedUser() {
        SecurityContextHolder.clearContext();

        assertThrows(SupplyCatalogUnavailableException.class, () -> catalog.findById(supplyId));
        server.verify();
    }
}
