# Architecture

KrugWorld is one Spring Boot application that serves both the JSON API and the
built React app, backed by one PostgreSQL database. A desk device (an ESP32 "desk
Gaby") talks to the same server through its own small API.

```mermaid
flowchart TB
    subgraph clients["Clients"]
        browser["🖥️ Browser<br/>React SPA · installable PWA"]
        desk["🔊 Desk Gaby<br/>ESP32-S3 · touch + mic + speaker"]
    end

    subgraph edge["Lighthouse server"]
        nginx["nginx<br/>TLS · krugworld.veteryr.online"]
        subgraph app["Spring Boot app"]
            direction TB
            sec{"Security<br/>which chain?"}
            static["Static files<br/>built React app"]
            ctrl["Controllers<br/>thin · @Valid records"]
            svc["Services<br/>rules · transactions"]
            events(["Domain events"])
            rewards["game.Rewards<br/>XP · badges"]
            steward["Steward · Gaby<br/>tool-use loop"]
            job["⏰ RecurringJob<br/>00:05 daily + on startup"]
        end
        db[("PostgreSQL 16<br/>Flyway-managed")]
    end

    ai["☁️ OpenAI · or Anthropic<br/>chat · speech-to-text · TTS"]
    meteo["☁️ Open-Meteo<br/>weather, from the browser"]

    browser -- "session cookie + CSRF" --> nginx
    desk -- "Bearer device token" --> nginx
    nginx -- "127.0.0.1:8096" --> sec
    sec -- "/api/device/**" --> ctrl
    sec -- "/api/** browser chain" --> ctrl
    sec -- "anything else" --> static
    ctrl --> svc
    svc -- "JPA + JdbcTemplate" --> db
    svc -. publishes .-> events
    events -. listened by .-> rewards
    rewards -- "INSERT … ON CONFLICT DO NOTHING" --> db
    ctrl --> steward
    steward -- "tools call the same services" --> svc
    steward <--> ai
    job --> svc
    browser -.-> meteo
```

## Why one jar

Shipping the frontend inside the backend jar means one artifact, one container, one
port, and no CORS: the app and the API share an origin, so the session cookie and the
CSRF cookie just work. In development Vite serves the frontend and proxies `/api` to
Spring, which keeps the same single-origin shape.

## Backend

Spring Boot 3.3 on Java 17. Each package is one feature and holds its own controller,
service, entities and repositories:

| Package | What it owns |
|---|---|
| `auth` | Users, login/registration, remember-me, preferences, password reset by email |
| `ledger` | Accounts, categories, ledger entries, month summaries |
| `budget` | Monthly budgets per expense category ("mana") |
| `recurring` | Monthly recurring entries, the nightly job, confirm/skip |
| `debt` | Debts between the user and other people, repayments |
| `quest` | Savings goals ("quests") |
| `game` | XP log, levels, badges, streaks, the character sheet |
| `calendar` | Calendar events |
| `vitals` | Meals/calories and the training plan |
| `workshop` | The idea board |
| `life` | "Today" aggregate (meals, workouts, upcoming events, ideas) |
| `steward` | Gaby: chat providers, tools, usage limits, speech |
| `device` | Desk device pairing, device API, Gaby's daily line |
| `importer` | One-off import from Krugger's Tavern |
| `web` | Forwards SPA routes to `index.html` |
| `status` | `/api/status` health and schema version |
| `common` | API errors, clock/time zone, scheduling switch |

### Life of a request

What happens when you log a RM 12.50 nasi lemak:

```mermaid
sequenceDiagram
    autonumber
    actor U as You
    participant R as React
    participant Q as Query cache
    participant S as Security
    participant C as Controller
    participant L as LedgerService
    participant DB as Database
    participant RW as Rewards

    U->>R: amount 12.50, Food, "Nasi lemak"
    R->>Q: useSaveEntry().mutate
    Q->>S: POST /api/entries (via nginx)
    S->>S: session valid? CSRF matches?
    S->>C: CurrentUser principal
    C->>C: @Valid EntryRequest
    C->>L: createEntry(userId, req)
    L->>DB: account and category belong to this user?
    L->>DB: INSERT ledger_entry
    L-)RW: publish EntryLogged
    RW->>DB: fewer than 10 entry rewards today?
    RW->>DB: INSERT xp_event … ON CONFLICT DO NOTHING
    L-->>C: EntryView
    C-->>Q: 201 Created
    Q->>Q: invalidate 6 money queries
    Q->>C: refetch what's on screen
    Q-->>R: fresh data
    R-->>U: toast "Logged · +5 XP"
```

### Layers and conventions

```mermaid
flowchart LR
    subgraph web["Web layer"]
        C["Controller<br/>reads principal<br/>validates body"]
    end
    subgraph domain["Domain layer"]
        S["Service<br/>userId first<br/>@Transactional<br/>throws ApiException"]
        V["Records<br/>XxxRequest · XxxView"]
    end
    subgraph data["Data layer"]
        R["JPA repository<br/>findByIdAndUserId"]
        J["JdbcTemplate<br/>sums · aggregates"]
    end
    H["ApiExceptionHandler<br/>{code, message, fields}"]

    C --> S
    S --- V
    S --> R
    S --> J
    S -. "rule broken" .-> H
    C -. "validation failed" .-> H
```

- **Controllers** are thin: they read the principal, validate the body (`@Valid` on
  records) and call one service method.
- **Services** own the rules and the transaction (`@Transactional` on the class).
  Every method takes the user id first, and every lookup by id also filters by user
  (`findByIdAndUserId`), so another user's row looks exactly like a missing one (404).
- **Request/response shapes** are Java records next to the service
  (`DebtService.DebtRequest`, `DebtService.DebtView`). Entities never leave the service.
- **JPA** handles single-row writes; **JdbcTemplate** handles aggregates (balances,
  totals by category, repaid amounts), where plain SQL is clearer and faster than
  loading entities.
- **Money** is `NUMERIC(14,2)` in the database and `BigDecimal` in Java. The frontend
  sends amounts as two-decimal strings, so no floating-point rounding reaches the
  database.
- **Derived numbers are never stored.** Account balances are opening balance plus
  entries; a debt's outstanding amount is the amount minus repayments. Both are
  summed in SQL on read, so they can't drift from the history.
- **Errors** are always `{ "code", "message", "fields"? }` (`ApiExceptionHandler`).
  Services throw `ApiException.badRequest / notFound / conflict`; the frontend switches
  on `code` and shows `message`.

### Domain events and rewards

Features don't call the game directly. They publish a small event and `game.Rewards`
listens:

```mermaid
flowchart TD
    ledger["LedgerService<br/>RecurringService.confirm"] -- EntryLogged --> R
    vitals1["VitalsService · meal"] -- MealLogged --> R
    vitals2["VitalsService · workout"] -- WorkoutDone --> R
    workshop["WorkshopService"] -- IdeaFinished --> R
    quest["QuestService"] -- QuestCompleted --> R
    debt["DebtService"] -- DebtCleared --> R
    sheet["CharacterService<br/>on every Hall load"] -- "budget kept?" --> X
    R["game.Rewards"] --> X["XpLedger.award<br/>INSERT … ON CONFLICT DO NOTHING"]
    X --> T[("xp_event<br/>unique user · kind · ref")]
    T --> L["total XP → level · title"]
    T --> B["badges"]
    T --> K["streak · days with activity"]
    auth["AuthController.register"] -- UserRegistered --> setup["LedgerSetup<br/>Purse + default categories"]
```

| Event | Published when | Reward |
|---|---|---|
| `EntryLogged` | A person logs an entry (or confirms a recurring one) | +5 XP, first 10 a day |
| `MealLogged` | A meal is logged | +5 XP, once per day |
| `WorkoutDone` | A workout is ticked | +10 XP, once per workout per day |
| `IdeaFinished` | An idea reaches DONE | +15 XP, once per idea |
| `QuestCompleted` | A quest reaches its target | +200 XP, once per quest |
| `DebtCleared` | A debt is repaid in full | +10 XP, once per debt |
| `UserRegistered` | An account is created | default account and categories (`LedgerSetup`) |

Every reward is an `INSERT … ON CONFLICT DO NOTHING` into `xp_event`, whose unique key
is `(user_id, kind, ref)`. That makes paying out **idempotent**: re-checking, a double
click or a retried request can never pay twice. "Budget kept" XP (+50 per budget per
finished month) uses the same trick: it is re-checked every time the character sheet
is opened instead of by a month-end job.

### Scheduling

`RecurringJob` runs at 00:05 in the app's time zone (`krugworld.zone`, default
`Asia/Kuala_Lumpur`) and once at startup to catch up after downtime. It locks due rules
with `FOR UPDATE SKIP LOCKED`, and a partial unique index on
`(recurring_rule_id, occurred_on)` makes a double booking impossible even if two
instances run at once. Rules set to "wait for my confirm" are skipped by the job.

```mermaid
flowchart TD
    start(["00:05 Asia/Kuala_Lumpur<br/>or app startup"]) --> lock["SELECT due rules<br/>active AND auto_post AND next_run_on ≤ today<br/>FOR UPDATE SKIP LOCKED"]
    lock --> any{"any rule left?"}
    any -- no --> done(["log 'Booked N entries'"])
    any -- yes --> acct{"account archived?"}
    acct -- yes --> pause["pause the rule<br/>instead of failing nightly"] --> any
    acct -- no --> due{"next_run_on ≤ today?"}
    due -- yes --> book["INSERT ledger_entry<br/>recurring_rule_id + date<br/>unique index blocks doubles"] --> adv["advance a month<br/>31st → last day of short months"] --> due
    due -- no --> any
```

Time is always read through an injected `Clock` (`common/TimeConfig`), so "today",
month boundaries and streaks follow the configured zone, not the server's, and tests
can control it.

### Security

Two filter chains (`auth/SecurityConfig`):

1. **Device chain**, `/api/device/**`, checked first: stateless, bearer token only
   (`DeviceTokenFilter`), principal `DevicePrincipal`, role `ROLE_DEVICE`. No cookies,
   so no CSRF. Device tokens are random; only their SHA-256 is stored.
2. **Browser chain**, everything else: session cookie plus a signed, year-long
   remember-me cookie (its key lives in `app_secret` so deploys don't log everyone
   out), CSRF via the `XSRF-TOKEN` cookie echoed in the `X-XSRF-TOKEN` header.
   `/api/**` needs a login except login, register and password reset. A missing login
   is a bare 401, never a redirect.

Non-API paths are public static files; the SPA asks `/api/auth/me` who you are.

The device chain, checked first:

```mermaid
flowchart TD
    req(["request to /api/device/…"]) --> pair{"/api/device/pair ?"}
    pair -- yes --> ok1(["allowed: trades a 6-digit<br/>code for a token"])
    pair -- no --> tok{"Bearer token's SHA-256<br/>matches a device?"}
    tok -- yes --> dp(["DevicePrincipal · ROLE_DEVICE<br/>no session, no CSRF"])
    tok -- no --> d401(["401"])
```

Everything else, the browser chain:

```mermaid
flowchart TD
    req(["any other request"]) --> api{"starts with /api/ ?"}
    api -- no --> pub(["static file or index.html<br/>public"])
    api -- yes --> open{"login, register, reset<br/>or /api/status ?"}
    open -- yes --> ok2(["allowed"])
    open -- no --> sess{"session or remember-me<br/>cookie valid?"}
    sess -- no --> b401(["bare 401, never a redirect"])
    sess -- yes --> csrf{"GET, or CSRF header<br/>matches the cookie?"}
    csrf -- no --> f403(["403"])
    csrf -- yes --> cu(["CurrentUser · controller runs"])
```

### Gaby (the Steward)

See [GABY.md](GABY.md). In short: a stateless tool-use loop. The browser sends the
recent conversation; the server lets the model call up to 8 rounds of tools (thin
wrappers over the same services the screens use, so the same rules and XP apply) and
returns the reply plus an English subtitle.

## Frontend

React 19 + TypeScript + Vite 8, React Query for server state, react-router, Tabler
icons. `npm run lint` (oxlint) is kept at zero warnings.

```
frontend/src/
  App.tsx            routes; every page except the Hall is a lazy() chunk
  api/client.ts      the only fetch wrapper: CSRF header, error -> ApiError
  api/types.ts       TypeScript shapes of the API
  api/hooks.ts       React Query hooks for money features (+ invalidation)
  api/lifeHooks.ts   hooks for calendar, vitals, workshop, steward
  components/        Layout, nav, ui primitives, Hall widgets, dialogs
  pages/             one file per page
  steward/           Gaby's portrait, chat and dock
  skin/skin.ts       applies the account's skin/theme to <html>
  styles/skins.css   skin variables; app.css uses only those variables
```

- **Server state** lives in React Query. After a write, hooks invalidate the keys whose
  data changed (an entry changes entries, accounts, summary, budgets, the character
  sheet and recurring). A few writes are **optimistic** (`onMutate` + rollback): ticking
  a workout and deleting an entry.

```mermaid
flowchart LR
    subgraph optimistic["Optimistic write · tick a workout, delete an entry"]
        direction TB
        o1["onMutate<br/>cancel queries · snapshot cache"] --> o2["update cache now<br/>UI changes instantly"] --> o3{"server OK?"}
        o3 -- yes --> o4["onSettled · invalidate · refetch"]
        o3 -- no --> o5["onError · restore snapshot"] --> o4
    end
    subgraph normal["Normal write · everything else"]
        direction TB
        n1["mutate · POST/PUT/DELETE"] --> n2{"server OK?"}
        n2 -- yes --> n3["invalidate affected keys<br/>e.g. MONEY_KEYS"] --> n4["only queries on screen refetch"]
        n2 -- no --> n5["ErrorLine shows {message}"]
    end
```

```mermaid
flowchart TD
    main["main.tsx<br/>QueryClient · Router"] --> appx["App.tsx<br/>useMe → logged in?"]
    appx -- no --> auth["Login · Register · Forgot · Reset"]
    appx -- yes --> layout["Layout<br/>sidebar or bottom tabs · Gaby dock"]
    layout --> hall["HallPage<br/>shipped in main bundle"]
    layout --> lazy["lazy() pages in PageBoundary<br/>Ledger · Budgets · Quests · Recurring · Debts<br/>Accounts · Calendar · Vitals · Workshop<br/>Steward · Oracle · Settings"]
    hall & lazy --> hooks["api/hooks.ts · lifeHooks.ts<br/>React Query"]
    hooks --> client["api/client.ts<br/>CSRF header · ApiError"]
    client --> api[("/api")]
    layout --> skin["skin.ts → html data-skin data-theme<br/>skins.css variables"]
```
- **Skins** are CSS variables on `<html data-skin data-theme>`. Components only use the
  variables, so a new theme is a block of variables, not a component change. *Classic
  Krugger* reproduces the old Tavern exactly; the KrugWorld skin has four backgrounds.
- **Layout**: grouped navigation (Hall · Finance · Life · Arcane · Settings); on phones
  each group is a tab in a bottom bar. The Hall's grid uses container queries on
  `main`, so it adapts to the room actually left when Gaby's dock is open.
- **PWA**: `vite-plugin-pwa` caches the app shell only. `/api` is never cached, so the
  app never shows stale money.
- **SPA routes**: any new route must also be added to `web/SpaController` so a refresh
  on that URL serves `index.html` instead of a 404.

## Desk device

`firmware/` holds the PlatformIO + Arduino + LVGL firmware for the Waveshare
ESP32-S3-Touch-LCD-4B. It pairs with a six-digit code from Settings, shows a glance
screen (Gaby's face, today's numbers, workouts to tick) and does tap-to-talk voice.
Its API is documented in [DEVICE-API.md](DEVICE-API.md).

## Deployment shape

One `postgres:16` container and one app container (`eclipse-temurin:17-jre` running
the jar) per `deploy/docker-compose.yml`, on a shared Lighthouse server behind the
host's nginx and Let's Encrypt. The jar is built on a developer machine; nothing is
compiled on the server. See [OPERATIONS.md](OPERATIONS.md) and `../DEPLOY.md`.
