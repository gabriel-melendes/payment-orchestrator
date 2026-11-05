package com.example.paymentorchestrator.service;

import com.example.paymentorchestrator.domain.IdempotencyKey;
import com.example.paymentorchestrator.dto.CreatePaymentIntentRequest;
import com.example.paymentorchestrator.dto.PaymentIntentResponse;
import com.example.paymentorchestrator.entity.PaymentIntent;
import com.example.paymentorchestrator.exception.IdempotencyConflictException;
import com.example.paymentorchestrator.exception.PaymentIntentNotFoundException;
import com.example.paymentorchestrator.repository.PaymentIntentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;

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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentIntentCreationResult create(IdempotencyKey key, CreatePaymentIntentRequest request) {
        var candidate = new PaymentIntent(request.amount(), request.currency(), request.description());
        int inserted = repository.insertIfAbsent(UUID.randomUUID(), candidate.getAmount(),
                candidate.getCurrency(), candidate.getDescription(), candidate.getCreatedAt(), key.value());
        // Em READ_COMMITTED esta consulta ve o commit vencedor, mesmo se o INSERT precisou esperar.
        var persisted = repository.findByIdempotencyKey(key.value())
                .orElseThrow(() -> new IllegalStateException("Intencao ausente apos insercao idempotente"));
        if (persisted.getAmount().compareTo(candidate.getAmount()) != 0
                || !persisted.getCurrency().equals(candidate.getCurrency())
                || !Objects.equals(persisted.getDescription(), candidate.getDescription())) {
            throw new IdempotencyConflictException();
        }
        return new PaymentIntentCreationResult(PaymentIntentResponse.from(persisted), inserted == 1);
    }
}
