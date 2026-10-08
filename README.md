# Coffold

A gamified personal finance app: your ledger as an RPG character sheet.
Balance is your health bar, budgets are mana, savings goals are quests.

## Stack

| Part | Tech |
|---|---|
| `frontend/` | React 19 + TypeScript, built with Vite |
| `backend/` | Spring Boot 3 (Java 17), Spring Data JPA |
| Database | PostgreSQL 16, schema managed by Flyway migrations |
| Tests | JUnit 5 + Testcontainers (real Postgres in Docker) |

## Run locally

Needs Docker, Java 17 and Node 20+.

```bash
docker compose up -d db                  # Postgres on 127.0.0.1:5434
cd backend && ./mvnw spring-boot:run     # API on :8085
cd frontend && npm install && npm run dev  # app on http://localhost:5174
```

In development the browser only talks to Vite (5174); requests to `/api`
are proxied to Spring Boot, so there is no CORS setup.

## Test

```bash
cd backend && ./mvnw test      # starts a throwaway Postgres in Docker
cd frontend && npm run build   # type-checks and builds
```

## Schema changes

Add a new file `backend/src/main/resources/db/migration/V<n>__what_changed.sql`.
Never edit a migration that has already run anywhere.
