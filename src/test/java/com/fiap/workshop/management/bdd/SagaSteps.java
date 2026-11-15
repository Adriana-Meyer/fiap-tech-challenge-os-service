package com.fiap.workshop.management.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.workshop.management.application.dto.auth.AuthRequest;
import com.fiap.workshop.management.domain.model.shared.Money;
import com.fiap.workshop.management.domain.model.supply.SupplySnapshot;
import com.fiap.workshop.management.infrastructure.persistence.repository.UserJpaRepository;
import com.fiap.workshop.management.infrastructure.security.UserJpaEntity;
import com.fiap.workshop.management.integration.SagaTestConfig;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The steps of the saga scenarios. They drive the OS service through its REST
 * API, as the mechanic and the consultant do, and check the outcome in the OS
 * and in what each simulated service recorded.
 */
public class SagaSteps {

    private static final Duration PATIENCE = Duration.ofSeconds(30);
    private static final String MECHANIC_CPF = "66633399901";
    private static final String CONSULTANT_CPF = "77700011122";
    private static final String ADMIN_CPF = "55544433380";

    // Created once for the whole JVM: customers and vehicles are unique by document and plate.
    private static final Map<String, UUID> catalogServices = new ConcurrentHashMap<>();
    private static UUID customerId;
    private static UUID vehicleId;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserJpaRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SagaTestConfig.FakeSupplyCatalog supplyCatalog;
    @Autowired
    private SimulatedServices services;

    private String mechanicToken;
    private String consultantToken;
    private String adminToken;
    private UUID orderId;

    @Before
    public void startFromACleanSlate() throws Exception {
        services.reset();
        jdbcTemplate.execute("DELETE FROM saga_instance");
        jdbcTemplate.execute("DELETE FROM processed_message");
        jdbcTemplate.execute("DELETE FROM service_order_items");
        jdbcTemplate.execute("DELETE FROM service_orders");

        saveUserIfMissing(MECHANIC_CPF, "ROLE_MECHANIC");
        saveUserIfMissing(CONSULTANT_CPF, "ROLE_CONSULTANT");
        saveUserIfMissing(ADMIN_CPF, "ROLE_ADMIN");
        mechanicToken = login(MECHANIC_CPF);
        consultantToken = login(CONSULTANT_CPF);
        adminToken = login(ADMIN_CPF);
        synchronized (SagaSteps.class) {
            if (customerId == null) {
                customerId = createdId("/api/v1/customers", consultantToken,
                        "{\"name\":\"Maria Souza\",\"documentNumber\":\"11144477735\",\"phone\":\"11988887777\",\"email\":\"maria@test.com\"}");
                vehicleId = createdId("/api/v1/vehicles", consultantToken,
                        "{\"plate\":\"BDD1A23\",\"brand\":\"Fiat\",\"model\":\"Argo\",\"year\":2021,\"color\":\"Red\",\"customerId\":\"%s\"}"
                                .formatted(customerId));
            }
        }
    }

    // ---- Dado ----------------------------------------------------------------

    @Dado("^um serviço \"([^\"]*)\" de R\\$ ([\\d,]+) no catálogo$")
    public void umServicoNoCatalogo(String name, String price) throws Exception {
        if (!catalogServices.containsKey(name)) {
            catalogServices.put(name, createdId("/api/v1/services", adminToken,
                    "{\"name\":\"%s\",\"description\":\"%s\",\"type\":\"MECHANICAL\",\"basePrice\":%s}"
                            .formatted(name, name, money(price))));
        }
    }

    @E("^uma peça \"([^\"]*)\" de R\\$ ([\\d,]+) com (\\d+) unidades no estoque do Inventory$")
    public void umaPecaNoEstoque(String name, String price, int quantity) {
        supplyCatalog.register(new SupplySnapshot(supplyId(name), name, Money.of(money(price))));
        services.setStock(supplyId(name), quantity);
    }

    @E("^uma OS em diagnóstico com (\\d+) \"([^\"]*)\" e (\\d+) \"([^\"]*)\"$")
    public void umaOsEmDiagnostico(int serviceQuantity, String service, int supplyQuantity, String supply)
            throws Exception {
        orderId = createdId("/api/v1/service-orders", consultantToken,
                "{\"customerId\":\"%s\",\"vehicleId\":\"%s\"}".formatted(customerId, vehicleId));
        send(patch("/api/v1/service-orders/" + orderId + "/diagnosis/start"), mechanicToken, null)
                .andExpect(status().isOk());
        send(post("/api/v1/service-orders/" + orderId + "/items"), mechanicToken,
                "{\"serviceCatalogItemId\":\"%s\",\"quantity\":%d}".formatted(catalogServices.get(service), serviceQuantity))
                .andExpect(status().isOk());
        send(post("/api/v1/service-orders/" + orderId + "/items"), mechanicToken,
                "{\"supplyId\":\"%s\",\"quantity\":%d}".formatted(supplyId(supply), supplyQuantity))
                .andExpect(status().isOk());
    }

    @Dado("^que o estoque do Inventory tem apenas (\\d+) unidades? de \"([^\"]*)\"$")
    public void queOEstoqueTemApenas(int quantity, String supply) {
        services.setStock(supplyId(supply), quantity);
    }

    @Dado("que o Execution não consegue enfileirar a OS")
    public void queOExecutionNaoConsegueEnfileirar() {
        services.breakExecutionQueue();
    }

    @Dado("que o orçamento foi calculado e a OS aguarda a aprovação")
    public void queOOrcamentoFoiCalculado() throws Exception {
        oMecanicoConcluiODiagnostico();
        aOsAguardaAAprovacao();
    }

    // ---- Quando --------------------------------------------------------------

    @Quando("o mecânico conclui o diagnóstico")
    public void oMecanicoConcluiODiagnostico() throws Exception {
        send(post("/api/v1/service-orders/" + orderId + "/diagnosis/complete"), mechanicToken,
                "{\"diagnosisNotes\":\"Troca de óleo e filtros\"}").andExpect(status().isOk());
    }

    @Quando("^o consultor aprova o orçamento pagando com \"([^\"]*)\"$")
    public void oConsultorAprovaOOrcamento(String paymentMethod) throws Exception {
        send(patch("/api/v1/service-orders/" + orderId + "/estimate/approve"), consultantToken,
                "{\"paymentMethod\":\"%s\"}".formatted(paymentMethod)).andExpect(status().isOk());
    }

    @Quando("os mecânicos concluem a execução")
    public void osMecanicosConcluemAExecucao() {
        services.completeExecution(orderId);
    }

    // ---- Então ---------------------------------------------------------------

    @Então("^o Billing calcula um orçamento de R\\$ ([\\d,]+) e a OS aguarda a aprovação$")
    public void oBillingCalculaUmOrcamento(String total) throws Exception {
        aOsAguardaAAprovacao();
        assertEquals(0, money(total).compareTo(orderOf().get("totalAmount").decimalValue()));
    }

    @Então("a OS fica em execução")
    public void aOsFicaEmExecucao() {
        awaitOrderStatus("IN_EXECUTION");
    }

    @Então("a OS fica finalizada")
    public void aOsFicaFinalizada() {
        awaitOrderStatus("FINISHED");
    }

    @Então("a OS fica cancelada")
    public void aOsFicaCancelada() {
        awaitOrderStatus("CANCELLED");
    }

    @E("a saga fica concluída")
    public void aSagaFicaConcluida() {
        awaitSagaStatus("COMPLETED");
    }

    @E("^a saga fica compensada, com o motivo \"([^\"]*)\"$")
    public void aSagaFicaCompensada(String reason) throws Exception {
        awaitSagaStatus("COMPENSATED");
        String failure = sagaOf().path("failureReason").asText();
        assertTrue(failure.contains(reason), "failure reason was: " + failure);
    }

    @E("^o estoque do Inventory (?:fica com|volta a|continua com) (\\d+) unidades? de \"([^\"]*)\"$")
    public void oEstoqueDoInventory(int quantity, String supply) {
        assertEquals(quantity, services.stockOf(supplyId(supply)));
    }

    @E("^a reserva de estoque da OS fica (consumida|liberada)$")
    public void aReservaDeEstoque(String state) {
        assertEquals("consumida".equals(state) ? "CONSUMED" : "RELEASED", services.reservationOf(orderId));
    }

    @E("^o Billing registra o pagamento como (aprovado|recusado|estornado)$")
    public void oBillingRegistraOPagamento(String state) {
        String expected = Map.of("aprovado", "APPROVED", "recusado", "DECLINED", "estornado", "REFUNDED").get(state);
        assertEquals(expected, services.paymentOf(orderId));
    }

    @E("o Billing registra o orçamento como cancelado")
    public void oBillingRegistraOOrcamentoComoCancelado() {
        assertEquals("CANCELLED", services.budgetOf(orderId));
    }

    @E("o Billing nunca recebeu um pedido de pagamento")
    public void oBillingNuncaRecebeuUmPedidoDePagamento() {
        assertEquals(0, services.commandsOf("ProcessPayment", orderId));
        assertEquals(null, services.paymentOf(orderId));
    }

    // ---- support ---------------------------------------------------------------

    private void aOsAguardaAAprovacao() {
        awaitOrderStatus("WAITING_APPROVAL");
    }

    private void awaitOrderStatus(String expected) {
        await().atMost(PATIENCE).pollInterval(Duration.ofMillis(250)).untilAsserted(
                () -> assertEquals(expected, orderOf().get("status").asText()));
    }

    private void awaitSagaStatus(String expected) {
        await().atMost(PATIENCE).pollInterval(Duration.ofMillis(250)).untilAsserted(
                () -> assertEquals(expected, sagaOf().get("status").asText()));
    }

    private JsonNode orderOf() throws Exception {
        return json(mockMvc.perform(get("/api/v1/service-orders/" + orderId)
                .header("Authorization", "Bearer " + consultantToken)).andExpect(status().isOk()));
    }

    private JsonNode sagaOf() throws Exception {
        return json(mockMvc.perform(get("/api/v1/service-orders/" + orderId + "/saga")
                .header("Authorization", "Bearer " + consultantToken)).andExpect(status().isOk()));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body.getBytes(StandardCharsets.UTF_8));
        }
        return mockMvc.perform(request);
    }

    private UUID createdId(String url, String token, String body) throws Exception {
        JsonNode created = json(send(post(url), token, body).andExpect(status().isCreated()));
        assertNotNull(created.get("id"));
        return UUID.fromString(created.get("id").asText());
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void saveUserIfMissing(String cpf, String role) {
        if (userRepository.findByCpf(cpf).isEmpty()) {
            userRepository.save(new UserJpaEntity(UUID.randomUUID().toString(), cpf,
                    passwordEncoder.encode("pass123"), role, true));
        }
    }

    private String login(String cpf) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest(cpf, "pass123"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    /** "150,00" as written in the scenarios. */
    private static BigDecimal money(String text) {
        return new BigDecimal(text.replace(',', '.'));
    }

    /** The same supply id every time, so the scenarios can refer to a supply by its name. */
    private static UUID supplyId(String name) {
        return UUID.nameUUIDFromBytes(("supply:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
