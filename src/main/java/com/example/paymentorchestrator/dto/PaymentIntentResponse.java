package com.example.paymentorchestrator.dto;

import com.example.paymentorchestrator.entity.PaymentIntent;
import com.example.paymentorchestrator.entity.PaymentIntentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentIntentResponse(UUID id, BigDecimal amount, String currency,
                                    String description, PaymentIntentStatus status, Instant createdAt) {

    public static PaymentIntentResponse from(PaymentIntent intent) {
        return new PaymentIntentResponse(intent.getId(), intent.getAmount(), intent.getCurrency(),
                intent.getDescription(), intent.getStatus(), intent.getCreatedAt());
    }
}
