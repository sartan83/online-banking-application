# Modernization Plan — Online Banking Application

Repo: https://github.com/ajkulkarni/online-banking-application
Local path: `/home/ubuntu/repos/online-banking-application`

## 1. Current State (what the repo actually is)

An academic-era (CSE545) **Spring MVC + JSP + MySQL** web app packaged as a WAR for Tomcat.

**Stack / conventions**
- Java **1.7**, Maven WAR, `packaging=war`, `sourceDirectory=src` (non-standard; not `src/main/java`).
- Spring **4.3.3** (`spring-core`, `spring-webmvc`, `spring-jdbc`), Spring Security **4.0.3**, Hibernate Validator 5.2, JSTL 1.2, Servlet API **2.5**, JUnit 4.12, `javax.mail` 1.5, `javax.json` 1.0, `commons-codec` 1.9.
- MySQL Connector **5.1.9**, raw JDBC via `DriverManagerDataSource` + hand-written DAOs (no ORM). Schema in `database_scripts/devilsvault_internal.sql`.
- Views: **JSPs** under `WebContent/WEB-INF/{customerPages,employeePages}` + global login/OTP JSPs.
- Security: form login, BCrypt, CSRF, session fixation protection, max-sessions=1, custom `LimitLoginAuthenticationProvider`, reCAPTCHA, OTP over SMTP, keystore `mykeystore.jks` checked into `src/main/resources` (must be removed from VCS).
- Config via `database.properties`, `smtp.properties`, XML bean wiring (`spring-dispatcher-servlet.xml`, `spring-security.xml`, `DaoDetails.xml`), `web.xml`.
- Domain: internal/external users, accounts (checking/savings/credit), transactions, requests/approvals, OTP, logs, merchant payments, authorization.
- **No CI, no tests of substance, no Docker, no migrations tool, no API layer** — all server-rendered.

### Key risks / smells
- **Severely outdated & EOL dependencies** (Spring 4.x EOL, Spring Security 4.x EOL, MySQL connector 5.1.x, Servlet 2.5, Java 7). Multiple known CVEs.
- `DriverManagerDataSource` (not a pool) in production wiring.
- Secrets and `keystore.jks` committed; `database.properties`/`smtp.properties` likely contain creds.
- SQL written by hand across many DAOs → inconsistent, injection-prone surface area to audit.
- JSP monolith — no API, no mobile/SPA story, hard to test.
- No automated tests, no CI, no observability.

---

## 2. Target Architecture (modern)

A **Spring Boot 3.x backend + React/TypeScript SPA**, containerized, CI/CD-driven, cloud-deployable.

```
┌──────────────────────────────┐       ┌─────────────────────────────┐
│  React + TS + Vite SPA       │◀─────▶│  Spring Boot 3.x REST API   │
│  TanStack Query, Zod, shadcn │  HTTPS│  Java 21, JPA, Flyway, JWT  │
└──────────────────────────────┘       └──────────────┬──────────────┘
                                                      │
                                ┌─────────────────────┼─────────────────────┐
                                ▼                     ▼                     ▼
                           PostgreSQL           Redis (sessions,       SMTP / Email
                           (or MySQL 8)          rate-limit, OTP)       provider
                                                      │
                                                      ▼
                                          OpenTelemetry → Grafana/Prom
```

Principles: API-first, stateless services, 12-factor config, migrations-as-code, secrets out of repo, tests & CI gating, containerized deploys.

---

## 3. Step-by-Step Modernization Roadmap

Phased so each step is shippable and reversible.

### Phase 0 — Safety net & hygiene (0.5–1 week)
1. **Remove secrets from history**: purge `mykeystore.jks`, `database.properties`, `smtp.properties` using `git filter-repo`; rotate any real credentials; add `.gitignore` entries.
2. Add `.editorconfig`, a Java formatter (Spotless + google-java-format), `.gitattributes`.
3. Add **GitHub Actions CI** that at minimum: builds, runs tests, runs dependency scan (Dependabot + OWASP Dependency-Check or Trivy).
4. Add a baseline `README` with run-from-scratch Docker instructions.
5. Snapshot current behavior: write a short **end-to-end smoke test** (Playwright or REST-Assured once an API exists) describing the core flows — login, transfer, OTP, approval.

### Phase 1 — Build & language upgrade on the existing app (1–2 weeks)
Goal: same WAR, modern toolchain, no behavior change.
1. Move sources to standard Maven layout: `src/main/java`, `src/main/resources`, `src/main/webapp` (from `WebContent`). Update `pom.xml` accordingly.
2. Bump to **Java 21** (LTS). Remove `source/target 1.7`.
3. Bump Spring to **5.3.x** as an intermediate step; fix deprecations.
4. Replace `mysql-connector-java:5.1.9` → `com.mysql:mysql-connector-j:8.x` (or `org.postgresql:postgresql` when we migrate).
5. Replace `DriverManagerDataSource` with **HikariCP**.
6. Introduce **Flyway**; baseline from `devilsvault_internal.sql` as `V1__init.sql`.
7. Externalize all config to env vars (`${DB_URL}`, `${SMTP_HOST}`, etc.) via `${...}` placeholders; no more checked-in `.properties` with secrets.
8. Add **Testcontainers** + JUnit 5 and write integration tests for auth, transfer, OTP happy paths.

### Phase 2 — Spring Boot migration (2–3 weeks)
Goal: replace XML + WAR with Spring Boot 3.x fat jar; JSPs still work.
1. Create a `spring-boot-starter-parent` POM (Boot 3.3+, Jakarta EE).
2. Replace `web.xml` + XML bean config with `@SpringBootApplication` + `@Configuration` classes.
3. Migrate `javax.*` → `jakarta.*` (Servlet, Mail, Validation, JSON). This is the big one — use `org.openrewrite` recipes (`org.openrewrite.java.migrate.JavaxMigrationToJakarta`).
4. Replace Spring Security XML with a Java `SecurityFilterChain` config; keep BCrypt, CSRF, session management, form-login intact. Upgrade to Spring Security 6.x.
5. Replace hand-rolled JDBC DAOs with **Spring Data JPA** repositories + JPA entities for the ~15 models (`Customer`, `InternalUser`, `BankAccount`, `Transaction`, `Request`, `OTP`, `UserAuthentication`, …). Keep raw JDBC only where a query is genuinely complex.
6. Keep JSPs temporarily (`spring-boot-starter-tomcat` + jasper) to de-risk; delete as SPA takes over.
7. Introduce **Lombok** or Java records, `@Valid` DTOs, `@ControllerAdvice` global error handler, structured logging with Logback JSON.

### Phase 3 — Expose a REST API (1–2 weeks)
Goal: every user action callable as JSON; JSPs become clients of the API internally.
1. Design REST endpoints around domain verbs: `/api/auth/login`, `/api/auth/otp/verify`, `/api/accounts`, `/api/transfers`, `/api/requests`, `/api/admin/users`, `/api/merchants/authorize`, `/api/payments`.
2. Document with **springdoc-openapi** → `/swagger-ui`.
3. Switch session auth to **stateless JWT access tokens + refresh tokens** (or stick with HttpOnly session cookies + SameSite=strict if you prefer server sessions — pick one and document the tradeoff). Keep CSRF strategy consistent with the choice.
4. Rate-limit login/OTP endpoints (Bucket4j + Redis).
5. Move OTP/session storage to **Redis** so the app becomes horizontally scalable.

### Phase 4 — Modern frontend (3–5 weeks)
Goal: replace JSPs with a SPA.
1. Scaffold **Vite + React + TypeScript**, Tailwind + shadcn/ui, TanStack Router, TanStack Query, Zod, React Hook Form.
2. Generate a typed API client from the OpenAPI spec (`openapi-typescript` or `orval`).
3. Rebuild screen-by-screen, starting with the auth/OTP flow, then customer dashboard, transfers, credit, merchant payments; employee/admin portals last.
4. Delete each JSP only once the SPA replacement is behind a feature flag and verified.
5. Serve the SPA either as static files from the Boot app (simple) or from a CDN (Cloudflare/S3+CloudFront) with the API on its own host.

### Phase 5 — Database & data quality (1–2 weeks, can parallelize)
1. Decide **Postgres vs MySQL 8**. Recommend **Postgres** (richer types, better constraints, wider tooling). Migrate with pgloader if chosen.
2. Harden schema: foreign keys everywhere, `DECIMAL(19,4)` for money (never `FLOAT`), `NOT NULL` defaults, check constraints (`balance >= 0` where applicable), proper indexes on `user_id`, `account_id`, `timestamp`.
3. Convert DDL entirely to Flyway migrations; no more ad-hoc SQL script.
4. Add soft-delete / audit columns (`created_at`, `updated_at`, `created_by`) consistently.

### Phase 6 — Security hardening (ongoing, finalize here) (1–2 weeks)
1. TLS terminated at the load balancer; HSTS; secure cookies; SameSite=strict; CSP; X-Content-Type-Options; Referrer-Policy.
2. Replace custom reCAPTCHA flow with **reCAPTCHA v3** or **hCaptcha** behind a pluggable interface.
3. Replace custom OTP with **TOTP (RFC 6238)** via authenticator apps, keep email OTP as fallback.
4. Centralize AuthN with **Keycloak / Auth0 / AWS Cognito** (OIDC) so you stop owning password storage long-term.
5. Add **Spring Security method-level authorization** (`@PreAuthorize`) alongside URL rules.
6. Secrets in **Vault / AWS Secrets Manager / Doppler**; never in repo.
7. SAST (**SonarCloud** or **CodeQL**) + DAST (**OWASP ZAP baseline scan**) in CI; fail the build on high-severity findings.
8. Pen-test checklist: OWASP ASVS L2 for a banking app.

### Phase 7 — DevOps, observability, deploy (1–2 weeks)
1. **Dockerfile** (multi-stage, distroless or `eclipse-temurin:21-jre`), `docker-compose.yml` for local dev (app + Postgres + Redis + MailHog).
2. **GitHub Actions**: build → test → scan → publish image to GHCR/ECR.
3. **Kubernetes** manifests or **Fly.io / Render / AWS ECS** for deploy; blue-green or rolling.
4. **OpenTelemetry** instrumentation → Grafana Cloud / Datadog / Honeycomb; structured logs; RED/USE dashboards; alerting on error rate, p95 latency, failed logins.
5. Backups & PITR for the database; disaster-recovery runbook.

### Phase 8 — Testing discipline (integrated across phases)
- Unit: JUnit 5 + Mockito on services.
- Integration: **Testcontainers** (Postgres, Redis, MailHog) for every repo/controller.
- Contract: `springdoc` spec + frontend generated client keeps drift out.
- E2E: **Playwright** against a seeded Dockerized stack, run in CI.
- Load: **k6** smoke on transfer + login endpoints.

---

## 4. Suggested Repo Structure (end state)

```
/
├── backend/                    # Spring Boot 3 service
│   ├── src/main/java/com/devilsvault/...
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/       # Flyway
│   └── pom.xml                 # or build.gradle.kts
├── frontend/                   # React + TS SPA
│   ├── src/
│   └── package.json
├── infra/
│   ├── docker/                 # Dockerfile(s), compose
│   ├── k8s/                    # manifests or Helm chart
│   └── terraform/              # cloud infra
├── .github/workflows/          # CI/CD
└── docs/                       # ADRs, API docs, runbooks
```

---

## 5. Sequencing & Effort (rough)

| Phase | Focus | Effort |
|------|-------|--------|
| 0 | Secrets purge + CI baseline | 0.5–1 wk |
| 1 | Java 21 + Spring 5 + Flyway + Hikari | 1–2 wks |
| 2 | Spring Boot 3 + Jakarta + JPA | 2–3 wks |
| 3 | REST API + OpenAPI + Redis | 1–2 wks |
| 4 | React/TS SPA replaces JSPs | 3–5 wks |
| 5 | DB migration + schema hardening | 1–2 wks (parallel) |
| 6 | Security hardening + OIDC | 1–2 wks |
| 7 | Docker + K8s + observability | 1–2 wks |
| 8 | Test pyramid (continuous) | ongoing |

**Solo dev, realistic total: ~3 months**. Small team: ~6 weeks.

---

## 6. Quick Wins You Can Ship This Week

1. `git filter-repo` to drop `mykeystore.jks` + `*.properties`; rotate creds.
2. Bump `mysql-connector-java` → `mysql-connector-j:8.4.0` and Java → 17 (minimum).
3. Replace `DriverManagerDataSource` with HikariCP (one XML change).
4. Add Dependabot + a CodeQL workflow — costs nothing, surfaces real CVEs.
5. Add a `Dockerfile` + `docker-compose.yml` so contributors stop needing Eclipse + Tomcat + local MySQL.

---

## 7. Open Questions for You

1. Target deployment: **cloud (AWS/GCP/Azure) vs on-prem**? Drives Phase 7.
2. Keep **MySQL** or migrate to **Postgres**?
3. Do you want to **preserve session-cookie auth** or switch to **JWT/OIDC**?
4. Is this a real product or a portfolio/learning exercise? (Affects how much Phase 6 you actually need.)
5. Team size & timeline constraints?

Answering 1–4 lets me turn this into a concrete sprint plan and start executing.
