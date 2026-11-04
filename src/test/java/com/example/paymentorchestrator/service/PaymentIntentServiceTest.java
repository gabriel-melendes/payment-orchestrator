package com.example.paymentorchestrator.service;

import com.example.paymentorchestrator.dto.CreatePaymentIntentRequest;
import com.example.paymentorchestrator.entity.PaymentIntent;
import com.example.paymentorchestrator.entity.PaymentIntentStatus;
import com.example.paymentorchestrator.repository.PaymentIntentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentIntentServiceTest {

    @Mock
    private PaymentIntentRepository repository;
    @InjectMocks
    private PaymentIntentService service;

    @Test
    void shouldPersistNewIntentAndReturnSavedIdentifier() {
        UUID savedId = UUID.randomUUID();
        PaymentIntent saved = mock(PaymentIntent.class);
        when(saved.getId()).thenReturn(savedId);
        when(saved.getAmount()).thenReturn(new BigDecimal("125.50"));
        when(saved.getCurrency()).thenReturn("BRL");
        when(saved.getDescription()).thenReturn("Pedido 123");
        when(saved.getStatus()).thenReturn(PaymentIntentStatus.CREATED);
        var createdAt = java.time.Instant.parse("2026-01-01T10:00:00Z");
        when(saved.getCreatedAt()).thenReturn(createdAt);
        when(repository.save(any(PaymentIntent.class))).thenReturn(saved);

        var result = service.create(new CreatePaymentIntentRequest(
                new BigDecimal("125.50"), "BRL", "Pedido 123"));

        var captor = ArgumentCaptor.forClass(PaymentIntent.class);
        verify(repository).save(captor.capture());
        var intent = captor.getValue();
        assertAll(
                () -> assertNull(intent.getId()),
                () -> assertEquals(new BigDecimal("125.50"), intent.getAmount()),
                () -> assertEquals("BRL", intent.getCurrency()),
                () -> assertEquals("Pedido 123", intent.getDescription()),
                () -> assertEquals(PaymentIntentStatus.CREATED, intent.getStatus()),
                () -> assertNotNull(intent.getCreatedAt()),
                () -> assertEquals(savedId, result.id()),
                () -> assertEquals(createdAt, result.createdAt()),
                () -> assertEquals(new BigDecimal("125.50"), result.amount()),
                () -> assertEquals("BRL", result.currency()),
                () -> assertEquals("Pedido 123", result.description()),
                () -> assertEquals(PaymentIntentStatus.CREATED, result.status()));
    }

    @Test
    void shouldPropagatePersistenceFailure() {
        when(repository.save(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> service.create(new CreatePaymentIntentRequest(BigDecimal.ONE, "BRL", null)));
    }
}
