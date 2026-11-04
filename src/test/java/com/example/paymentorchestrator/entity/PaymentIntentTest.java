package com.example.paymentorchestrator.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentIntentTest {

    @Test
    void shouldNormalizeAmountWithoutRounding() {
        var intent = new PaymentIntent(new BigDecimal("10"), "BRL", null);
        assertEquals(new BigDecimal("10.00"), intent.getAmount());
        assertEquals(PaymentIntentStatus.CREATED, intent.getStatus());
        assertNotNull(intent.getCreatedAt());
        assertNull(intent.getDescription());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-0.01", "100000000000000000.00"})
    void shouldRejectInvalidAmounts(String amount) {
        assertThrows(IllegalArgumentException.class, () -> new PaymentIntent(
                amount == null ? null : new BigDecimal(amount), "BRL", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.001", "1.999"})
    void shouldRejectAmountsRequiringRounding(String amount) {
        assertThrows(ArithmeticException.class,
                () -> new PaymentIntent(new BigDecimal(amount), "BRL", null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"USD", "brl", " BRL"})
    void shouldRejectUnsupportedCurrency(String currency) {
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentIntent(BigDecimal.ONE, currency, null));
    }

    @Test
    void shouldAcceptMaximumAmountAndDescription() {
        var intent = new PaymentIntent(new BigDecimal("99999999999999999.99"), "BRL", "a".repeat(255));
        assertEquals(new BigDecimal("99999999999999999.99"), intent.getAmount());
    }

    @Test
    void shouldRejectLongDescription() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentIntent(BigDecimal.ONE, "BRL", "a".repeat(256)));
    }
}
