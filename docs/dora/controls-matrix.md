# DORA Controls Matrix

Maps DORA articles to specific controls in this codebase. Status values:
**Implemented**, **Partial**, **Planned**, **Out of scope**.

## Pillar 1 — ICT risk management framework

| Article | Control | Status | Evidence / Plan |
|---|---|---|---|
| 5 | Internal governance | Out of scope | Board-level. |
| 6 | RMF documented & reviewed annually | Planned | `docs/dora/risk-framework-template.md` (DORA-2.10). |
| 7(a) | Modern, supported tech stack | Implemented | `backend/pom.xml`, `frontend/package.json`. |
| 7(b) | Capacity / performance monitoring | Planned | Micrometer + Prometheus (DORA-2.3). |
| 8 | Asset inventory & data classification | Planned | `docs/dora/asset-inventory-template.md` (DORA-2.10). |
| 9(a) | Strong authentication | Partial | BCrypt + JWT today; MFA missing (DORA-2.7). |
| 9(b) | Cryptography & key management | Partial | HS256 today; RS256/JWKS planned (DORA-2.6). |
| 9(c) | Secure development | Implemented | SAST suite: CodeQL, SpotBugs+FindSecBugs, PMD, Checkstyle, ESLint strict, SonarCloud. |
| 9(d) | Network security & segmentation | Partial | nginx in front of SPA; backend not behind WAF; no mTLS service-to-service. |
| 9(e) | ICT project management | Out of scope | Process. |
| 9(f) | Physical security | Out of scope | Hosting concern. |
| 10 | Detection (SIEM, anomaly) | Planned | DORA-2.2 audit log + DORA-2.3 structured logs feed downstream SIEM. |
| 11(1) | BCP | Out of scope | Org policy + tested annually. |
| 11(2) | RTO / RPO defined per service | Planned | DORA-2.9. |
| 12 | Backup & restore | Planned | DORA-2.9 (Postgres PITR + weekly restore drill in CI). |
| 13 | Learning from incidents | Out of scope | Process. |

## Pillar 2 — Incident management

| Article | Control | Status | Evidence / Plan |
|---|---|---|---|
| 17(1) | ICT incident-management process | Planned | `docs/dora/incident-classification.md` + DORA-2.2. |
| 17(2) | Classification of incidents | Planned | DORA-2.2; criteria per Art. 18. |
| 17(3) | Operational/security payment incidents | Partial | Transfer flow audited under DORA-2.2. |
| 18 | Classification criteria & materiality thresholds | Planned | `docs/dora/incident-classification.md`. |
| 19(1)–(7) | Notification to authority | Out of scope | Organisational; audit log under DORA-2.2 produces evidence. |
| 20 | Harmonised reporting content & templates | Planned | DORA-2.2 schema aligns with RTS. |

## Pillar 3 — Resilience testing

| Article | Control | Status | Evidence / Plan |
|---|---|---|---|
| 24(1) | Comprehensive testing programme | Partial | SAST in CI; DAST + SCA planned (DORA-2.4, 2.5). |
| 25(1) | Annual vulnerability assessments | Partial | CodeQL on every PR + main; Trivy/OSV planned (DORA-2.5). |
| 25(1) | Network security assessments | Out of scope | External pen-test. |
| 25(1) | Source-code review (SAST) | Implemented | Five SAST tools wired to CI. |
| 25(1) | Performance / penetration testing | Out of scope | External engagement. |
| 25(1) | Scenario-based tests | Planned | DORA-2.9 includes restore drill. |
| 26 | Threat-Led Penetration Testing (TLPT) | Out of scope | Critical-entity obligation; external. |
| 27 | Tester independence & skill | Out of scope | Procurement. |

## Pillar 4 — Third-party risk

| Article | Control | Status | Evidence / Plan |
|---|---|---|---|
| 28(1)(a) | Vendor strategy | Out of scope | Org policy. |
| 28(1)(c) | Vendor register | Planned | `docs/dora/vendor-register-template.md`. |
| 28(2) | Pre-contractual assessment | Out of scope | Procurement. |
| 28(3) | Concentration risk monitoring | Out of scope | Org-level. |
| 28(8) | Exit plans for critical providers | Planned | `docs/dora/exit-strategy-template.md`. |
| 30 | Key contractual provisions | Out of scope | Legal. |
| App-side | SBOM (CycloneDX) | Planned | DORA-2.5 (Maven + npm). |
| App-side | Container provenance (digest pinning) | Planned | DORA-2.5. |
| App-side | Dependency vulnerability scanning | Planned | Trivy + OSV-scanner in CI (DORA-2.5). |

## Pillar 5 — Information sharing

| Article | Control | Status | Evidence / Plan |
|---|---|---|---|
| 45 | Voluntary threat-info sharing | Out of scope | Voluntary, org-level. |

## Annex II — Additional cryptographic & access controls

| Item | Control | Status | Evidence / Plan |
|---|---|---|---|
| Strong authentication for privileged users | MFA (TOTP) on admin accounts | Planned | DORA-2.7. |
| Cryptographic key lifecycle | JWT key rotation, JWKS endpoint | Planned | DORA-2.6. |
| Encryption at rest for sensitive data | pgcrypto column-level encryption | Planned | DORA-2.8. |
