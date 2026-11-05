package com.example.paymentorchestrator.domain;

import com.example.paymentorchestrator.exception.InvalidIdempotencyKeyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class IdempotencyKeyTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "a b", "-a", "a,b", "a/b", "a\nb"})
    void shouldRejectInvalidKeys(String value) {
        assertThrows(InvalidIdempotencyKeyException.class, () -> new IdempotencyKey(value));
    }

    @Test
    void shouldEnforceLengthAndPreserveCase() {
        assertEquals("AbC._:-123", new IdempotencyKey("AbC._:-123").value());
        assertEquals(128, new IdempotencyKey("a".repeat(128)).value().length());
        assertThrows(InvalidIdempotencyKeyException.class, () -> new IdempotencyKey("a".repeat(129)));
        assertNotEquals(new IdempotencyKey("abc"), new IdempotencyKey("ABC"));
    }
}
