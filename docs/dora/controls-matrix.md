# EU DORA Controls Matrix

Tracks implementation status of ICT risk-management controls required by the
Digital Operational Resilience Act (DORA).

| Article | Control area | Status | Evidence / notes |
|---------|-------------|--------|-----------------|
| Art. 5 | ICT governance — board oversight | **Template** | `docs/dora/ict-risk-framework.md` §5 — board responsibilities, reporting cadence, approval gates. Requires institution-specific completion. |
| Art. 6–8 | ICT risk-management framework | **Template** | `docs/dora/ict-risk-framework.md` — risk taxonomy, control objectives, RACI matrix, review cadence. Requires institution-specific completion. |
| Art. 11 | BCP/DR — backup policy & recovery procedures | **Implemented (repo-level)** | `infra/postgres/backup.sh` (base backup), `infra/postgres/restore.sh` (PITR), `infra/postgres/postgresql.conf.sample` (WAL archiving config). RPO: 5 min, RTO: 1 h. `docker-compose.yml` enables WAL archiving in dev. **Deployment to production is on the operator.** |
| Art. 11 | BCP/DR — restore testing | **Implemented (manual)** | `.github/workflows/restore-drill.yml` — `workflow_dispatch` restore drill: seed → backup → drop → restore → verify. Not run on every PR (expensive). |
| Art. 11 | BCP/DR plan template | **Template** | `docs/dora/bcp-dr-template.md` — RPO/RTO per service tier, recovery procedures, communication plan, escalation contacts, test schedule. Requires institution-specific completion. |
| Art. 16 | Simplified ICT risk-management framework | N/A | Not an exempt entity |
| Art. 17–23 | ICT-related incident management | **Template** | `docs/dora/incident-classification.md` — classification matrix (significance, critical services, data loss, geographic spread, reputational impact), decision tree, notification deadlines (initial: 4 h, intermediate: 72 h, final: 1 month). |
| Art. 24 | ICT resilience testing — DAST | **Partial** | OWASP ZAP baseline scan runs in CI on every PR (`.github/workflows/dast.yml`). Fails on HIGH-severity findings. Full authenticated scan and annual pen-test still required. |
| Art. 25 | TLPT (threat-led penetration testing) | Planned | Requires external red-team engagement |
| Art. 28 | ICT third-party risk — vendor register | **Implemented** | `docs/dora/vendor-register.csv` — populated with actual application dependencies (PostgreSQL, Redis, Spring, React, GitHub, Docker, etc.). Columns: criticality, data accessed, Art. 30 contract clauses, exit-strategy link, last review date. |
| Art. 28 | ICT third-party risk — supply-chain scanning | **Partial** | CycloneDX SBOMs, Trivy, and OSV-Scanner gates in `supply-chain.yml` |
| Art. 28(8) | ICT third-party exit strategy | **Template** | `docs/dora/exit-strategy-template.md` — data extraction, alternative provider assessment, transition timeline, contractual cooperation requirements. Requires per-vendor completion. |
| Annex I | Encryption of personal data at rest | **Partial** | Application-level AES-256-GCM on `users.email` via JPA `AttributeConverter`; deterministic SHA-256 hash for lookup. pgcrypto extension enabled. Broader rollout to other PII columns planned. See `docs/dora/key-management.md`. |
| Art. 45 | Information sharing | Planned | — |
