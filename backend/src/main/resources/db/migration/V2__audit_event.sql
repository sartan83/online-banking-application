-- DORA-2.2: append-only, hash-chained audit log.
--
-- Each row's `entry_hash` is HMAC-SHA-256(secret, prev_hash || "\n" || canonical_payload).
-- prev_hash is NULL for the first row. The chain lets us detect tampering
-- (deletes, rewrites, reorders) at verification time.

CREATE TABLE audit_event (
    id              BIGSERIAL PRIMARY KEY,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type      VARCHAR(64)  NOT NULL,
    outcome         VARCHAR(16)  NOT NULL,
    actor_username  VARCHAR(64),
    actor_ip        VARCHAR(64),
    correlation_id  VARCHAR(64),
    resource_type   VARCHAR(64),
    resource_id     VARCHAR(128),
    payload         TEXT         NOT NULL DEFAULT '{}',
    prev_hash       VARCHAR(64),
    entry_hash      VARCHAR(64)  NOT NULL UNIQUE
);

CREATE INDEX idx_audit_event_actor      ON audit_event(actor_username);
CREATE INDEX idx_audit_event_correlation ON audit_event(correlation_id);
CREATE INDEX idx_audit_event_occurred   ON audit_event(occurred_at);
CREATE INDEX idx_audit_event_type       ON audit_event(event_type);
