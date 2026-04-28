# DORA-2.8 — Encryption Key Management

## Overview

The application encrypts PII columns at rest using **AES-256-GCM** with a
symmetric key supplied via the environment variable `DB_ENCRYPTION_KEY`.

| Column | Table | Encrypted? | Notes |
|--------|-------|-----------|-------|
| `email` | `app_user` | **Yes** — AES-256-GCM via JPA `AttributeConverter` | Ciphertext stored as `BYTEA`; plaintext never persists after migration V8. |
| `email_search_hash` | `app_user` | No (deterministic SHA-256 of lowercase email) | Used for uniqueness checks and lookup; never used for display. |
| `username` | `app_user` | No | Public-facing; used for login. |
| `actor_username` | `audit_event` | No | Needed for log search/correlation. |
| Balances / amounts | `account`, `transfer` | Not yet | Planned for a future PR. |

## How `DB_ENCRYPTION_KEY` is loaded

| Environment | Mechanism |
|-------------|-----------|
| **Docker Compose (local dev)** | Set in `infra/docker/docker-compose.yml` as a base64-encoded 32-byte key. |
| **CI / Tests** | A fixed dev key is hard-coded in `src/test/resources/application.yml` (`app.encryption.key`) and in the Flyway Java migration fallback. Never use the test key in production. |
| **Production** | **MUST** be loaded from a secrets manager (e.g. AWS Secrets Manager, HashiCorp Vault, Kubernetes Secret). Inject as the `DB_ENCRYPTION_KEY` environment variable. |

### Generating a new key

```bash
# 32 random bytes, base64-encoded
python3 -c "import base64, os; print(base64.b64encode(os.urandom(32)).decode())"
# — or —
openssl rand -base64 32
```

## Key rotation (recipe — not yet implemented)

Key rotation requires re-encrypting every encrypted column with the new key.
A future PR should implement a CLI command or Flyway migration that:

1. Read the **old** key from `DB_ENCRYPTION_KEY_OLD` and the **new** key from
   `DB_ENCRYPTION_KEY`.
2. For each row in `app_user`:
   a. Decrypt `email` with the old key.
   b. Re-encrypt with the new key.
   c. Update the row.
3. Once all rows are re-encrypted, remove `DB_ENCRYPTION_KEY_OLD` from the
   environment.
4. The `email_search_hash` column does **not** change during rotation because
   SHA-256 is key-independent.

### Rotation checklist (manual, until automated)

- [ ] Generate new key.
- [ ] Deploy new key as `DB_ENCRYPTION_KEY` and old key as
      `DB_ENCRYPTION_KEY_OLD`.
- [ ] Run the re-encryption migration/script.
- [ ] Verify a sample of rows decrypt correctly.
- [ ] Remove `DB_ENCRYPTION_KEY_OLD`.

## How tests use a fixed dev key

Tests run against H2 in PostgreSQL-compatibility mode. The encryption key is
set via the Spring property `app.encryption.key` in
`backend/src/test/resources/application.yml`. The Flyway Java migration
(`V7_1__Migrate_email_encryption`) falls back to the same hard-coded test key
when the `DB_ENCRYPTION_KEY` environment variable is absent.

This test key is:
```
zuzU9ht1V8QggzT7DbEsnPGfoMYW8sksbtPJRvw7YGQ=
```
**Never use this key in production.**

## pgcrypto extension

The PostgreSQL `pgcrypto` extension is enabled by migration `V7.1` on
PostgreSQL databases. It provides database-level crypto primitives
(`pgp_sym_encrypt`, `digest`, etc.) that can be used for future batch
operations or database-level encryption if needed. The current runtime
encryption is handled entirely at the application layer (Java AES-256-GCM)
so that the same code path is used on both PostgreSQL and H2.
