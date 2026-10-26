# KrugWorld

A gamified life app, the successor to Krugger's Tavern: your money, days,
meals and training as an RPG character sheet. Balance is your health bar,
budgets are mana, savings goals are quests, and everything you do earns XP.

## Features

- **Ledger**: accounts (bank, cash, e-wallet), income, spending and
  transfers, categories, monthly summary. Balances are computed from
  entries in SQL, never stored, so they can't drift.
- **Budgets** per category, shown as draining mana bars.
- **Recurring entries** (salary, rent, subscriptions) booked by a nightly
  job that catches up after downtime and can't double-book a date, or held
  for you to confirm (and correct the amount) when the bill varies.
- **Debts**: who owes you and whom you owe, repaid in parts, with due dates.
- **Calendar** with a month view and timed or all-day events.
- **Vitals**: meals against a daily calorie goal with a 7-day trend, and a
  weekday training plan whose streaks only count planned days.
- **Workshop**: an idea board from pinned to in progress to done.
- **The Steward**: an AI helper (Claude or OpenAI) that acts on all of the
  above through 29 tools, under a per-user daily allowance.
- **Oracle's Eye**: Anthropic usage and cost by day, using an admin key that
  stays in the browser.
- **Hall**: your character, money and debts side by side, bills awaiting
  your confirmation, today at a glance, and the weather.
- **Game layer**: XP and levels, activity streaks, quests (savings goals),
  and 12 badges, all derived from the real data.
- **Two skins**: *Classic Krugger* (pixel style) and *KrugWorld* (Pirata One
  + Barlow Condensed) with four backgrounds, saved per account.
- **Accounts** with registration and "log in once per device".
- One-off **import of everything from Krugger's Tavern**.

## Documentation

Full documentation is in [docs/](docs/README.md): features and game rules,
architecture, database, API reference, Gaby, development and operations.

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

Add a new file `backend/src/main/resources/db/migration/V<n>__what_changed.sql`,
numbered above production's latest migration. Never edit a migration that has
already run anywhere. See [docs/DATABASE.md](docs/DATABASE.md).
