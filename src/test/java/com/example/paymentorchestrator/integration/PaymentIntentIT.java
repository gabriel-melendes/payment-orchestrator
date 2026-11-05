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
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.http.ResponseEntity;

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
        assertEquals(List.of("1", "2", "3"), jdbc.queryForList(
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
                keyed(new CreatePaymentIntentRequest(new BigDecimal("125.50"), "BRL", "Pedido integrado"), UUID.randomUUID().toString()),
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
        var first = http.postForEntity(ENDPOINT, keyed(request, UUID.randomUUID().toString()), PaymentIntentResponse.class);
        var second = http.postForEntity(ENDPOINT, keyed(request, UUID.randomUUID().toString()), PaymentIntentResponse.class);
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

    @Test
    void shouldReplaySameKeyWithSamePersistedData() {
        String key = "replay-key";
        var first = http.postForEntity(ENDPOINT, keyed(new CreatePaymentIntentRequest(
                new BigDecimal("125.50"), "BRL", null), key), PaymentIntentResponse.class);
        var replay = http.postForEntity(ENDPOINT, keyed(new CreatePaymentIntentRequest(
                new BigDecimal("125.5"), "BRL", null), key), PaymentIntentResponse.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertNotNull(first.getBody());
        assertNotNull(replay.getBody());
        assertEquals(first.getBody(), replay.getBody());
        assertEquals(1, countIntents());
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "description", "emptyDescription"})
    void shouldRejectReusedKeyWithDifferentPayload(String difference) {
        String key = "conflict-key";
        var first = http.postForEntity(ENDPOINT, keyed(new CreatePaymentIntentRequest(
                BigDecimal.ONE, "BRL", null), key), PaymentIntentResponse.class);
        var changed = new CreatePaymentIntentRequest(difference.equals("amount") ? BigDecimal.TEN : BigDecimal.ONE,
                "BRL", difference.equals("description") ? "Outro pedido" : difference.equals("emptyDescription") ? "" : null);
        var response = http.postForEntity(ENDPOINT, keyed(changed, key), Map.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().get("status"));
        assertEquals(1, countIntents());
        assertEquals(new BigDecimal("1.00"), jdbc.queryForObject(
                "SELECT amount FROM payment.payment_intents WHERE idempotency_key = ?", BigDecimal.class, key));
        assertNull(jdbc.queryForObject("SELECT description FROM payment.payment_intents WHERE idempotency_key = ?", String.class, key));
    }

    @Test
    void shouldRejectMissingAndOversizedKeysWithoutPersisting() {
        var body = new CreatePaymentIntentRequest(BigDecimal.ONE, "BRL", null);
        assertEquals(HttpStatus.BAD_REQUEST, http.postForEntity(ENDPOINT, body, Map.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.postForEntity(ENDPOINT,
                keyed(body, "a".repeat(129)), Map.class).getStatusCode());
        assertEquals(0, countIntents());
    }

    @Test
    void shouldAllowRetryAfterInvalidPayloadWithoutReservingKey() {
        String key = "retry-after-invalid";
        var invalid = http.postForEntity(ENDPOINT, keyed(new CreatePaymentIntentRequest(
                BigDecimal.ZERO, "BRL", null), key), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        var valid = http.postForEntity(ENDPOINT, keyed(new CreatePaymentIntentRequest(
                BigDecimal.ONE, "BRL", null), key), PaymentIntentResponse.class);
        assertEquals(HttpStatus.CREATED, valid.getStatusCode());
        assertEquals(1, countIntents());
    }

    @Test
    void shouldKeepKeysCaseSensitive() {
        var body = new CreatePaymentIntentRequest(BigDecimal.ONE, "BRL", null);
        assertEquals(HttpStatus.CREATED, http.postForEntity(ENDPOINT,
                keyed(body, "Case-key"), Map.class).getStatusCode());
        assertEquals(HttpStatus.CREATED, http.postForEntity(ENDPOINT,
                keyed(body, "case-key"), Map.class).getStatusCode());
        assertEquals(2, countIntents());
    }

    @Test
    void shouldCreateOnlyOnceForConcurrentIdenticalRequests() throws Exception {
        var request = new CreatePaymentIntentRequest(BigDecimal.ONE, "BRL", null);
        var responses = concurrent(request, request, "same-concurrent-key");
        assertEquals(1, responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count());
        assertEquals(1, responses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count());
        assertNotNull(responses.get(0).getBody());
        assertEquals(responses.get(0).getBody(), responses.get(1).getBody());
        assertEquals(1, countIntents());
    }

    @Test
    void shouldReturnConflictForConcurrentDifferentPayloads() throws Exception {
        var responses = concurrent(new CreatePaymentIntentRequest(BigDecimal.ONE, "BRL", null),
                new CreatePaymentIntentRequest(BigDecimal.TEN, "BRL", null), "different-concurrent-key");
        assertEquals(1, responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count());
        assertEquals(1, responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count());
        var winner = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).findFirst().orElseThrow();
        assertNotNull(winner.getBody());
        assertEquals(0, new BigDecimal(winner.getBody().get("amount").toString()).compareTo(
                jdbc.queryForObject("SELECT amount FROM payment.payment_intents", BigDecimal.class)));
        assertEquals(1, countIntents());
    }

    @Test
    void shouldEnforceUniqueKeyInDatabase() {
        String sql = """
                INSERT INTO payment.payment_intents (id, amount, currency, status, created_at, idempotency_key)
                VALUES (?, 1, 'BRL', 'CREATED', CURRENT_TIMESTAMP, 'unique-key')
                """;
        jdbc.update(sql, UUID.randomUUID());
        var failure = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, UUID.randomUUID()));
        assertInstanceOf(SQLException.class, failure.getMostSpecificCause());
        assertEquals("23505", ((SQLException) failure.getMostSpecificCause()).getSQLState());
        assertTrue(failure.getMostSpecificCause().getMessage().contains("uk_payment_intents_idempotency_key"));
        assertEquals(1, countIntents());
    }

    private List<ResponseEntity<Map>> concurrent(CreatePaymentIntentRequest first,
                                                CreatePaymentIntentRequest second, String key) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<ResponseEntity<Map>> task1 = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return http.postForEntity(ENDPOINT, keyed(first, key), Map.class);
            };
            Callable<ResponseEntity<Map>> task2 = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return http.postForEntity(ENDPOINT, keyed(second, key), Map.class);
            };
            var result1 = executor.submit(task1);
            var result2 = executor.submit(task2);
            return List.of(result1.get(30, TimeUnit.SECONDS), result2.get(30, TimeUnit.SECONDS));
        }
    }

    private HttpEntity<String> jsonBody(String json) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return new HttpEntity<>(json, headers);
    }

    private <T> HttpEntity<T> keyed(T body, String key) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        return new HttpEntity<>(body, headers);
    }

    private int countIntents() {
        return jdbc.queryForObject("SELECT count(*) FROM payment.payment_intents", Integer.class);
    }
}
