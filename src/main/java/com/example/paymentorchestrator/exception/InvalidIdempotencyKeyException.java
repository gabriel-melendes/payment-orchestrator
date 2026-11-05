package com.example.paymentorchestrator.exception;

public class InvalidIdempotencyKeyException extends RuntimeException {
    public InvalidIdempotencyKeyException() {
        super("Informe Idempotency-Key com 1 a 128 caracteres: letras, numeros, ponto, hifen, sublinhado ou dois-pontos; inicie com letra ou numero.");
    }
}
