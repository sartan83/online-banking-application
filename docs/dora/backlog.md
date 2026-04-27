# DORA Engineering Backlog

PR-sized engineering items to close code-level gaps identified in
`gap-analysis.md`. Each item references the DORA article(s) it supports.

Status: **Open** unless otherwise noted.

## Phase 2 (active)

### DORA-2.1 — Login rate limiting & lockout
- **Articles**: Art. 9 (Protection & prevention), Annex II (strong auth).
- **Why**: `/api/auth/login` is currently unprotected against brute-force /
  credential-stuffing.
- **Approach**: Bucket4j + Caffeine in-memory backend (Redis-ready
  abstraction). Per-username and per-IP buckets, 5 failed attempts within
  15 minutes triggers `429 Too Many Requests` with `Retry-After`. Cleared
  on successful authentication.
- **Acceptance**:
  - 6th attempt within window returns 429.
  - Per-IP bucket protects against username enumeration.
  - Successful login resets the username bucket.
  - Unit + integration tests covering both buckets.

### DORA-2.2 — Audit-event log (append-only, hash-chained)
- **Articles**: Art. 17, 18, 20, 23.
- **Why**: `audit` table exists in `V1__init.sql` but no events are written;
  required to evidence incident reports under Art. 17–20.
- **Approach**: New `audit_event` table with `prev_hash` + `entry_hash`
  (HMAC-SHA-256 of canonicalised payload + previous hash). AOP `@Auditable`
  annotation on auth + transfer + admin endpoints. Correlation-ID filter
  on all requests, propagated via MDC.
- **Acceptance**:
  - All auth events (success / failure / lockout / register) recorded.
  - All transfers recorded with before/after balances.
  - Hash-chain verifier endpoint (admin-only) validates integrity.
  - Tampering with any row is detectable.

### DORA-2.3 — Structured JSON logs + correlation IDs
- **Articles**: Art. 10, 17.
- **Approach**: Logback JSON encoder, Micrometer Tracing (W3C trace-context),
  `X-Correlation-Id` request header propagated across SPA → backend → DB.
- **Acceptance**:
  - Every log line carries `trace_id`, `span_id`, `correlation_id`, `user_id`
    (when authenticated).
  - SPA fetches surface trace IDs in error UIs for support.

### DORA-2.4 — DAST in CI (OWASP ZAP baseline)
- **Articles**: Art. 24, 25.
- **Approach**: Spin up the docker-compose stack in a CI job, run the OWASP
  ZAP baseline scanner against `http://backend:8080`, fail on `High` findings.
- **Acceptance**: ZAP report uploaded as artefact on every PR.

### DORA-2.5 — SBOM + supply-chain scanning + pinned base images
- **Articles**: Art. 8, 28; Annex III (third-party register).
- **Approach**:
  - Maven CycloneDX plugin → `target/bom.json`.
  - npm `@cyclonedx/cyclonedx-npm` → `frontend/bom.json`.
  - Trivy scan of built images, fail on HIGH/CRITICAL.
  - OSV-scanner across both lockfiles.
  - All `Dockerfile`s and `compose.yml` pin images by digest (`@sha256:…`).
- **Acceptance**: SBOMs uploaded as PR artefacts; Trivy + OSV gates green;
  digest-pinned images.

## Phase 3 (queued)

### DORA-2.6 — JWT to RS256 with JWKS rotation
- **Articles**: Art. 9, Annex II.
- **Approach**: Asymmetric keys (RS256), `/.well-known/jwks.json`, rotation
  job, refresh-token table, revocation list.
- **Notes**: Bigger touch; touches `JwtService`, `JwtAuthenticationFilter`,
  SPA token refresh.

### DORA-2.7 — MFA (TOTP) for privileged accounts
- **Articles**: Annex II.
- **Approach**: `mfa_secret` column on `User`, RFC-6238 TOTP, recovery codes,
  `/api/auth/mfa/{enroll,verify}` endpoints, SPA enrolment + challenge UI.

### DORA-2.8 — Column-level encryption for PII
- **Articles**: Art. 9.
- **Approach**: pgcrypto `pgp_sym_encrypt` for sensitive fields; key in
  external secrets manager (Vault / AWS SM). JPA `@Convert` adapter.

### DORA-2.9 — Postgres PITR + restore drill
- **Articles**: Art. 11, 12.
- **Approach**: WAL archiving config, retention policy, weekly restore-drill
  CI job that restores latest backup into ephemeral container, runs smoke
  tests, reports RPO/RTO.

### DORA-2.10 — Documentation package finalisation
- **Articles**: Art. 6, 8, 28(4), 28(8).
- **Approach**: Templates under `docs/dora/` filled out for the operating
  entity (asset inventory, RMF, vendor register, exit plans).

## Tracking

Each PR's title prefix should reference the backlog ID, e.g.
`DORA-2.1: rate-limit /api/auth/login`. PR body links back to this file.
