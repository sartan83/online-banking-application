-- DORA-2.7: MFA columns for TOTP-based multi-factor authentication
ALTER TABLE app_user ADD COLUMN mfa_secret VARCHAR(64);
ALTER TABLE app_user ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN mfa_enrolled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE app_user ADD COLUMN mfa_recovery_codes_hash TEXT;

-- Pre-enroll MFA for the seeded admin user (test-only secret, never use in production).
-- Stored as URL-safe Base64 (matches MfaService.generateSecret format).
-- Base32 equivalent for authenticator apps: AAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQT
-- Test recovery codes: 1234567890, 0987654321 (SHA-256 hashed below).
UPDATE app_user
SET mfa_secret           = 'AAECAwQFBgcICQoLDA0ODxAREhM',
    mfa_enabled          = TRUE,
    mfa_enrolled_at      = NOW(),
    mfa_recovery_codes_hash = 'c775e7b757ede630cd0aa1113bd102661ab38829ca52a6422ab782862f268646
17756315ebd47b7110359fc7b168179bf6f2df3646fcc888bc8aa05c78b38ac1'
WHERE username = 'admin';
