# ICT-Related Incident Classification Matrix

EU DORA Art. 18 — classification criteria for ICT-related incidents and
notification deadlines per Art. 19.

---

## 1. Classification Criteria (Art. 18)

Each ICT-related incident is assessed across five dimensions. A single
**Critical** rating in any dimension elevates the entire incident to
**Major**.

### 1.1 Significance

| Level | Criteria |
|-------|----------|
| **Critical** | Complete unavailability of a critical or important function for > 2 h, or any incident affecting > 10 % of clients |
| **High** | Degraded performance of a critical function (> 50 % throughput loss) or unavailability for 30 min–2 h |
| **Medium** | Minor degradation of a non-critical function; workaround available |
| **Low** | No user-facing impact; detected proactively |

### 1.2 Critical Services Affected

| Level | Services |
|-------|----------|
| **Critical** | Core banking (payments, account access, transfers), authentication/authorisation |
| **High** | Reporting, audit trail, transaction history |
| **Medium** | Non-customer-facing internal tools, dev/staging environments |
| **Low** | Monitoring dashboards, documentation sites |

### 1.3 Data Loss Type

| Level | Type |
|-------|------|
| **Critical** | Customer PII exfiltrated, financial data compromised, authentication credentials leaked |
| **High** | Internal data exposed (employee records, internal configs with secrets) |
| **Medium** | Non-sensitive data exposed (public-facing content, anonymised logs) |
| **Low** | No data loss or exposure |

### 1.4 Geographic Spread

| Level | Spread |
|-------|--------|
| **Critical** | Multiple EU member states affected; cross-border impact |
| **High** | Single member state, multiple regions / data centres |
| **Medium** | Single region / data centre |
| **Low** | Single host or isolated component |

### 1.5 Reputational Impact

| Level | Impact |
|-------|--------|
| **Critical** | Media coverage, regulatory inquiry, client attrition expected |
| **High** | Social-media attention, formal client complaints |
| **Medium** | Internal escalation, limited external awareness |
| **Low** | No external visibility |

---

## 2. Decision Tree

```
START
  │
  ├─ Is a critical or important function unavailable for > 2 h?
  │   YES → MAJOR incident → Go to §3 Notification
  │   NO  ↓
  │
  ├─ Is customer PII or financial data compromised?
  │   YES → MAJOR incident → Go to §3 Notification
  │   NO  ↓
  │
  ├─ Are > 10 % of clients affected?
  │   YES → MAJOR incident → Go to §3 Notification
  │   NO  ↓
  │
  ├─ Does the incident affect multiple EU member states?
  │   YES → MAJOR incident → Go to §3 Notification
  │   NO  ↓
  │
  ├─ Is media coverage likely or has it already occurred?
  │   YES → Escalate to CISO; assess as potential MAJOR
  │   NO  ↓
  │
  ├─ Any single dimension rated HIGH?
  │   YES → SIGNIFICANT incident → Internal escalation
  │   NO  → STANDARD incident → Handle per normal IR process
```

---

## 3. Notification Deadlines (Art. 19)

Major ICT-related incidents must be reported to the competent authority
(and, where applicable, affected clients) per the following timeline:

| Report | Deadline | Content |
|--------|----------|---------|
| **Initial notification** | **4 hours** after classification as major (no later than 24 h after detection) | Incident reference, detection time, classification rationale, initial impact assessment, first containment actions |
| **Intermediate report** | **72 hours** after initial notification | Updated impact assessment, root-cause analysis (preliminary), remediation actions taken, services restored/still affected |
| **Final report** | **1 month** after intermediate report | Confirmed root cause, full timeline, total impact (financial, data, reputational), lessons learned, preventive measures implemented |

### 3.1 Voluntary Notification

Significant cyber threats (Art. 19(2)) may be voluntarily reported to the
competent authority even if they did not result in a major incident. This
supports information sharing (Art. 45).

### 3.2 Client Notification

If a major incident has or is likely to have an impact on the financial
interests of clients, the institution shall inform affected clients without
undue delay, including the nature of the incident and measures they can take
to mitigate adverse effects.

---

## 4. Severity-to-Response Mapping

| Severity | Response lead | Comms cadence | Escalation |
|----------|--------------|---------------|------------|
| **MAJOR** | Incident Commander (on-call) | Every 30 min internally; external per §3 | Board + competent authority |
| **SIGNIFICANT** | CISO / Security team lead | Every 2 h internally | CTO + Compliance |
| **STANDARD** | On-call engineer | End-of-incident summary | Team lead |

---

## 5. Post-Incident Review

All MAJOR and SIGNIFICANT incidents require a post-incident review within
**5 business days** of resolution. The review must produce:

1. Root-cause analysis (5-whys or equivalent).
2. Timeline with detection, escalation, and resolution milestones.
3. Gap analysis against existing controls.
4. Remediation plan with owner and deadline for each action item.
5. Update to the risk register if new risks are identified.

---

*This classification matrix implements the requirements of DORA Art. 18–19.
It must be reviewed annually and after any major incident to ensure thresholds
remain appropriate.*
