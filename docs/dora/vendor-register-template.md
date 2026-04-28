# ICT Third-Party Register Template

DORA Art. 28(4) requires financial entities to maintain a **register of all
contractual arrangements** with ICT third-party service providers, broken
down between those supporting **critical or important functions** and the
rest. The register must be available to the competent authority on request.

## Required fields (per Annex VI of the Implementing Regulation)

| # | Field | Notes |
|---|---|---|
| 1 | Provider legal name & LEI | Legal Entity Identifier (ISO 17442). |
| 2 | Provider country of registration | ISO 3166. |
| 3 | Function supported | Description of the ICT service. |
| 4 | Function classification | "Critical or important function" (CIF) yes/no per Art. 28(2). |
| 5 | Service category | Per ESA categorisation (e.g. cloud IaaS, cloud SaaS, software licensing). |
| 6 | Contract reference | Internal contract ID. |
| 7 | Contract effective date | |
| 8 | Termination notice period | |
| 9 | Sub-contracting allowed? | Yes/No, list of approved sub-contractors. |
| 10 | Data location(s) | Country / region of processing & storage. |
| 11 | Personal data processed? | Yes/No, categories. |
| 12 | Last assessment date | Pre-contractual + last periodic review. |
| 13 | Concentration risk flag | Yes if same provider supports > 1 CIF. |
| 14 | Substitutability | Easy / Difficult / Highly difficult. |
| 15 | Exit-plan reference | Link to exit-strategy doc (see `exit-strategy-template.md`). |

## Sample entries (illustrative — replace with actuals)

| Provider | Function | CIF? | Category | Data location | Substitutability |
|---|---|---|---|---|---|
| Amazon Web Services EMEA SARL | IaaS hosting (production) | Yes | Cloud IaaS | eu-central-1 (Frankfurt) | Difficult |
| GitHub Inc. | Source control + CI/CD | No | SaaS DevOps | EU/US | Easy |
| SonarSource SA | Code-quality analysis (SonarCloud) | No | SaaS DevOps | EU | Easy |
| Twilio SendGrid | Transactional email | No | SaaS communications | EU/US | Easy |

## Maintenance

- Review annually and after any contract amendment.
- Re-classify CIF status if the function's importance changes.
- Submit to competent authority annually (DORA Art. 28(3)).

## Code-side support

Container base images and dependency manifests are tracked separately via
the SBOM produced by DORA-2.5 (CycloneDX). The SBOM is **not** a substitute
for the legal register but provides the technical inventory needed for the
"data location", "sub-contracting", and "concentration risk" fields when
SaaS/PaaS providers are used.
