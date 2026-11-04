package com.example.paymentorchestrator.repository;

import com.example.paymentorchestrator.entity.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
}
