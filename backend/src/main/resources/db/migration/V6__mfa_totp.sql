-- DORA-2.7: MFA columns for TOTP-based multi-factor authentication
ALTER TABLE app_user ADD COLUMN mfa_secret VARCHAR(64);
ALTER TABLE app_user ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN mfa_enrolled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE app_user ADD COLUMN mfa_recovery_codes_hash TEXT;

-- Pre-enroll MFA for the seeded admin user (test-only secret).
-- Base32 secret: JBSWY3DPEHPK3PXP (test-only, never use in production).
-- Recovery codes are pre-hashed (SHA-256) for the seeded admin.
UPDATE app_user
SET mfa_secret           = 'JBSWY3DPEHPK3PXP',
    mfa_enabled          = TRUE,
    mfa_enrolled_at      = NOW(),
    mfa_recovery_codes_hash = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
WHERE username = 'admin';
