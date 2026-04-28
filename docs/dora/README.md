# DORA Compliance Package

This directory contains the operational artefacts for working toward
**Digital Operational Resilience Act** (Regulation (EU) 2022/2554) alignment for
this application.

> **Important caveat.** DORA is a governance regulation primarily addressed to
> EU financial entities. Most controls live outside the codebase: board-approved
> policies, signed third-party contracts, ICT risk-management framework,
> tested business-continuity plans, regulator notifications, and (for critical
> entities) Threat-Led Penetration Testing. Code changes alone **cannot make an
> organisation DORA-compliant**. The artefacts in this directory describe how
> the application supports those controls; they are not a substitute for the
> organisation-level governance work.

## Contents

| File | Purpose | Maps to |
|---|---|---|
| [`gap-analysis.md`](./gap-analysis.md) | Current state of every DORA pillar versus the application code, with concrete gaps. | All pillars |
| [`controls-matrix.md`](./controls-matrix.md) | Article-by-article mapping of DORA requirements to controls (existing + planned). | Art. 5–45 |
| [`backlog.md`](./backlog.md) | Prioritised, PR-sized engineering work to close the code-level gaps. | All pillars |
| [`incident-classification.md`](./incident-classification.md) | Severity matrix and reporting timelines per Art. 17–23. | Art. 17–23 |
| [`vendor-register-template.md`](./vendor-register-template.md) | Template for ICT third-party register required by Art. 28. | Art. 28–44 |
| [`exit-strategy-template.md`](./exit-strategy-template.md) | Template per Art. 28(8) for exit plans from critical ICT third parties. | Art. 28(8) |

## Status legend

- **Implemented**: the control is in place and tested in CI.
- **Partial**: scaffolding exists but the control is not enforced or covered.
- **Planned**: explicitly tracked in [`backlog.md`](./backlog.md).
- **Out of scope**: organisation-level control, not implementable in code.

## Updating

When closing a code-level item, update both `controls-matrix.md` and the
relevant section of `gap-analysis.md`. Backlog items are referenced by their
`DORA-x.y` ID across PRs and commits.
