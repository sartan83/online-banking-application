CREATE TABLE merchant_authorization (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT      NOT NULL REFERENCES app_user(id),
    merchant_id BIGINT      NOT NULL REFERENCES app_user(id),
    status      VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT  uq_merchant_authorization_pair UNIQUE (customer_id, merchant_id),
    CONSTRAINT  ck_merchant_authorization_distinct CHECK (customer_id <> merchant_id)
);
CREATE INDEX idx_merchant_auth_customer ON merchant_authorization(customer_id);
CREATE INDEX idx_merchant_auth_merchant ON merchant_authorization(merchant_id);

CREATE TABLE merchant_payment (
    id                   BIGSERIAL PRIMARY KEY,
    authorization_id     BIGINT         NOT NULL REFERENCES merchant_authorization(id),
    customer_account_id  BIGINT         NOT NULL REFERENCES account(id),
    merchant_account_id  BIGINT         NOT NULL REFERENCES account(id),
    amount               NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    status               VARCHAR(16)    NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED', 'FAILED')),
    description          VARCHAR(255),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_merchant_payment_auth     ON merchant_payment(authorization_id);
CREATE INDEX idx_merchant_payment_customer ON merchant_payment(customer_account_id);
CREATE INDEX idx_merchant_payment_merchant ON merchant_payment(merchant_account_id);
