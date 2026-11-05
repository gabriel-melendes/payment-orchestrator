package com.example.paymentorchestrator.repository;

import com.example.paymentorchestrator.entity.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {

    Optional<PaymentIntent> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(value = """
            INSERT INTO payment.payment_intents
                (id, amount, currency, description, status, created_at, idempotency_key)
            VALUES (:id, :amount, :currency, :description, 'CREATED', :createdAt, :key)
            ON CONFLICT ON CONSTRAINT uk_payment_intents_idempotency_key DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("amount") BigDecimal amount,
                       @Param("currency") String currency, @Param("description") String description,
                       @Param("createdAt") Instant createdAt, @Param("key") String key);
}
