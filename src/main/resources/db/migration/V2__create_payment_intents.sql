CREATE TABLE payment.payment_intents (
    id UUID PRIMARY KEY,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_payment_intents_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_intents_currency CHECK (currency = 'BRL'),
    CONSTRAINT ck_payment_intents_status CHECK (status = 'CREATED')
);
