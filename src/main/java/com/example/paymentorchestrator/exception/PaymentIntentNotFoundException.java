package com.example.paymentorchestrator.exception;

import java.util.UUID;

public class PaymentIntentNotFoundException extends RuntimeException {

    public PaymentIntentNotFoundException(UUID id) {
        super("Intencao de pagamento nao encontrada: " + id);
    }
}
