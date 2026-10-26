# KrugWorld documentation

KrugWorld is a gamified personal life app styled as an RPG character sheet: money,
calendar, meals, training and ideas all earn XP, and the AI steward **Gaby** can act on
all of it, in the browser or on a desk device.

| Document | Read it when you want to… |
|---|---|
| [FLOWS.md](FLOWS.md) | see it in pictures: an index of every diagram, plus sign-in, password reset, pairing and import flows |
| [FEATURES.md](FEATURES.md) | know what every screen does, and the game rules (XP, levels, streaks, badges) |
| [ARCHITECTURE.md](ARCHITECTURE.md) | understand how the pieces fit: packages, layers, events, security, frontend |
| [DATABASE.md](DATABASE.md) | look up a table, the migrations, or the rules for changing the schema |
| [API.md](API.md) | call the browser API: every endpoint, body, response and error code |
| [GABY.md](GABY.md) | understand or extend the AI steward: tools, providers, limits, voice |
| [DEVICE-API.md](DEVICE-API.md) | work on the desk device (ESP32) and its API |
| [DEVELOPMENT.md](DEVELOPMENT.md) | run it locally, run the tests, add a feature the house way |
| [SETUP-NEW-PC.md](SETUP-NEW-PC.md) | set up a fresh machine |
| [OPERATIONS.md](OPERATIONS.md) | release, check, back up, roll back or fix the live site |
| [../DEPLOY.md](../DEPLOY.md) | set the server up for the first time |

## At a glance

- **Live:** https://krugworld.veteryr.online
- **Stack:** Spring Boot 3.3 (Java 17) + PostgreSQL 16 (Flyway) + React 19 / TypeScript /
  Vite, shipped as one jar in Docker behind nginx.
- **Tests:** backend integration tests against real Postgres (Testcontainers); frontend
  lint at zero warnings; CI on every push.
- **Design ideas worth knowing:** balances and debts are summed from history, never stored;
  rewards are idempotent through a unique `(user, kind, ref)` key; every lookup is scoped
  to the user; the nightly job can't double-book; Gaby's tools go through the same
  services as the screens.

## Known gap

On 2026-10-22, four releases were deployed from another PC and aren't in this repository
yet: **push notifications** (`V10__push.sql`), **meal money**, **one language** and
**push urgency**. Once that code is merged, document them in FEATURES, API and DATABASE
(DATABASE.md already reserves the V10 row).
