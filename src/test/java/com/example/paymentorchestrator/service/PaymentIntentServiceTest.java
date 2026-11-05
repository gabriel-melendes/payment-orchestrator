package com.example.paymentorchestrator.service;

import com.example.paymentorchestrator.domain.IdempotencyKey;
import com.example.paymentorchestrator.dto.CreatePaymentIntentRequest;
import com.example.paymentorchestrator.entity.PaymentIntent;
import com.example.paymentorchestrator.entity.PaymentIntentStatus;
import com.example.paymentorchestrator.exception.IdempotencyConflictException;
import com.example.paymentorchestrator.exception.PaymentIntentNotFoundException;
import com.example.paymentorchestrator.repository.PaymentIntentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentIntentServiceTest {

    @Mock
    private PaymentIntentRepository repository;
    @InjectMocks
    private PaymentIntentService service;

    private final IdempotencyKey key = new IdempotencyKey("pedido-123");
    private final CreatePaymentIntentRequest request = new CreatePaymentIntentRequest(
            new BigDecimal("125.50"), "BRL", "Pedido 123");

    @Test
    void shouldCreateAndReturnPersistedIntent() {
        var persisted = stored(new BigDecimal("125.50"), "Pedido 123");
        when(repository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(repository.findByIdempotencyKey(key.value())).thenReturn(Optional.of(persisted));

        var result = service.create(key, request);

        assertTrue(result.created());
        assertEquals(persisted.getId(), result.response().id());
        assertEquals(persisted.getCreatedAt(), result.response().createdAt());
        assertEquals(new BigDecimal("125.50"), result.response().amount());
        verify(repository).insertIfAbsent(any(UUID.class), eq(new BigDecimal("125.50")),
                eq("BRL"), eq("Pedido 123"), any(Instant.class), eq("pedido-123"));
        verify(repository).findByIdempotencyKey(key.value());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldReplayNumericallyEquivalentAmountWithoutCreating() {
        var persisted = stored(new BigDecimal("125.50"), "Pedido 123");
        when(repository.findByIdempotencyKey(key.value())).thenReturn(Optional.of(persisted));
        var result = service.create(key,
                new CreatePaymentIntentRequest(new BigDecimal("125.5"), "BRL", "Pedido 123"));
        assertFalse(result.created());
        assertEquals(persisted.getId(), result.response().id());
        assertEquals(persisted.getCreatedAt(), result.response().createdAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "description", "nullDescription"})
    void shouldRejectSameKeyWithDifferentPayload(String field) {
        var persisted = stored(field.equals("amount") ? BigDecimal.ONE : request.amount(),
                field.equals("description") ? "Outro pedido" : field.equals("nullDescription") ? null : request.description());
        when(repository.findByIdempotencyKey(key.value())).thenReturn(Optional.of(persisted));
        assertThrows(IdempotencyConflictException.class, () -> service.create(key, request));
    }

    @Test
    void shouldPropagateInsertionFailure() {
        when(repository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("offline"));
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> service.create(key, request));
        verify(repository, never()).findByIdempotencyKey(any());
    }

    private PaymentIntent stored(BigDecimal amount, String description) {
        var intent = new PaymentIntent(amount, "BRL", description);
        ReflectionTestUtils.setField(intent, "id", UUID.randomUUID());
        return intent;
    }

    @Test
    void shouldReturnExistingIntentWithoutWriting() {
        UUID id = UUID.randomUUID();
        PaymentIntent intent = mock(PaymentIntent.class);
        var createdAt = Instant.parse("2026-01-01T10:00:00Z");
        when(intent.getId()).thenReturn(id);
        when(intent.getAmount()).thenReturn(new BigDecimal("12.34"));
        when(intent.getCurrency()).thenReturn("BRL");
        when(intent.getDescription()).thenReturn("Pedido 42");
        when(intent.getStatus()).thenReturn(PaymentIntentStatus.CREATED);
        when(intent.getCreatedAt()).thenReturn(createdAt);
        when(repository.findById(id)).thenReturn(Optional.of(intent));

        var result = service.findById(id);

        assertAll(
                () -> assertEquals(id, result.id()),
                () -> assertEquals(new BigDecimal("12.34"), result.amount()),
                () -> assertEquals("BRL", result.currency()),
                () -> assertEquals("Pedido 42", result.description()),
                () -> assertEquals(PaymentIntentStatus.CREATED, result.status()),
                () -> assertEquals(createdAt, result.createdAt()));
        verify(repository).findById(id);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldThrowWhenIntentDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        var error = assertThrows(PaymentIntentNotFoundException.class, () -> service.findById(id));

        assertTrue(error.getMessage().contains(id.toString()));
        verify(repository).findById(id);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldNotTranslateDatabaseFailureIntoNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("offline"));
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> service.findById(id));
    }
}
