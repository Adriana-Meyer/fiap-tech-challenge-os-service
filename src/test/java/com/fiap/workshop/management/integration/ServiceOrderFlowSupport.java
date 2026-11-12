package com.fiap.workshop.management.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.workshop.management.application.dto.auth.AuthRequest;
import com.fiap.workshop.management.application.port.in.saga.HandleSagaEventInputPort;
import com.fiap.workshop.management.application.saga.event.SagaEvent;
import com.fiap.workshop.management.domain.model.shared.Money;
import com.fiap.workshop.management.domain.model.supply.SupplySnapshot;
import com.fiap.workshop.management.infrastructure.persistence.repository.UserJpaRepository;
import com.fiap.workshop.management.infrastructure.security.UserJpaEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup and helpers of the service order integration tests: users and
 * reference data are created once for the whole JVM (the MySQL container is
 * shared), the orders are wiped before each test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ServiceOrderFlowSupport extends BaseIntegrationTest {

    private static final String ADMIN_CPF = "55544433380";
    private static final String MECHANIC_CPF = "66633399901";

    private static UUID customerId;
    private static UUID vehicleId;
    private static UUID catalogItemId;
    private static final UUID SUPPLY_ID = UUID.fromString("b2000000-0000-0000-0000-000000000001");

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected HandleSagaEventInputPort sagaEvents;
    /** Absent in the tests that run the real queues. */
    @Autowired(required = false)
    protected SagaTestConfig.RecordingPublisher publisher;
    @Autowired
    protected SagaTestConfig.FakeSupplyCatalog supplyCatalog;
    @Autowired
    private UserJpaRepository userJpaRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    protected String adminToken;
    protected String mechanicToken;

    @BeforeAll
    void setUpReferenceData() throws Exception {
        saveUserIfMissing(ADMIN_CPF, "ROLE_ADMIN");
        saveUserIfMissing(MECHANIC_CPF, "ROLE_MECHANIC");
        adminToken = loginAndGetToken(ADMIN_CPF);
        mechanicToken = loginAndGetToken(MECHANIC_CPF);

        if (customerId == null) {
            customerId = createCustomer();
            vehicleId = createVehicle(customerId);
            catalogItemId = createCatalogItem();
        }
        supplyCatalog.register(new SupplySnapshot(SUPPLY_ID, "Oil Filter", Money.of(25.00)));
    }

    @BeforeEach
    void cleanServiceOrders() {
        if (publisher != null) {
            publisher.reset();
        }
        jdbcTemplate.execute("DELETE FROM saga_instance");
        jdbcTemplate.execute("DELETE FROM processed_message");
        jdbcTemplate.execute("DELETE FROM service_order_items");
        jdbcTemplate.execute("DELETE FROM service_orders");
    }

    // ---- scenario steps ---------------------------------------------------

    /** An order in diagnosis with one service (150.00) and 2 oil filters (25.00 each). */
    protected UUID orderInDiagnosisWithItems() throws Exception {
        UUID orderId = createServiceOrder();
        patchOk("/api/v1/service-orders/" + orderId + "/diagnosis/start", mechanicToken);
        postOk("/api/v1/service-orders/" + orderId + "/items",
                "{\"serviceCatalogItemId\":\"%s\",\"quantity\":1}".formatted(catalogItemId), mechanicToken);
        postOk("/api/v1/service-orders/" + orderId + "/items",
                "{\"supplyId\":\"%s\",\"quantity\":2}".formatted(SUPPLY_ID), mechanicToken);
        return orderId;
    }

    protected UUID completeDiagnosis(UUID orderId) throws Exception {
        postOk("/api/v1/service-orders/" + orderId + "/diagnosis/complete",
                "{\"diagnosisNotes\":\"Oil and filter replacement required\"}", mechanicToken);
        return sagaId(orderId);
    }

    protected UUID sagaId(UUID orderId) throws Exception {
        return UUID.fromString(sagaOf(orderId).get("id").asText());
    }

    protected JsonNode sagaOf(UUID orderId) throws Exception {
        return json(mockMvc.perform(get("/api/v1/service-orders/" + orderId + "/saga")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()));
    }

    protected JsonNode orderOf(UUID orderId) throws Exception {
        return json(mockMvc.perform(get("/api/v1/service-orders/" + orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()));
    }

    /** The other services answer: the orchestrator handles the event. */
    protected void answer(SagaEvent event) {
        sagaEvents.handle(event);
    }

    // ---- HTTP helpers -----------------------------------------------------

    protected ResultActions performPatch(String url, String token) throws Exception {
        return mockMvc.perform(patch(url).header("Authorization", "Bearer " + token));
    }

    protected ResultActions performPatch(String url, String body, String token) throws Exception {
        return mockMvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(body.strip())
                .header("Authorization", "Bearer " + token));
    }

    protected ResultActions performPost(String url, String body, String token) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body.strip())
                .header("Authorization", "Bearer " + token));
    }

    protected void patchOk(String url, String token) throws Exception {
        performPatch(url, token).andExpect(status().isOk());
    }

    protected void postOk(String url, String body, String token) throws Exception {
        performPost(url, body, token).andExpect(status().isOk());
    }

    protected JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    protected UUID createServiceOrder() throws Exception {
        String response = mockMvc.perform(post("/api/v1/service-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"%s\",\"vehicleId\":\"%s\"}".formatted(customerId, vehicleId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    // ---- reference data ---------------------------------------------------

    private void saveUserIfMissing(String cpf, String role) {
        if (userJpaRepository.findByCpf(cpf).isEmpty()) {
            userJpaRepository.save(new UserJpaEntity(UUID.randomUUID().toString(), cpf,
                    passwordEncoder.encode("pass123"), role, true));
        }
    }

    private String loginAndGetToken(String cpf) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest(cpf, "pass123"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private UUID createCustomer() throws Exception {
        return createdId(post("/api/v1/customers"),
                "{\"name\":\"João Silva\",\"documentNumber\":\"52998224725\",\"phone\":\"11999990000\",\"email\":\"joao@test.com\"}");
    }

    private UUID createVehicle(UUID ownerId) throws Exception {
        return createdId(post("/api/v1/vehicles"),
                "{\"plate\":\"ABC1234\",\"brand\":\"Toyota\",\"model\":\"Corolla\",\"year\":2020,\"color\":\"Silver\",\"customerId\":\"%s\"}"
                        .formatted(ownerId));
    }

    private UUID createCatalogItem() throws Exception {
        return createdId(post("/api/v1/services"),
                "{\"name\":\"Oil Change\",\"description\":\"Engine oil replacement\",\"type\":\"MECHANICAL\",\"basePrice\":150.00}");
    }

    private UUID createdId(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                           String body) throws Exception {
        String response = mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
