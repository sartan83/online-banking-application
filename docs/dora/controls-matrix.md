# EU DORA Controls Matrix

Tracks implementation status of ICT risk-management controls required by the
Digital Operational Resilience Act (DORA).

| Article | Control area | Status | Evidence / notes |
|---------|-------------|--------|-----------------|
| Art. 5–15 | ICT risk-management framework | Planned | — |
| Art. 16 | Simplified ICT risk-management framework | N/A | Not an exempt entity |
| Art. 17–23 | ICT-related incident management | Planned | — |
| Art. 24 | ICT resilience testing — DAST | **Partial** | OWASP ZAP baseline scan runs in CI on every PR (`.github/workflows/dast.yml`). Fails on HIGH-severity findings. Full authenticated scan and annual pen-test still required. |
| Art. 25 | TLPT (threat-led penetration testing) | Planned | Requires external red-team engagement |
| Art. 28 | ICT third-party risk | **Partial** | CycloneDX SBOMs, Trivy, and OSV-Scanner gates in `supply-chain.yml` |
| Art. 45 | Information sharing | Planned | — |
