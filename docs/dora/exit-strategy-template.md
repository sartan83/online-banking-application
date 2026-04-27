# ICT Third-Party Exit Strategy Template

DORA Art. 28(8) requires financial entities to put in place **exit
strategies** for ICT services supporting critical or important functions
(CIF). Exit strategies must be:

- Documented and tested.
- Sufficient to allow the entity to **exit without disruption** to business.
- Compliant with regulatory obligations during the transition.

This template is to be filled per CIF provider listed in
`vendor-register-template.md`.

## Provider

| Field | Value |
|---|---|
| Provider name | |
| Function supported | |
| CIF? | Yes |
| Contract reference | |
| Last exit-test date | |

## Trigger events

Document conditions that activate the exit plan:

- [ ] Provider material breach of contract.
- [ ] Provider insolvency.
- [ ] Service unavailability beyond agreed SLA for **N** consecutive days.
- [ ] Material adverse change in provider's compliance posture.
- [ ] Termination at financial entity's discretion.
- [ ] Regulatory direction.

## Migration scenarios

For each, specify target alternative, RPO, RTO, owner, and steps.

### Scenario A — Migrate to alternative provider
- **Target**: <name of pre-qualified alternative>.
- **RPO / RTO**: e.g. RPO 15 min, RTO 4 h.
- **Steps**:
  1. Activate alternative provider contract (pre-signed shell agreement).
  2. Replicate data using documented format (e.g. Postgres logical dump,
     CycloneDX SBOM for redeployment).
  3. Cut DNS / traffic over.
  4. Decommission original provider; delete data per contract.

### Scenario B — Bring in-house
- **Target**: internal infrastructure team.
- **RPO / RTO**: e.g. RPO 1 h, RTO 24 h.
- **Steps**:
  1. Provision on-prem / private-cloud capacity.
  2. Restore from latest off-site backup.
  3. Re-issue secrets and rotate keys.
  4. Reroute traffic, decommission provider.

### Scenario C — Wind-down (only if function can be paused)
- Conditions, communications plan, regulatory notifications, customer impact.

## Data portability

- **Format**: e.g. SQL dump, CSV, Parquet, ISO-20022 messages.
- **Schema documentation**: `docs/dora/data-schema.md` (to be added).
- **Test cadence**: extract test runs at least annually; verify a sample
  restore in a non-prod environment.

## Knowledge transfer

- Runbooks, configuration files, infra-as-code stored in this repo (`infra/`)
  and exportable without provider-specific dependencies.
- Identify any **sole-knowledge** holders and cross-train.

## Communication plan

| Stakeholder | Channel | Owner | When |
|---|---|---|---|
| Customers | In-app + email | Comms lead | Per scenario |
| Regulator | Formal notice | Compliance | Within 30 days of trigger |
| Internal staff | All-hands | Engineering lead | Day 0 |
| Sub-contractors | Email | Procurement | Day 0 |

## Testing

- **Tabletop exercise**: at least annually.
- **Functional drill**: at least once before contract renewal.
- Record results, update plan accordingly.

## Sign-off

| Role | Name | Date |
|---|---|---|
| Business owner | | |
| ICT risk owner | | |
| CISO | | |
| Compliance | | |
