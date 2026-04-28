# EU DORA — DevilsVault implementation index

This folder is the consolidated DORA (Digital Operational Resilience Act, Reg. 2022/2554) documentation for the application. It pairs with the application-level controls implemented in the codebase and tracked in the [controls matrix](controls-matrix.md).

> **Scope.** Code can deliver the *substrate* of DORA controls (audit logging, MFA, encryption, SBOMs, DAST, BCP scripts). It cannot sign a board minute, negotiate a third-party contract, or run a TLPT engagement. The institution wraps this repo with policies, contracts, and operational drills to be compliant.

## Index

| Document | Article(s) | Purpose |
|----------|-----------|---------|
| [`controls-matrix.md`](controls-matrix.md) | All | Per-article status, evidence, and gaps. **Start here.** |
| [`ict-risk-framework.md`](ict-risk-framework.md) | 5–8 | Risk taxonomy, control objectives, RACI, board oversight, review cadence. Skeleton with `[FILL]` placeholders. |
| [`incident-classification.md`](incident-classification.md) | 17–19 | Significance / critical-services / data-loss / geographic-spread / reputational matrix; decision tree; Art. 19 notification deadlines. |
| [`bcp-dr-template.md`](bcp-dr-template.md) | 11 | BCP/DR plan template — RPO/RTO per service tier, recovery procedures, comms plan, escalation, test schedule. |
| [`vendor-register.csv`](vendor-register.csv) | 28 | Populated vendor register (Postgres, Redis, Spring, React, Vite, TanStack, Axios, Tailwind, Flyway, Bucket4j, Caffeine, JJWT, MailHog, GitHub, Docker). Columns track Art. 30 contract clauses + criticality. |
| [`exit-strategy-template.md`](exit-strategy-template.md) | 28(8) | Per-vendor exit plan template (data extraction, alternative provider, transition timeline, contractual cooperation). |
| [`key-management.md`](key-management.md) | Annex I | How `DB_ENCRYPTION_KEY` is loaded, what's encrypted, rotation recipe. |

## How the codebase maps to the 5 DORA pillars

### Pillar 1 — ICT risk management (Art. 5–16)

| Control | Where | Article |
|---------|-------|---------|
| Hash-chained append-only audit log | `backend/.../audit/AuditEventService.java`, Flyway `V2`, [`controls-matrix.md`](controls-matrix.md) | Art. 9 |
| Login rate limiting (Bucket4j + Caffeine, per-user + per-IP) | `backend/.../auth/RateLimitFilter.java` | Art. 9 |
| TOTP MFA (RFC 6238) — required for ADMIN, optional for customers | `backend/.../auth/MfaService.java`, `frontend/.../SettingsPage.tsx`, Flyway `V6` | Annex II |
| pgcrypto / AES-GCM encryption of `users.email` | `backend/.../user/EmailEncryptionConverter.java`, Flyway `V7`+`V8` | Annex I |
| Risk taxonomy + RACI + board oversight skeleton | [`ict-risk-framework.md`](ict-risk-framework.md) | Art. 5–8 |

### Pillar 2 — Incident classification & reporting (Art. 17–23)

| Control | Where | Article |
|---------|-------|---------|
| Correlation IDs propagated end-to-end (W3C trace-context) | `backend/.../filter/CorrelationIdFilter.java`, `frontend/src/api.ts` | Art. 17 |
| Structured JSON logs (Logback encoder) — `traceId`, `spanId`, `requestId`, `actor`, MDC | `backend/src/main/resources/logback-spring.xml` | Art. 17 |
| Audit log forensics queryable through `/api/admin/audit` | `backend/.../audit/AdminAuditController.java` | Art. 17 |
| Hash-chain integrity check (`/api/admin/audit/integrity`) | `backend/.../audit/AuditEventService.java#verifyChain` | Art. 17 |
| Classification matrix + notification deadlines | [`incident-classification.md`](incident-classification.md) | Art. 18–19 |

### Pillar 3 — Resilience testing (Art. 24–27)

| Control | Where | Article |
|---------|-------|---------|
| SAST: CodeQL (java + js), SpotBugs+FindSecBugs, PMD, Checkstyle, Sonar | `.github/workflows/ci.yml` | Art. 24 |
| DAST: OWASP ZAP baseline scan against ephemeral compose stack on every PR | `.github/workflows/dast.yml`, `infra/zap/baseline-rules.tsv` | Art. 24 |
| TLPT (threat-led pen test) | External engagement — out of repo scope | Art. 25 |

### Pillar 4 — Third-party risk management (Art. 28–44)

| Control | Where | Article |
|---------|-------|---------|
| CycloneDX SBOMs (Maven + npm), uploaded as CI artifacts | `.github/workflows/supply-chain.yml` | Art. 28 |
| Trivy (containers, Dockerfiles, frontend/backend deps) | `.github/workflows/supply-chain.yml` | Art. 28 |
| OSV-Scanner with HIGH/CRITICAL gate + `set -o pipefail` | `.github/workflows/supply-chain.yml` | Art. 28 |
| Container base images pinned by digest | `infra/docker/Dockerfile.*`, `infra/docker/docker-compose.yml` | Art. 28 |
| Populated vendor register | [`vendor-register.csv`](vendor-register.csv) | Art. 28 |
| Exit-strategy template per vendor | [`exit-strategy-template.md`](exit-strategy-template.md) | Art. 28(8) |

### Pillar 5 — Information sharing (Art. 45)

Voluntary; out of repo scope.

### BCP / DR (Art. 11)

| Control | Where |
|---------|-------|
| `pg_basebackup` wrapper script | [`../../infra/postgres/backup.sh`](../../infra/postgres/backup.sh) |
| PITR `restore.sh` (input-sanitized) | [`../../infra/postgres/restore.sh`](../../infra/postgres/restore.sh) |
| WAL archiving config sample | [`../../infra/postgres/postgresql.conf.sample`](../../infra/postgres/postgresql.conf.sample) |
| WAL archiving enabled in compose | `infra/docker/docker-compose.yml` (`command:` flags + `wal_archive` volume) |
| Manual restore-drill workflow | [`../../.github/workflows/restore-drill.yml`](../../.github/workflows/restore-drill.yml) |
| BCP plan template (RPO/RTO, comms, escalation) | [`bcp-dr-template.md`](bcp-dr-template.md) |

RPO target: **5 min**. RTO target: **1 h**. Deployment to durable storage (S3/GCS/Azure Blob/pgBackRest/WAL-G) is on the operator.

## Operator checklist

Code can't make this institution DORA-compliant. The operator/legal/compliance team must:

1. **Adopt the ICT risk framework.** Fill in [`ict-risk-framework.md`](ict-risk-framework.md) with institution-specific risk appetite, RACI ownership, and board-approved control objectives. Version-control it.
2. **Sign Art. 30-compliant contracts** with every ICT third party in [`vendor-register.csv`](vendor-register.csv). The columns track which clauses must be present.
3. **Deploy WAL archiving to durable storage.** The repo's compose archives to a local volume — production must use S3/GCS/Azure Blob/pgBackRest/WAL-G. See [`../../infra/postgres/README.md`](../../infra/postgres/README.md).
4. **Run TLPT every 3 years** for critical/important entities. External red-team engagement.
5. **Register with the competent authority** (national regulator, e.g. Banca d'Italia for IT, BaFin for DE, ACPR for FR). Submit annual ICT risk reports.
6. **Document and test BCP** at least annually. Use [`bcp-dr-template.md`](bcp-dr-template.md) as the starting point.
7. **Classify and route real incidents** per [`incident-classification.md`](incident-classification.md). Meet Art. 19 deadlines (initial 4 h / intermediate 72 h / final 1 month).
8. **Rotate keys.** `DB_ENCRYPTION_KEY` and the JWT RSA key pair must be loaded from a secret manager and rotated per [`key-management.md`](key-management.md).

## Traceability — DORA-related PRs

| PR | Item | Article(s) |
|----|------|-----------|
| [#2](../../../../pull/2) | DORA documentation package (gap analysis, controls matrix, templates) | 5 |
| [#3](../../../../pull/3) | Login rate-limit (Bucket4j + Caffeine, per-user + per-IP) | 9, Annex II |
| [#4](../../../../pull/4) | Hash-chained audit log + correlation-ID filter | 17 |
| [#5](../../../../pull/5) | SBOMs + Trivy + OSV-Scanner + pinned base images | 28 |
| [#10](../../../../pull/10) | OWASP ZAP DAST baseline in CI | 24 |
| [#11](../../../../pull/11) | Structured JSON logs + W3C trace-context propagation | 17 |
| [#12](../../../../pull/12) | RS256/JWKS + refresh tokens + revocation | 9, Annex II |
| [#13](../../../../pull/13) | PITR runbook + extended ICT risk docs | 5–8, 11, 17–19, 28(8) |
| [#14](../../../../pull/14) | TOTP MFA (recovery codes; required for ADMIN) | Annex II |
| [#15](../../../../pull/15) | pgcrypto / app-level AES-GCM encryption of PII | Annex I |
