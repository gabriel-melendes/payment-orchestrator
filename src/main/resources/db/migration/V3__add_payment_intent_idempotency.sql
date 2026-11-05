-- NULL preserva os registros antigos, que nao receberam chave no cadastro original.
ALTER TABLE payment.payment_intents
    ADD COLUMN idempotency_key VARCHAR(128),
    ADD CONSTRAINT uk_payment_intents_idempotency_key UNIQUE (idempotency_key),
    ADD CONSTRAINT ck_payment_intents_idempotency_key CHECK (
        idempotency_key IS NULL OR idempotency_key ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'
    );
