package com.example.paymentorchestrator.domain;

import com.example.paymentorchestrator.exception.InvalidIdempotencyKeyException;
import java.util.regex.Pattern;

public record IdempotencyKey(String value) {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    public IdempotencyKey {
        if (value == null || value.length() > 128 || !VALID.matcher(value).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
    }
}
