CREATE TABLE request (
    id              BIGSERIAL PRIMARY KEY,
    requester_id    BIGINT       NOT NULL REFERENCES app_user(id),
    request_type    VARCHAR(64)  NOT NULL,
    current_value   VARCHAR(255),
    requested_value VARCHAR(255),
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    scope           VARCHAR(16)  NOT NULL CHECK (scope IN ('INTERNAL', 'EXTERNAL')),
    approver_id     BIGINT       REFERENCES app_user(id),
    description     VARCHAR(1024),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_request_requester ON request(requester_id);
CREATE INDEX idx_request_status    ON request(status);
CREATE INDEX idx_request_scope     ON request(scope);
