package com.example.paymentorchestrator.service;

import com.example.paymentorchestrator.dto.CreatePaymentIntentRequest;
import com.example.paymentorchestrator.dto.PaymentIntentResponse;
import com.example.paymentorchestrator.entity.PaymentIntent;
import com.example.paymentorchestrator.repository.PaymentIntentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentIntentService {

    private final PaymentIntentRepository repository;

    public PaymentIntentService(PaymentIntentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PaymentIntentResponse create(CreatePaymentIntentRequest request) {
        var intent = new PaymentIntent(request.amount(), request.currency(), request.description());
        return PaymentIntentResponse.from(repository.save(intent));
    }
}
