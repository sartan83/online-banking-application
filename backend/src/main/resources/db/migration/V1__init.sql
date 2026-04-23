CREATE TABLE app_user (
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL UNIQUE,
    email        VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name    VARCHAR(128) NOT NULL,
    role         VARCHAR(32)  NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE account (
    id           BIGSERIAL PRIMARY KEY,
    owner_id     BIGINT         NOT NULL REFERENCES app_user(id),
    account_type VARCHAR(16)    NOT NULL,
    balance      NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    currency     VARCHAR(3)     NOT NULL DEFAULT 'USD',
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_account_owner ON account(owner_id);

CREATE TABLE transfer (
    id               BIGSERIAL PRIMARY KEY,
    source_account_id BIGINT        NOT NULL REFERENCES account(id),
    target_account_id BIGINT        NOT NULL REFERENCES account(id),
    amount           NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)     NOT NULL DEFAULT 'USD',
    description      VARCHAR(255),
    status           VARCHAR(16)    NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_transfer_source ON transfer(source_account_id);
CREATE INDEX idx_transfer_target ON transfer(target_account_id);

CREATE TABLE audit_log (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT REFERENCES app_user(id),
    action     VARCHAR(64)  NOT NULL,
    details    VARCHAR(1024),
    ip_address VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_user ON audit_log(user_id);
