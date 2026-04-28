# ICT Risk-Management Framework

EU DORA Articles 6–8 — skeleton template for the institution's ICT risk-management
framework. Placeholders marked **[FILL]** must be completed by the compliance team.

---

## 1. Purpose & Scope

This document establishes the ICT risk-management framework for **[FILL: Entity legal name]**
in accordance with EU Regulation 2022/2554 (Digital Operational Resilience Act — DORA),
Articles 5–15.

The framework covers all ICT systems, networks, and third-party service providers that
support critical or important functions of the institution.

---

## 2. Risk Taxonomy (Art. 6)

### 2.1 Risk Categories

| # | Category | Description | Example threats |
|---|----------|-------------|-----------------|
| R1 | **Availability** | Disruption or unavailability of ICT systems | DDoS, hardware failure, cloud region outage |
| R2 | **Integrity** | Unauthorised or accidental modification of data | SQL injection, insider threat, software bugs |
| R3 | **Confidentiality** | Unauthorised access to or disclosure of data | Credential theft, misconfigured access controls |
| R4 | **Change risk** | Failures introduced by changes to ICT systems | Failed deployments, regression bugs, schema migration errors |
| R5 | **Third-party risk** | Risks arising from ICT service provider dependencies | Vendor lock-in, SLA breach, supply-chain compromise |
| R6 | **Continuity risk** | Inability to recover ICT services within target times | Missing backups, untested disaster-recovery procedures |

### 2.2 Risk Severity Levels

| Level | Impact | Recovery |
|-------|--------|----------|
| **Critical** | Major financial loss, regulatory breach, customer data loss | Immediate response required (< 1 h) |
| **High** | Significant operational disruption | Response within 4 h |
| **Medium** | Limited disruption, workaround available | Response within 1 business day |
| **Low** | Minimal impact, cosmetic issues | Scheduled fix |

---

## 3. Control Objectives (Art. 7)

| Objective | DORA reference | Controls | Owner |
|-----------|---------------|----------|-------|
| Identify and classify ICT assets | Art. 8(1) | Asset inventory, data classification | **[FILL: CISO / IT Ops]** |
| Protect systems and data | Art. 9 | Access control, encryption, patching | **[FILL]** |
| Detect anomalies and incidents | Art. 10 | SIEM, IDS/IPS, log monitoring | **[FILL]** |
| Respond to ICT-related incidents | Art. 17–23 | Incident-response plan, classification matrix | **[FILL]** |
| Recover and restore services | Art. 11 | BCP/DR plans, backup & PITR, failover procedures | **[FILL]** |
| Test operational resilience | Art. 24–27 | DAST, pen-testing, TLPT, restore drills | **[FILL]** |
| Manage third-party ICT risk | Art. 28–30 | Vendor register, exit strategies, SLA monitoring | **[FILL]** |
| Learn and improve | Art. 13 | Post-incident reviews, lessons learned | **[FILL]** |

---

## 4. RACI Matrix

| Activity | Board / Management Body | CISO | IT Operations | Dev Team | Compliance | External Auditor |
|----------|------------------------|------|---------------|----------|------------|------------------|
| Approve ICT risk framework | **A** | R | C | I | C | I |
| Maintain asset inventory | I | A | **R** | C | I | I |
| Implement security controls | I | A | **R** | **R** | C | I |
| Monitor for incidents | I | I | **R** | C | I | I |
| Classify & report incidents | I | **A** | R | C | **R** | I |
| Maintain BCP/DR plans | **A** | R | **R** | C | C | I |
| Conduct resilience testing | I | **A** | R | **R** | C | C |
| Manage third-party risk | **A** | R | C | I | **R** | C |
| Annual framework review | **A** | **R** | C | I | **R** | **R** |

Legend: **R** = Responsible, **A** = Accountable, **C** = Consulted, **I** = Informed.

---

## 5. Board Oversight (Art. 5)

### 5.1 Management Body Responsibilities

The management body shall:

1. **Define and approve** the ICT risk-management framework, including risk appetite and
   tolerance thresholds for ICT disruptions.
2. **Allocate budget** for ICT security, resilience testing, and third-party risk management.
3. **Receive regular reports** (at least quarterly) on ICT risk posture, incident trends,
   and resilience-testing results.
4. **Ensure adequate training** — at least one annual session on ICT risk for all board members.
5. **Approve major ICT changes** — new critical vendor onboarding, architecture changes
   affecting critical functions, major incident responses.

### 5.2 Reporting Cadence

| Report | Frequency | Audience | Owner |
|--------|-----------|----------|-------|
| ICT risk dashboard | Monthly | CISO, CTO | IT Operations |
| Incident summary | Monthly | CISO, Board (quarterly) | Incident Response Team |
| Resilience test results | After each test + annual summary | Board | CISO |
| Third-party risk review | Quarterly | Board, Compliance | Procurement / Compliance |
| Framework review | Annual | Board | CISO + Compliance |

---

## 6. Review Cadence & Continuous Improvement

| Activity | Frequency | Trigger for ad-hoc review |
|----------|-----------|---------------------------|
| Full framework review | **Annual** | Major incident, regulatory change, M&A |
| Risk register update | **Quarterly** | New threat intelligence, vendor change |
| Control effectiveness testing | **Semi-annual** | Failed audit finding |
| BCP/DR drill | **Annual** (minimum) | RTO/RPO miss in production |
| Penetration test | **Annual** + after major release | TLPT mandate by competent authority |
| Third-party assessment | **Annual** per critical vendor | Contract renewal, SLA breach |

### 6.1 Document Control

| Field | Value |
|-------|-------|
| Document owner | **[FILL: CISO name]** |
| Approved by | **[FILL: Board chair / Management body]** |
| Version | 1.0 |
| Last reviewed | **[FILL: Date]** |
| Next review due | **[FILL: Date + 12 months]** |
| Classification | Internal / Confidential |

---

*This template satisfies the structural requirements of DORA Art. 6–8. All **[FILL]**
placeholders must be completed with institution-specific information before the
framework is considered operative.*
