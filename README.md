# KrugWorld

A gamified personal finance app: your ledger as an RPG character sheet.
Balance is your health bar, budgets are mana, savings goals are quests.

## Features

- **Ledger**: accounts (bank, cash, e-wallet), income, spending and
  transfers, categories, monthly summary. Balances are computed from
  entries in SQL, never stored, so they can't drift.
- **Budgets** per category, shown as draining mana bars.
- **Recurring entries** (salary, rent, subscriptions) booked by a nightly
  job that catches up after downtime and can't double-book a date.
- **Game layer**: XP and levels, logging streaks, quests (savings goals),
  and badges, all derived from the real data.
- **Two skins**: *Classic Krugger* (pixel style) and *KrugWorld* (Pirata One
  + Barlow Condensed) with four backgrounds, saved per account.
- **Accounts** with registration and "log in once per device".
- One-off **import from Krugger's Tavern**.

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

## Build and deploy

`.\build.ps1` produces `deploy/krugworld.jar` (React app bundled inside).
See [DEPLOY.md](DEPLOY.md).

## Schema changes

Add a new file `backend/src/main/resources/db/migration/V<n>__what_changed.sql`.
Never edit a migration that has already run anywhere.
