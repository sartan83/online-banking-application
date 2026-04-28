-- DORA-2.8: pgcrypto extension + encrypted email columns.
--
-- This migration adds the columns needed for application-level
-- AES-256-GCM encryption of users.email.  The actual data migration
-- (encrypting existing plaintext emails) is handled by the companion
-- Java migration V7.1.  H2 (used in tests) does not support
-- CREATE EXTENSION, so that statement runs in the Java migration
-- only when PostgreSQL is detected.

ALTER TABLE app_user ADD COLUMN email_enc BYTEA;
ALTER TABLE app_user ADD COLUMN email_search_hash CHAR(64);
