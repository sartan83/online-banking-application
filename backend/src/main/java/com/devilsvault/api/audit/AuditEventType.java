package com.devilsvault.api.audit;

/**
 * Catalog of auditable event types. New events should be added here so the
 * set is reviewable in one place; values are stored verbatim in
 * {@code audit_event.event_type}.
 */
public enum AuditEventType {

    AUTH_LOGIN_SUCCESS,
    AUTH_LOGIN_FAILURE,
    AUTH_LOGIN_RATE_LIMITED,
    AUTH_REGISTER_SUCCESS,
    AUTH_REGISTER_FAILURE,

    TRANSFER_COMPLETED,
    TRANSFER_REJECTED,

    ACCOUNT_FROZEN,
    ACCOUNT_UNFROZEN,

    AUTH_REFRESH_SUCCESS,
    AUTH_REFRESH_FAILURE,
    AUTH_REFRESH_REUSE_DETECTED,
    AUTH_LOGOUT,
    AUTH_LOGOUT_ALL,
    SESSION_REVOKED,

    AUDIT_INTEGRITY_CHECK,

    MFA_ENABLED,
    MFA_DISABLED,
    MFA_LOGIN_SUCCESS,
    MFA_LOGIN_FAILURE
}
