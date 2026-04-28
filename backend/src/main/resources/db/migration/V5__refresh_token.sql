CREATE TABLE refresh_token (
    id             BIGSERIAL       PRIMARY KEY,
    user_id        BIGINT          NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash     VARCHAR(255)    NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at     TIMESTAMP WITH TIME ZONE,
    replaced_by_id BIGINT          REFERENCES refresh_token(id),
    device_label   VARCHAR(255)
);

CREATE INDEX idx_refresh_token_user_revoked ON refresh_token (user_id, revoked_at);
CREATE INDEX idx_refresh_token_hash ON refresh_token (token_hash);
