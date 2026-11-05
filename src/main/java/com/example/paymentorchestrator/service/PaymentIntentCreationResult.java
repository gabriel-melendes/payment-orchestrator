package com.example.paymentorchestrator.service;

import com.example.paymentorchestrator.dto.PaymentIntentResponse;

public record PaymentIntentCreationResult(PaymentIntentResponse response, boolean created) {
}
