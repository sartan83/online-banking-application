CREATE TABLE pending_registration (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(128) NOT NULL,
    role          VARCHAR(32)  NOT NULL CHECK (role IN ('CUSTOMER', 'MERCHANT', 'EMPLOYEE', 'MANAGER', 'ADMIN')),
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requester_id  BIGINT       REFERENCES app_user(id),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_pending_registration_status ON pending_registration(status);
