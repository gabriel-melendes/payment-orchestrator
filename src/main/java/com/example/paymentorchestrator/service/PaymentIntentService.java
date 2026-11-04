package com.example.paymentorchestrator.service;

import com.example.paymentorchestrator.dto.CreatePaymentIntentRequest;
import com.example.paymentorchestrator.dto.PaymentIntentResponse;
import com.example.paymentorchestrator.entity.PaymentIntent;
import com.example.paymentorchestrator.repository.PaymentIntentRepository;
import org.springframework.stereotype.Service;
import java.util.UUID;
import com.example.paymentorchestrator.exception.PaymentIntentNotFoundException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentIntentService {

    private final PaymentIntentRepository repository;

    public PaymentIntentService(PaymentIntentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PaymentIntentResponse findById(UUID id) {
        var intent = repository.findById(id)
                .orElseThrow(() -> new PaymentIntentNotFoundException(id));
        return PaymentIntentResponse.from(intent);
    }

    @Transactional
    public PaymentIntentResponse create(CreatePaymentIntentRequest request) {
        var intent = new PaymentIntent(request.amount(), request.currency(), request.description());
        return PaymentIntentResponse.from(repository.save(intent));
    }
}
