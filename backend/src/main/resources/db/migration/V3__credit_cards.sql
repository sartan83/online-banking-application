CREATE TABLE credit_account (
    id           BIGSERIAL PRIMARY KEY,
    owner_id     BIGINT         NOT NULL REFERENCES app_user(id),
    card_number  VARCHAR(19)    NOT NULL UNIQUE,
    credit_limit NUMERIC(19, 4) NOT NULL CHECK (credit_limit >= 0),
    balance      NUMERIC(19, 4) NOT NULL DEFAULT 0,
    apr          NUMERIC(5, 4)  NOT NULL CHECK (apr >= 0),
    status       VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CLOSED', 'FROZEN')),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_credit_account_owner ON credit_account(owner_id);

CREATE TABLE credit_transaction (
    id                BIGSERIAL PRIMARY KEY,
    credit_account_id BIGINT         NOT NULL REFERENCES credit_account(id),
    kind              VARCHAR(16)    NOT NULL CHECK (kind IN ('CHARGE', 'PAYMENT', 'INTEREST', 'FEE')),
    amount            NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    source_account_id BIGINT REFERENCES account(id),
    description       VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_credit_txn_account ON credit_transaction(credit_account_id);
CREATE INDEX idx_credit_txn_source  ON credit_transaction(source_account_id);
