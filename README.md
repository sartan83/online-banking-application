# DevilsVault — Online Banking

Modernization of the original CSE545 Secure Banking System. The legacy Spring MVC + JSP WAR is preserved for reference; the new stack lives alongside it.

## Stack (new)

| Layer     | Tech |
|-----------|------|
| Backend   | Spring Boot 3.3, Java 17, Spring Security 6 (JWT, stateless), Spring Data JPA, Flyway, HikariCP, springdoc-openapi |
| Frontend  | Vite + React 18 + TypeScript, Tailwind CSS, TanStack Query, React Router, Axios |
| Database  | PostgreSQL 16 |
| Infra     | Docker Compose (Postgres + Redis + MailHog + backend + frontend) |
| CI        | GitHub Actions (build, test, CodeQL, DORA metrics) |

## Repository layout

```
backend/                 Spring Boot REST API
frontend/                Vite + React SPA
infra/docker/            Dockerfiles + compose
.github/workflows/       CI
database_scripts/        [legacy] MySQL schema
src/ WebContent/ pom.xml [legacy] Spring MVC 4 + JSP WAR (kept for reference, slated for removal)
docs/                    Architecture decisions, runbooks (TBD)
```

## Quick start

Requires Docker + Docker Compose.

```bash
cd infra/docker
docker compose up --build
```

Then:

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080/api
- Swagger UI: http://localhost:8080/swagger-ui.html
- MailHog UI: http://localhost:8025

### Local dev without Docker

Backend (requires Java 17 + Maven, Postgres reachable at `jdbc:postgresql://localhost:5432/devilsvault`):

```bash
cd backend
DB_URL=jdbc:postgresql://localhost:5432/devilsvault \
DB_USER=devilsvault DB_PASSWORD=devilsvault \
JWT_SECRET=local-dev-secret-local-dev-secret-local-dev-secret-64-chars \
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

### Running tests

```bash
# Backend
cd backend && mvn -B verify

# Frontend
cd frontend && npm run build
```

## API surface (v0)

| Method | Path                  | Auth   | Purpose                 |
|--------|-----------------------|--------|-------------------------|
| POST   | `/api/auth/register`  | public | Register a new customer |
| POST   | `/api/auth/login`     | public | Obtain JWT              |
| GET    | `/api/accounts`       | bearer | List caller's accounts  |
| POST   | `/api/transfers`      | bearer | Transfer between accounts |

OpenAPI spec served at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`.

## Security

- BCrypt password hashing.
- Stateless JWT (HS256, 64-byte secret via `JWT_SECRET`). Rotate per environment.
- CORS locked to the SPA origin via `CORS_ALLOWED_ORIGINS`.
- Money stored as `NUMERIC(19,4)`; transfers are transactional with balance checks.
- `.gitignore` blocks `*.jks`, `*.key`, `*.pem`, `.env*`, `database.properties`, `smtp.properties`. **Never** commit secrets — use env vars or a secret manager.

## DORA Metrics

Four key DevOps Research and Assessment (DORA) metrics are tracked automatically via GitHub Actions (`.github/workflows/dora.yml`):

- **Deployment Frequency** — how often code is deployed to production.
- **Lead Time for Changes** — time from commit to production deploy.
- **Mean Time to Restore (MTTR)** — time to recover from a failure in production.
- **Change Failure Rate** — percentage of deployments that cause a failure.

The workflow runs weekly (Monday 09:00 UTC) and can be triggered manually via `workflow_dispatch`. Results are written to the GitHub Actions workflow summary.

## Modernization roadmap

See [`docs/modernization-plan.md`](docs/modernization-plan.md) for the full 8-phase plan. Phase 0 + Phase 1 (foundation + Boot 3 skeleton) land in this PR; subsequent phases port the remaining legacy domain (OTP, credit cards, employee/admin portals, authorization workflows) onto the new stack.

## Legacy app

The original Spring MVC 4 + JSP WAR is still at the repo root (`pom.xml`, `src/`, `WebContent/`, `database_scripts/`). It is retained only for reference while the new stack is being built out and will be removed once all flows have been ported.
