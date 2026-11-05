package com.example.paymentorchestrator.controller;

import com.example.paymentorchestrator.domain.IdempotencyKey;
import com.example.paymentorchestrator.dto.CreatePaymentIntentRequest;
import com.example.paymentorchestrator.dto.PaymentIntentResponse;
import com.example.paymentorchestrator.service.PaymentIntentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment-intents")
public class PaymentIntentController {

    private final PaymentIntentService service;

    public PaymentIntentController(PaymentIntentService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public PaymentIntentResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<PaymentIntentResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CreatePaymentIntentRequest request) {
        var result = service.create(new IdempotencyKey(key), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }
}
