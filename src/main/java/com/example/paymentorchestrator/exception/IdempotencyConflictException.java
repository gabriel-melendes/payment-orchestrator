package com.example.paymentorchestrator.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("A chave de idempotencia ja foi utilizada com dados diferentes.");
    }
}
