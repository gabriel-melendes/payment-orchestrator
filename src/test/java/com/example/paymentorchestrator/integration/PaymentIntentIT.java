package com.example.paymentorchestrator.integration;

import com.example.paymentorchestrator.dto.CreatePaymentIntentRequest;
import com.example.paymentorchestrator.dto.PaymentIntentResponse;
import com.example.paymentorchestrator.entity.PaymentIntentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentIntentIT {

    private static final String ENDPOINT = "/api/payment-intents";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("payment_integration")
            .withUsername("payment_test")
            .withPassword(UUID.randomUUID().toString());

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearTestData() {
        // A conexao vem exclusivamente do container de teste via @ServiceConnection.
        jdbc.execute("TRUNCATE TABLE payment.payment_intents");
    }

    @Test
    void shouldApplyProductionMigrationsOnEmptyPostgres() {
        assertEquals(List.of("1", "2"), jdbc.queryForList(
                "SELECT version FROM public.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class));
        assertEquals("numeric", jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'payment' AND table_name = 'payment_intents' AND column_name = 'amount'
                """, String.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT numeric_scale FROM information_schema.columns
                WHERE table_schema = 'payment' AND table_name = 'payment_intents' AND column_name = 'amount'
                """, Integer.class));
    }

    @Test
    void shouldCreatePersistAndRetrievePaymentIntent() {
        var response = http.postForEntity(ENDPOINT,
                new CreatePaymentIntentRequest(new BigDecimal("125.50"), "BRL", "Pedido integrado"),
                PaymentIntentResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        var created = response.getBody();
        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals(PaymentIntentStatus.CREATED, created.status());
        assertNotNull(created.createdAt());
        assertEquals(0, new BigDecimal("125.50").compareTo(created.amount()));
        assertEquals("BRL", created.currency());
        assertEquals("Pedido integrado", created.description());

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT amount, currency, description, status, created_at
                FROM payment.payment_intents WHERE id = ?
                """, created.id());
        assertEquals(new BigDecimal("125.50"), row.get("amount"));
        assertEquals("BRL", row.get("currency"));
        assertEquals("Pedido integrado", row.get("description"));
        assertEquals("CREATED", row.get("status"));
        assertNotNull(row.get("created_at"));

        var lookup = http.getForEntity(ENDPOINT + "/" + created.id(), PaymentIntentResponse.class);
        assertEquals(HttpStatus.OK, lookup.getStatusCode());
        var found = lookup.getBody();
        assertNotNull(found);
        assertEquals(created.id(), found.id());
        assertEquals(0, created.amount().compareTo(found.amount()));
        assertEquals(created.currency(), found.currency());
        assertEquals(created.description(), found.description());
        assertEquals(created.status(), found.status());
        // PostgreSQL armazena microssegundos; nao exigir nanossegundos do Instant original.
        assertTrue(java.time.Duration.between(created.createdAt(), found.createdAt()).abs()
                .compareTo(java.time.Duration.ofMillis(1)) < 0);
        assertEquals(1, countIntents());
    }

    @Test
    void shouldReturn404ForMissingIntent() {
        var response = http.getForEntity(ENDPOINT + "/" + UUID.randomUUID(), Map.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().get("status"));
        assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(response.getHeaders().getContentType()));
        assertEquals(0, countIntents());
    }

    @Test
    void shouldReturn400ForInvalidUuid() {
        var response = http.getForEntity(ENDPOINT + "/not-a-uuid", Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Parametro invalido", response.getBody().get("title"));
        assertEquals(0, countIntents());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"amount\":0,\"currency\":\"BRL\"}",
            "{\"amount\":1.001,\"currency\":\"BRL\"}",
            "{\"amount\":1,\"currency\":\"USD\"}"
    })
    void shouldRejectInvalidRequestWithoutPersisting(String json) {
        var response = http.postForEntity(ENDPOINT, jsonBody(json), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("errors") instanceof List<?>);
        assertEquals(0, countIntents());
    }

    @Test
    void shouldRejectMalformedJsonWithoutPersisting() {
        var response = http.postForEntity(ENDPOINT, jsonBody("{"), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(0, countIntents());
    }

    @Test
    void shouldAcceptOptionalDescriptionAndCreateIndependentIntents() {
        var request = new CreatePaymentIntentRequest(new BigDecimal("0.01"), "BRL", null);
        var first = http.postForEntity(ENDPOINT, request, PaymentIntentResponse.class);
        var second = http.postForEntity(ENDPOINT, request, PaymentIntentResponse.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CREATED, second.getStatusCode());
        assertNotNull(first.getBody());
        assertNotNull(second.getBody());
        assertNull(first.getBody().description());
        assertNotNull(first.getBody().id());
        assertNotNull(second.getBody().id());
        assertNotEquals(first.getBody().id(), second.getBody().id());
        assertEquals(2, countIntents());
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "currency", "status"})
    void shouldEnforceDatabaseCheckConstraints(String invalidField) {
        BigDecimal amount = invalidField.equals("amount") ? BigDecimal.ZERO : BigDecimal.ONE;
        String currency = invalidField.equals("currency") ? "USD" : "BRL";
        String status = invalidField.equals("status") ? "PAID" : "CREATED";
        var failure = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO payment.payment_intents (id, amount, currency, status, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), amount, currency, status));
        var cause = failure.getMostSpecificCause();
        assertInstanceOf(SQLException.class, cause);
        assertEquals("23514", ((SQLException) cause).getSQLState());
        assertTrue(cause.getMessage().contains("ck_payment_intents_" + invalidField));
        assertEquals(0, countIntents());
    }

    private HttpEntity<String> jsonBody(String json) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }

    private int countIntents() {
        return jdbc.queryForObject("SELECT count(*) FROM payment.payment_intents", Integer.class);
    }
}
