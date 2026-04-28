# ICT Third-Party Exit Strategy Template

EU DORA Art. 28(8) — template for planning the exit from a critical or
important ICT third-party service provider.

---

## 1. Document Control

| Field | Value |
|-------|-------|
| Document owner | **[FILL: Procurement / IT lead]** |
| Vendor name | **[FILL]** |
| Service provided | **[FILL]** |
| Criticality | **[FILL: CRITICAL / HIGH / MEDIUM / LOW]** |
| Contract expiry | **[FILL]** |
| Version | 1.0 |
| Last reviewed | **[FILL]** |

---

## 2. Purpose

This document defines the exit strategy for **[FILL: vendor name / service]**
in accordance with DORA Art. 28(8). It ensures the institution can terminate
or transition away from this ICT service provider without unacceptable
disruption to critical or important functions.

---

## 3. Exit Triggers

An exit may be triggered by any of the following:

- [ ] Contract expiry or non-renewal
- [ ] Vendor insolvency or cessation of service
- [ ] Sustained SLA breaches (> **[FILL]** incidents in **[FILL]** months)
- [ ] Regulatory requirement or competent-authority instruction
- [ ] Security incident attributable to the vendor
- [ ] Strategic decision to in-source or change provider
- [ ] Vendor concentration risk exceeds tolerance threshold

---

## 4. Data Extraction & Portability

| Data category | Format | Export method | Retention after exit |
|--------------|--------|---------------|---------------------|
| **[FILL: e.g. Customer records]** | **[FILL: CSV/JSON/SQL dump]** | **[FILL: API / admin console / support request]** | **[FILL: X years per regulation]** |
| **[FILL: e.g. Transaction logs]** | **[FILL]** | **[FILL]** | **[FILL]** |
| **[FILL: e.g. Configuration/IaC]** | **[FILL]** | **[FILL]** | **[FILL]** |

### 4.1 Data Deletion Confirmation

Upon completion of the exit, the institution shall:

1. Request written confirmation from the vendor that all institution data
   has been securely deleted from their systems (including backups).
2. Retain this confirmation for a minimum of **5 years**.

---

## 5. Alternative Provider Assessment

| Criterion | Current vendor | Alternative 1 | Alternative 2 |
|-----------|---------------|----------------|----------------|
| Service name | **[FILL]** | **[FILL]** | **[FILL]** |
| Geographic presence (EU) | **[FILL]** | **[FILL]** | **[FILL]** |
| Compliance certifications | **[FILL]** | **[FILL]** | **[FILL]** |
| Data residency guarantees | **[FILL]** | **[FILL]** | **[FILL]** |
| Migration effort estimate | N/A | **[FILL]** | **[FILL]** |
| Estimated transition cost | N/A | **[FILL]** | **[FILL]** |

---

## 6. Transition Timeline

| Phase | Duration | Activities | Owner |
|-------|----------|-----------|-------|
| **1. Preparation** | **[FILL: e.g. 1 month]** | Contract review, alternative vendor selection, migration plan | Procurement + IT |
| **2. Parallel run** | **[FILL: e.g. 2 months]** | Deploy alternative service, mirror traffic/data, validate | IT Operations + Dev |
| **3. Migration** | **[FILL: e.g. 1 month]** | Data migration, DNS/config cutover, integration testing | Dev + QA |
| **4. Validation** | **[FILL: e.g. 2 weeks]** | Regression testing, performance validation, user acceptance | QA + Business |
| **5. Decommission** | **[FILL: e.g. 2 weeks]** | Terminate old service, confirm data deletion, archive logs | IT Ops + Compliance |

**Total estimated transition time**: **[FILL]**

---

## 7. Contractual Cooperation Requirements (Art. 28(8))

The ICT third-party service provider's contract shall include provisions for:

- [ ] Adequate transition period (not less than the time needed to complete
      the exit plan)
- [ ] Continued service delivery during transition at agreed SLA levels
- [ ] Full cooperation in data migration (API access, export tools, support)
- [ ] Return or secure deletion of all institution data upon completion
- [ ] No impediments to migration (no data lock-in, no proprietary formats
      without export capability)
- [ ] Reasonable assistance fees (if any) documented in advance

---

## 8. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Data loss during migration | **[FILL]** | **[FILL]** | Parallel run + integrity checksums |
| Extended downtime during cutover | **[FILL]** | **[FILL]** | Blue-green deployment, rollback plan |
| Vendor refuses cooperation | **[FILL]** | **[FILL]** | Contractual clauses (§7), legal escalation |
| Alternative provider onboarding delay | **[FILL]** | **[FILL]** | Early engagement, multiple alternatives evaluated |
| Knowledge loss (vendor-specific expertise) | **[FILL]** | **[FILL]** | Documentation, training, knowledge transfer sessions |

---

## 9. Approval & Review

| Action | Responsible | Date |
|--------|------------|------|
| Exit strategy drafted | **[FILL]** | **[FILL]** |
| Reviewed by CISO | **[FILL]** | **[FILL]** |
| Approved by management body | **[FILL]** | **[FILL]** |
| Next review | **[FILL: Date + 12 months]** | — |

---

*This template satisfies the requirements of DORA Art. 28(8) for ICT
third-party exit strategies. Complete one instance per critical or important
ICT vendor. All **[FILL]** placeholders must be populated with
institution-specific information.*
