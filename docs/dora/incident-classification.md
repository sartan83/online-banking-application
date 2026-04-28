# Incident Classification

Aligned with DORA Art. 18 and the [RTS on classification of major incidents](https://www.eba.europa.eu/regulation-and-policy/operational-resilience/regulatory-technical-standards-classification-major-ict-related-incidents-and-cyber-threats).

## Severity matrix

| Severity | Examples | Trigger thresholds (illustrative) | Reporting timeline |
|---|---|---|---|
| **Critical** | Confirmed unauthorised funds movement; mass account takeover; data exfiltration of PII at scale; full service outage > 30 min during business hours. | ≥ 1 confirmed fraudulent transfer, OR > 10 % of clients affected, OR > 1 M€ economic impact, OR > 30 min downtime during business hours. | Initial notification ≤ 4 h; intermediate ≤ 72 h; final ≤ 1 month. |
| **High** | Repeated brute-force succeeded on a privileged account; persistent DoS; partial outage of payment flow. | 1 confirmed compromised privileged account, OR 1 % – 10 % clients affected, OR 0.1 M€ – 1 M€ impact, OR 10 – 30 min payment downtime. | Internal SOC alert; classify within 24 h; report if it crosses Critical thresholds. |
| **Medium** | Anomalous auth activity contained by rate-limiting; transient backend errors; minor data integrity issue. | < 1 % clients affected, < 0.1 M€ impact, < 10 min outage. | SOC ticket; weekly trend review. |
| **Low** | Single-user transient errors; expected security alerts (e.g. expected high traffic). | None. | Logged in audit trail; no further action. |

## Classification criteria (DORA Art. 18 RTS)

The following criteria must be evaluated for **every** incident; if any
threshold is met, classify as **Major** and trigger Art. 19 notification.

1. **Clients, financial counterparts, transactions affected** (number / value).
2. **Reputational impact** (media coverage, complaints volume).
3. **Duration & service downtime**.
4. **Geographical spread** (cross-border).
5. **Data losses** (confidentiality, integrity, availability of PII or
   transaction data).
6. **Criticality of services affected** (payment vs. read-only).
7. **Economic impact** (direct losses + recovery costs + reputational).

## Reporting timeline (DORA Art. 19)

| Phase | Deadline | Content |
|---|---|---|
| **Initial notification** | within **4 hours** of classifying as major (and at most **24 hours** from detection). | Detection time, classification, services affected, preliminary impact. |
| **Intermediate report** | within **72 hours** of initial notification. | Updated impact, root cause hypothesis, response actions taken. |
| **Final report** | within **1 month** of initial notification. | Confirmed root cause, full impact assessment, remediation, lessons learned. |

## Code-side hooks

The audit-event log (DORA-2.2) captures every classifiable event with the
fields needed to satisfy the RTS reporting templates:

- `event_type` (auth.success, auth.failure, transfer.completed, …)
- `actor_id`, `actor_ip`, `correlation_id`
- `resource_type`, `resource_id`
- `before_hash`, `after_hash` (data-change events)
- `outcome` (success / failure / blocked)
- `classification` (set by incident-handler when promoted to incident)

When an event is promoted to an incident, the incident-handler service
writes a row to `incident` referencing the originating `audit_event` IDs and
runs the classification matrix above to assign severity.

## Out of scope

- Notifying the competent authority (CSSF / CONSOB / BaFin / etc.) is the
  operating entity's obligation.
- Customer notifications (Art. 19(3)) require legal review.
- Inter-entity information sharing (Art. 45) is voluntary.
