# DevilsVault — Online Banking

Modernization of the original CSE545 Secure Banking System with **EU DORA-aligned** ICT risk controls baked in. The legacy Spring MVC + JSP WAR is preserved at the repo root for reference; the new stack lives alongside it and is what the running app serves.

> **DORA scope**: This repo covers application-level controls. Compliance with the [Digital Operational Resilience Act (Reg. 2022/2554)](https://eur-lex.europa.eu/eli/reg/2022/2554/oj) also requires board-approved policies, signed third-party contracts, tested BCP, and registration with the competent authority. See [§ DORA controls](#dora-controls) below.

## Stack (new)

| Layer     | Tech |
|-----------|------|
| Backend   | Spring Boot 3.5, Java 17, Spring Security 6, Spring Data JPA, Flyway, HikariCP, springdoc-openapi |
| Auth      | RS256-signed JWT access tokens (5 min) + opaque refresh tokens (30 d) with server-side revocation, JWKS at `/.well-known/jwks.json`, BCrypt password hashing, **TOTP MFA** (RFC 6238) required for ADMIN |
| Resilience| Bucket4j + Caffeine login rate limit, hash-chained append-only audit log, Micrometer Tracing (W3C trace-context), Logback JSON encoder |
| Frontend  | Vite + React 18 + TypeScript, Tailwind CSS, TanStack Query v5, React Router, Axios |
| Database  | PostgreSQL 16 (WAL archiving on, `pgcrypto` for application-level PII encryption) |
| Infra     | Docker Compose (Postgres + Redis + MailHog + backend + frontend), nginx (unprivileged) for the SPA |
| CI        | GitHub Actions — backend build/test, frontend build, CodeQL (java + js), CycloneDX SBOM, Trivy (containers + Dockerfiles + frontend/backend deps), OSV-Scanner, OWASP ZAP DAST baseline, SonarCloud, Devin Review |

## Repository layout

```
backend/                 Spring Boot REST API
frontend/                Vite + React SPA (nginx-served)
infra/docker/            Dockerfiles + compose + nginx config
infra/postgres/          backup.sh + restore.sh + WAL archiving config + runbook
infra/zap/               OWASP ZAP baseline rules
.github/workflows/       CI (build, test, CodeQL, supply-chain, dast, restore-drill)
docs/                    Architecture decisions, runbooks
docs/dora/               EU DORA controls matrix, ICT risk framework, BCP/DR + incident + vendor + exit templates
database_scripts/        [legacy] MySQL schema
src/ WebContent/ pom.xml [legacy] Spring MVC 4 + JSP WAR (kept for reference, slated for removal)
```

## Quick start

Requires Docker + Docker Compose.

```bash
cd infra/docker
docker compose up --build
```

Then:

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080/api
- Swagger UI: http://localhost:8080/swagger-ui.html
- MailHog UI: http://localhost:8025
- JWKS: http://localhost:5173/.well-known/jwks.json (proxied)

### Seeded users (Flyway V4 + V6 + V7)

| Username | Password      | Role     | Notes |
|----------|---------------|----------|-------|
| `alice`  | `alicepass1`  | CUSTOMER | Two accounts (CHECKING #1, SAVINGS #2) |
| `bob`    | `bobpass1234` | CUSTOMER | One account (CHECKING #3) |
| `admin`  | `adminpass1`  | ADMIN    | **MFA pre-enrolled.** Test-only TOTP secret: `JBSWY3DPEHPK3PXP` |

ADMIN users are blocked from `/api/admin/**` until MFA is enabled. Generate an admin TOTP code without an authenticator app:

```bash
docker run --rm alpine:3 sh -c "apk add -q oath-toolkit && oathtool -b --totp JBSWY3DPEHPK3PXP"
```

### Local dev without Docker

Backend (requires Java 17 + Maven, Postgres reachable at `jdbc:postgresql://localhost:5432/devilsvault`):

```bash
cd backend
DB_URL=jdbc:postgresql://localhost:5432/devilsvault \
DB_USER=devilsvault DB_PASSWORD=devilsvault \
DB_ENCRYPTION_KEY=$(openssl rand -base64 32) \
mvn spring-boot:run
```

The backend now uses an RS256 JWT key pair generated on boot when no key is configured — for production set `JWT_PRIVATE_KEY_PEM` + `JWT_PUBLIC_KEY_PEM` (see `application.yaml`).

Frontend:

```bash
cd frontend
npm install
npm run dev
```

### Running tests

```bash
# Backend (76 tests, includes integration + audit chain + MFA)
cd backend && mvn -B verify

# Frontend
cd frontend && npm run lint && npm run build
```

## API surface

### Auth

| Method | Path                          | Auth          | Purpose |
|--------|-------------------------------|---------------|---------|
| POST   | `/api/auth/register`          | public        | Register a new customer |
| POST   | `/api/auth/login`             | public        | Login. Returns `{ accessToken, refreshToken }` or `{ mfaRequired, partialToken }` if MFA enabled |
| POST   | `/api/auth/login/mfa`         | partial token | Complete MFA login with 6-digit TOTP or recovery code |
| POST   | `/api/auth/refresh`           | refresh token | Rotate refresh token; issue new access token |
| POST   | `/api/auth/logout`            | bearer        | Revoke current refresh token |
| POST   | `/api/auth/logout-all`        | bearer        | Revoke all of the user's refresh tokens (incident response) |
| POST   | `/api/auth/mfa/enroll`        | bearer        | Generate TOTP secret + 8 recovery codes (one-time view) |
| POST   | `/api/auth/mfa/verify`        | bearer        | Confirm enrollment by verifying first TOTP code |
| POST   | `/api/auth/mfa/disable`       | bearer        | Disable MFA after re-verifying |
| GET    | `/.well-known/jwks.json`      | public        | RSA public key for JWT verification |

### Customer

| Method | Path                                  | Auth   | Purpose |
|--------|---------------------------------------|--------|---------|
| GET    | `/api/me`                             | bearer | Current user, role, MFA status |
| GET    | `/api/accounts`                       | bearer | List caller's accounts |
| GET    | `/api/accounts/{id}/statement`        | bearer | Paginated account statement (debits + credits + running balance) |
| POST   | `/api/transfers`                      | bearer | Internal transfer (pessimistic locking, audited) |
| GET    | `/api/transfers`                      | bearer | Transfer history for caller |

### Admin (ADMIN role + MFA required)

| Method | Path                              | Purpose |
|--------|-----------------------------------|---------|
| GET    | `/api/admin/users`                | List users |
| POST   | `/api/admin/accounts/{id}/freeze` | Freeze an account (audit `ACCOUNT_FROZEN`) |
| POST   | `/api/admin/accounts/{id}/unfreeze` | Unfreeze account |
| GET    | `/api/admin/audit`                | Filter audit log by event type / actor / date range |
| GET    | `/api/admin/audit/integrity`      | Recompute hash chain and report any tampering |

OpenAPI spec at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`.

## DORA controls

The **EU [Digital Operational Resilience Act](https://eur-lex.europa.eu/eli/reg/2022/2554/oj)** (Reg. 2022/2554, in force since 17 Jan 2025) is the regulatory framework that drove the security/resilience design of this project. DORA is governance-first; what a code repo can deliver is a *substrate* of controls that the institution wraps with policies, contracts, and operational drills.

### Status snapshot

| Pillar | Article(s) | What this repo delivers |
|--------|-----------|-------------------------|
| **1. ICT risk management** | 5–16 | Hash-chained append-only audit log of every auth + transfer + admin action (PR #4). Login rate-limiting (PR #3). MFA-TOTP for ADMIN (PR #14). pgcrypto/AES-GCM encryption of `users.email` with deterministic-hash lookup (PR #15). Skeletons for risk taxonomy, RACI, board oversight in [`docs/dora/ict-risk-framework.md`](docs/dora/ict-risk-framework.md). |
| **2. Incident classification & reporting** | 17–23 | Correlation IDs propagated end-to-end via W3C trace-context (PR #11). Structured JSON logs (Logback encoder) with `requestId`, `actor`, `traceId`, `spanId`. Audit log forensics queryable through `/api/admin/audit`. Classification matrix + notification deadlines in [`docs/dora/incident-classification.md`](docs/dora/incident-classification.md). |
| **3. Resilience testing** | 24–27 | SAST: CodeQL (java + js), SpotBugs+FindSecBugs, PMD, Checkstyle, Sonar. DAST: OWASP ZAP baseline scan against an ephemeral compose stack on every PR (PR #10). TLPT remains an external red-team engagement. |
| **4. Third-party risk** | 28–44 | CycloneDX SBOMs (Maven + npm), Trivy (containers, Dockerfiles, deps), OSV-Scanner — all gating on HIGH/CRITICAL (PR #5). Container base images pinned by digest. Populated vendor register in [`docs/dora/vendor-register.csv`](docs/dora/vendor-register.csv). Exit-strategy template per Art. 28(8) at [`docs/dora/exit-strategy-template.md`](docs/dora/exit-strategy-template.md). |
| **5. Information sharing** | 45 | Voluntary; out of repo scope. |
| **BCP / DR** | 11 | `pg_basebackup` wrapper + PITR `restore.sh` + WAL archiving config + runbook in [`infra/postgres/`](infra/postgres/). RPO 5 min, RTO 1 h. Manual restore-drill workflow (`workflow_dispatch` only — see [`.github/workflows/restore-drill.yml`](.github/workflows/restore-drill.yml)). BCP plan template at [`docs/dora/bcp-dr-template.md`](docs/dora/bcp-dr-template.md). |
| **Strong auth (Annex II)** | — | RS256/JWKS access tokens with rotating refresh tokens (PR #12). TOTP MFA enforced for ADMIN, optional for customers (PR #14). |
| **Encryption (Annex I)** | — | App-level AES-256-GCM on `users.email`; deterministic SHA-256 for lookup. pgcrypto enabled in Postgres. Key handling documented at [`docs/dora/key-management.md`](docs/dora/key-management.md). |

Full controls matrix with per-article evidence: [`docs/dora/controls-matrix.md`](docs/dora/controls-matrix.md).

### What's still on the customer

Code can't sign a board minute. The institution must:

1. Adopt and version-control an institution-specific ICT risk framework (use the skeleton at [`docs/dora/ict-risk-framework.md`](docs/dora/ict-risk-framework.md)).
2. Classify and route real incidents per the matrix; meet Art. 19 deadlines (initial 4 h / intermediate 72 h / final 1 month).
3. Sign Art. 30-compliant contracts with every ICT third party. The vendor register lists what to cover.
4. Deploy WAL archiving to durable storage (S3, GCS, Azure Blob, pgBackRest, WAL-G — examples documented).
5. Run TLPT every 3 years for critical entities.
6. Register with the competent authority and submit annual ICT risk reports.

### DORA-related PRs (for traceability)

| PR | Item | Article(s) |
|----|------|-----------|
| [#3](../../pull/3) | Login rate-limit (Bucket4j + Caffeine, per-user + per-IP) | 9, Annex II |
| [#4](../../pull/4) | Hash-chained audit log + correlation-ID filter | 17 |
| [#5](../../pull/5) | SBOMs + Trivy + OSV-Scanner + pinned base images | 28 |
| [#10](../../pull/10) | OWASP ZAP DAST baseline in CI | 24 |
| [#11](../../pull/11) | Structured JSON logs + W3C trace-context propagation | 17 |
| [#12](../../pull/12) | RS256/JWKS + refresh tokens + revocation | 9, Annex II |
| [#13](../../pull/13) | PITR runbook + extended ICT risk docs | 5–8, 11, 17–19, 28(8) |
| [#14](../../pull/14) | TOTP MFA (recovery codes; required for ADMIN) | Annex II |
| [#15](../../pull/15) | pgcrypto / app-level AES-GCM encryption of PII | Annex I |
| [#2](../../pull/2) | DORA documentation package (gap analysis, controls matrix, templates) | 5 |

## Security

- **Passwords**: BCrypt (cost 10).
- **JWTs**: RS256, ephemeral RSA-2048 key pair generated on first boot (rotate via `JWT_PRIVATE_KEY_PEM` + `JWT_PUBLIC_KEY_PEM` in production); access tokens 5 min, refresh tokens 30 d (rotated on use).
- **Refresh-token revocation**: SHA-256 hashed, server-side store (`refresh_token` table), `replaced_by_id` chain for rotation, `revoked_at` for incident response. `POST /api/auth/logout-all` revokes everything.
- **MFA**: TOTP (RFC 6238, 6 digits, 30 s, SHA-1) + 8 hashed single-use recovery codes. Required for ADMIN.
- **CORS**: locked to `CORS_ALLOWED_ORIGINS` (comma-separated env var).
- **CSRF**: `CookieCsrfTokenRepository` + double-submit pattern on the SPA.
- **Money**: `NUMERIC(19,4)`; transfers wrap pessimistic-locked balance reads + writes inside a single transaction; audit log row inside the same transaction (committed `afterCommit` for success, recorded synchronously on failure).
- **PII**: `users.email` encrypted at rest (AES-256-GCM via JPA `AttributeConverter`), looked up via deterministic SHA-256.
- **Secrets**: `.gitignore` blocks `*.jks`, `*.key`, `*.pem`, `.env*`, `database.properties`, `smtp.properties`. **Never** commit secrets — use env vars or a secret manager. `DB_ENCRYPTION_KEY` and JWT keys must be loaded from a secret manager in production.
- **Container hardening**: backend runs as uid 1001, frontend uses `nginx-unprivileged` on port 8080, Dockerfiles pinned by digest.

## Modernization roadmap

See [`docs/modernization-plan.md`](docs/modernization-plan.md) for the full 8-phase plan.

| Phase | Status | What |
|-------|--------|------|
| 0 — Foundation | ✅ | Build, CI, Docker, lint, SAST, secrets hygiene |
| 1 — Boot 3 skeleton | ✅ | Spring Boot 3.5, JPA, JWT, React SPA, basic flows |
| 2 — Full SPA | ✅ | Transfer UI + history (PR #6), account detail + statement (PR #8), admin pages (PR #7), polish/nav/toasts/idle (PR #9) |
| DORA backlog | ✅ (10/10) | PRs #3, #4, #5, #10, #11, #12, #13, #14, #15 + docs PR #2 |
| Future | — | Credit cards, OTP via SMTP, employee/admin authorization workflows, WebAuthn, more pgcrypto coverage |

## Legacy app

The original Spring MVC 4 + JSP WAR is still at the repo root (`pom.xml`, `src/`, `WebContent/`, `database_scripts/`). Retained only for reference while the new stack is being built out; will be removed once all flows are ported.
