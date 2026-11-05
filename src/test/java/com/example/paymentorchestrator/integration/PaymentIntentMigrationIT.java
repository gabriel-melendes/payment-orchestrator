package com.example.paymentorchestrator.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PaymentIntentMigrationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("migration_verification")
            .withUsername("migration_test")
            .withPassword(UUID.randomUUID().toString());

    @Test
    void shouldPreserveExistingIntentsWhenUpgradingFromV2() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("2").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        UUID legacyId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment.payment_intents (id, amount, currency, description, status, created_at)
                VALUES (?, 12.34, 'BRL', 'Pedido anterior', 'CREATED', TIMESTAMPTZ '2026-01-01 10:00:00Z')
                """, legacyId);

        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load().migrate();

        var row = jdbc.queryForMap("SELECT * FROM payment.payment_intents WHERE id = ?", legacyId);
        assertEquals(legacyId, row.get("id"));
        assertEquals(new BigDecimal("12.34"), row.get("amount"));
        assertEquals("BRL", row.get("currency"));
        assertEquals("Pedido anterior", row.get("description"));
        assertEquals("CREATED", row.get("status"));
        assertNull(row.get("idempotency_key"));
        assertEquals(java.time.Instant.parse("2026-01-01T10:00:00Z"),
                ((java.sql.Timestamp) row.get("created_at")).toInstant());
        assertEquals(3, jdbc.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE success", Integer.class));
    }
}
