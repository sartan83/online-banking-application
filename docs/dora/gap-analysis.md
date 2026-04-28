# DORA Gap Analysis

Reference: [Regulation (EU) 2022/2554](https://eur-lex.europa.eu/eli/reg/2022/2554/oj)
("DORA"), in force 17 January 2025.

This analysis covers the application code in `backend/`, `frontend/`,
`infra/docker/`, and `.github/`. Organisation-level controls (board approval,
signed contracts, regulator registration, BCP exercising) are flagged
**Out of scope** and are the responsibility of the financial entity operating
the application.

## Pillar 1 — ICT risk management framework (Art. 5–16)

| Requirement | Status | Notes |
|---|---|---|
| Art. 5 — Governance & organisation | Out of scope | Board responsibility. |
| Art. 6 — ICT risk-management framework documented | Planned (DORA-2.10) | Will ship a template under `docs/dora/`. |
| Art. 7 — ICT systems, protocols, tools | Partial | Spring Boot 3 + Postgres 16 + Redis are current and supported; no documented inventory. |
| Art. 8 — Identification (asset inventory, classification) | Missing | No asset/data classification register. |
| Art. 9 — Protection & prevention | Partial | Spring Security 6, BCrypt, JWT (HS256), CSRF (cookie-based), pessimistic locking on transfers. **Missing**: rate limiting, MFA, RS256 JWT with rotation, column-level encryption. |
| Art. 10 — Detection | Missing | No SIEM, no anomaly detection on auth events. |
| Art. 11 — Response & recovery (BCP) | Missing | No documented RPO/RTO, no PITR config, no DR runbook. |
| Art. 12 — Backup, recovery, restoration | Missing | Postgres has no backup policy in compose; no restore drill. |
| Art. 13 — Learning & evolving | Out of scope | Process control. |
| Art. 14 — Communication | Out of scope | Process control. |

## Pillar 2 — ICT-related incident management (Art. 17–23)

| Requirement | Status | Notes |
|---|---|---|
| Art. 17 — ICT incident management process | Partial | `audit` table exists in `V1__init.sql` but unused; no classification, no runbook. |
| Art. 18 — Classification | Planned (DORA-2.2) | Severity matrix in `incident-classification.md`; classification logic to live in incident-handler service. |
| Art. 19 — Reporting major incidents | Out of scope | Notification to competent authority (initial / intermediate / final) is an organisational obligation. The audit log under DORA-2.2 produces the evidence package. |
| Art. 20 — Harmonisation of reporting content | Planned (DORA-2.2) | Audit-event schema designed to capture the Annex-required fields (timestamp, classification, root cause, impact, response). |
| Art. 21 — Centralisation of major-incident reporting | Out of scope | Authority-level. |
| Art. 22 — Supervisory feedback | Out of scope | Authority-level. |
| Art. 23 — Operational/security payment-related incidents | Partial | Transfer flow has pessimistic locking and concurrency tests; lacks audit linkage. |

## Pillar 3 — Digital operational resilience testing (Art. 24–27)

| Requirement | Status | Notes |
|---|---|---|
| Art. 24 — General testing requirements | Partial | SAST: CodeQL (Java + JS/TS), SpotBugs+FindSecBugs, PMD, Checkstyle, ESLint strict. |
| Art. 25 — Testing of ICT tools and systems (annual vuln scans, scenario-based tests) | Planned (DORA-2.4, 2.5) | DAST (OWASP ZAP baseline) and dependency scanning (Trivy, OSV-scanner) to be added. |
| Art. 26 — Advanced testing — TLPT (every 3 years for critical entities) | Out of scope | External red-team engagement; not a code item. |
| Art. 27 — Requirements for testers | Out of scope | Procurement control. |

## Pillar 4 — ICT third-party risk management (Art. 28–44)

| Requirement | Status | Notes |
|---|---|---|
| Art. 28(1)–(2) — Sound management of third-party risk | Missing | No vendor register. |
| Art. 28(3) — Strategy on third-party risk | Out of scope | Board-level policy. |
| Art. 28(4) — Register of contractual arrangements | Planned (DORA-2.10) | Template in `vendor-register-template.md`. |
| Art. 28(7) — Pre-contractual assessment | Out of scope | Procurement control. |
| Art. 28(8) — Exit strategies | Planned (DORA-2.10) | Template in `exit-strategy-template.md`. |
| Art. 30 — Key contractual provisions | Out of scope | Legal control. |
| Art. 31–44 — Oversight framework for critical ICT third-party providers | Out of scope | EU-level supervision. |
| **Application-side**: SBOM, image provenance, dependency scanning | Planned (DORA-2.5) | CycloneDX SBOM, pinned image digests, Trivy + OSV-scanner in CI. |

## Pillar 5 — Information sharing (Art. 45)

| Requirement | Status | Notes |
|---|---|---|
| Art. 45 — Voluntary cyber-threat info sharing | Out of scope | Voluntary; organisational decision. |

## Application-level summary

**Implemented today**
- Modern stack on supported versions (Spring Boot 3.3, Java 17, Postgres 16, Node 20).
- Spring Security 6 with stateless JWT (Bearer), CSRF cookie protection on cookie endpoints.
- BCrypt password hashing with constant-time comparison (timing-oracle fix in PR #1).
- Pessimistic locking with deterministic order on inter-account transfers.
- Auto-managed audit timestamps on entities (`@CreationTimestamp` / `@UpdateTimestamp`).
- Static-analysis suite in CI: CodeQL, SpotBugs+FindSecBugs, PMD, Checkstyle, ESLint strict, SonarCloud (conditional).

**Top 12 application-level gaps** (each tracked in `backlog.md`):
1. No rate limiting / lockout on `/api/auth/login`.
2. No real audit trail; `audit` table is empty.
3. No structured logging or correlation-ID propagation.
4. No SBOM, no container vuln scanning, no pinned base-image digests.
5. Containers run as `root` with writable filesystems.
6. JWT signed with static HS256 secret; no JWKS rotation.
7. No MFA for privileged users.
8. No column-level encryption on PII.
9. No Postgres PITR or backup policy.
10. No JWT revocation list / refresh-token store.
11. nginx config does not enforce HSTS, modern TLS ciphers, or mTLS.
12. No DAST in CI.

These are necessary but **not sufficient** for compliance; see top of file for the
non-code controls that the operating organisation must put in place.
