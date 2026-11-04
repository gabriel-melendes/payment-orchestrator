package com.example.paymentorchestrator.controller;

import com.example.paymentorchestrator.dto.CreatePaymentIntentRequest;
import com.example.paymentorchestrator.dto.PaymentIntentResponse;
import com.example.paymentorchestrator.service.PaymentIntentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentIntentResponse create(@Valid @RequestBody CreatePaymentIntentRequest request) {
        return service.create(request);
    }
}
