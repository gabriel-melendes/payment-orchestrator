package com.example.paymentorchestrator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_intents", schema = "payment")
public class PaymentIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", length = 128, unique = true, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentIntentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentIntent() {
        // Construtor exigido pelo JPA.
    }

    public PaymentIntent(BigDecimal amount, String currency, String description) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("O valor deve ser positivo");
        }
        BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (normalizedAmount.precision() > 19) {
            throw new IllegalArgumentException("O valor excede a precisao permitida");
        }
        if (!"BRL".equals(currency)) {
            throw new IllegalArgumentException("A moeda suportada e BRL");
        }
        if (description != null && description.length() > 255) {
            throw new IllegalArgumentException("A descricao excede 255 caracteres");
        }
        this.amount = normalizedAmount;
        this.currency = currency;
        this.description = description;
        this.status = PaymentIntentStatus.CREATED;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public PaymentIntentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
