# Business Continuity & Disaster Recovery Plan Template

EU DORA Art. 11 — BCP/DR template for ICT services supporting critical
or important functions.

---

## 1. Document Control

| Field | Value |
|-------|-------|
| Document owner | **[FILL: BCP Manager / CISO]** |
| Approved by | **[FILL: Management body]** |
| Version | 1.0 |
| Last reviewed | **[FILL: Date]** |
| Next review due | **[FILL: Date + 12 months]** |
| Classification | Internal — Restricted |

---

## 2. Scope

This plan covers the ICT systems and services required to maintain the
critical or important functions of **[FILL: Entity name]**. It defines
recovery objectives, procedures, communication plans, and testing schedules.

### 2.1 In-Scope Services

| Service | Tier | Description |
|---------|------|-------------|
| Core banking API | Tier 1 (Critical) | Account access, payments, transfers |
| Authentication & authorisation | Tier 1 (Critical) | JWT issuance, session management |
| PostgreSQL database | Tier 1 (Critical) | Primary data store for all financial records |
| Redis cache | Tier 2 (Important) | Rate limiting, session cache |
| Frontend SPA | Tier 2 (Important) | Customer-facing web application |
| MailHog / SMTP relay | Tier 3 (Standard) | Email notifications (dev: MailHog) |
| Monitoring & logging | Tier 2 (Important) | Observability, audit trail |

---

## 3. RPO / RTO Targets

| Service tier | RPO (max data loss) | RTO (max downtime) | Rationale |
|-------------|--------------------|--------------------|-----------|
| **Tier 1 — Critical** | 5 minutes | 1 hour | WAL archiving every 5 min; base backup + PITR restore within 1 h |
| **Tier 2 — Important** | 1 hour | 4 hours | Can tolerate short data lag; rebuild from config + cache warm-up |
| **Tier 3 — Standard** | 24 hours | 24 hours | Non-essential; rebuild from infrastructure-as-code |

---

## 4. Recovery Procedures

### 4.1 PostgreSQL (Tier 1)

| Step | Action | Owner | Expected duration |
|------|--------|-------|-------------------|
| 1 | Detect failure (automated health check or alert) | Monitoring / On-call | < 5 min |
| 2 | Assess: is failover to replica sufficient, or is PITR required? | DBA / On-call | 5–10 min |
| 3a | **Failover**: promote streaming replica | DBA | 5 min |
| 3b | **PITR**: run `restore.sh` with latest base backup + WAL archive | DBA | 30–45 min |
| 4 | Validate data integrity (run checksums, spot-check recent txns) | DBA + App team | 10 min |
| 5 | Update connection strings / DNS | Infrastructure | 5 min |
| 6 | Notify stakeholders of recovery | Incident Commander | — |

### 4.2 Application Services (Tier 1–2)

| Step | Action | Owner | Expected duration |
|------|--------|-------|-------------------|
| 1 | Detect: health-check failure, alerting | Monitoring | < 5 min |
| 2 | Restart containers / pods from latest image | DevOps | 5–15 min |
| 3 | If persistent: redeploy from CI/CD pipeline | DevOps | 15–30 min |
| 4 | Validate: smoke tests against restored service | QA / Dev team | 10 min |

### 4.3 Full-Site Disaster (all tiers)

| Step | Action | Owner | Expected duration |
|------|--------|-------|-------------------|
| 1 | Activate DR site / secondary region | Infrastructure lead | 15 min |
| 2 | Restore database from off-site backup (S3/GCS) | DBA | 30–45 min |
| 3 | Deploy application stack via IaC | DevOps | 15–30 min |
| 4 | DNS failover to DR site | Infrastructure | 5 min |
| 5 | Full validation + customer comms | IC + Comms team | 30 min |

---

## 5. Communication Plan

### 5.1 Internal Escalation

| Severity | First responder | Escalation path | Timeframe |
|----------|----------------|-----------------|-----------|
| Tier 1 outage | On-call engineer | → CTO → Board | Immediate |
| Tier 2 outage | On-call engineer | → Team lead → CTO | Within 30 min |
| Tier 3 outage | On-call engineer | → Team lead | Within 4 h |

### 5.2 External Communication

| Audience | Channel | Trigger | Owner |
|----------|---------|---------|-------|
| Competent authority | Regulatory reporting portal | Major incident (see incident-classification.md) | Compliance |
| Customers | Status page + email | Tier 1 outage > 15 min | Comms team |
| Partners / vendors | Email / dedicated channel | If vendor services are affected | Procurement |

### 5.3 Escalation Contacts

| Role | Name | Phone | Email |
|------|------|-------|-------|
| Incident Commander | **[FILL]** | **[FILL]** | **[FILL]** |
| DBA (on-call) | **[FILL]** | **[FILL]** | **[FILL]** |
| CTO | **[FILL]** | **[FILL]** | **[FILL]** |
| CISO | **[FILL]** | **[FILL]** | **[FILL]** |
| Compliance Officer | **[FILL]** | **[FILL]** | **[FILL]** |
| External DR provider | **[FILL]** | **[FILL]** | **[FILL]** |

---

## 6. Test Schedule

| Test type | Frequency | Last performed | Next due | Owner |
|-----------|-----------|---------------|----------|-------|
| Tabletop exercise | Semi-annual | **[FILL]** | **[FILL]** | CISO |
| Restore drill (PITR) | Annual (min) | **[FILL]** | **[FILL]** | DBA |
| Failover test (replica promotion) | Annual | **[FILL]** | **[FILL]** | Infrastructure |
| Full-site DR drill | Annual | **[FILL]** | **[FILL]** | IC + Infrastructure |
| Communication drill | Annual | **[FILL]** | **[FILL]** | Comms team |

### 6.1 Automated Restore Drill

A GitHub Actions workflow (`.github/workflows/restore-drill.yml`) is
available for on-demand backup-and-restore testing. It is triggered
manually via `workflow_dispatch` and validates:

- `backup.sh` produces a valid compressed archive
- The archive contains valid PostgreSQL data files
- Known data can be recovered after a destructive drop

See `infra/postgres/README.md` for full details.

---

## 7. Plan Maintenance

- This plan must be reviewed **annually** and after any major incident.
- Changes must be approved by the management body (Art. 5).
- All test results must be documented and retained for **5 years**.
- Gaps identified during testing must be added to the risk register with
  remediation deadlines.

---

*This template satisfies the structural requirements of DORA Art. 11 for
ICT business continuity and disaster recovery. All **[FILL]** placeholders
must be completed with institution-specific information.*
