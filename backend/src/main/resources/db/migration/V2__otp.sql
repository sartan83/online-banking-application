CREATE TABLE otp (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    code       VARCHAR(16)  NOT NULL,
    purpose    VARCHAR(32)  NOT NULL CHECK (purpose IN ('LOGIN', 'CRITICAL_TRANSFER', 'FORGOT_PASSWORD')),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    attempts   INTEGER      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_otp_user_purpose ON otp(user_id, purpose);
CREATE INDEX idx_otp_expires_at  ON otp(expires_at);
