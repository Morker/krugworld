# Flows: KrugWorld in pictures

Every diagram in the documentation, plus the journeys that don't belong to one page.
The diagrams are [Mermaid](https://mermaid.js.org/), which GitHub renders straight from
the Markdown (in VS Code, install a Mermaid preview extension).

## Diagram index

| Topic | Diagram | In |
|---|---|---|
| The whole system | flowchart: clients → nginx → Spring → Postgres / AI | [ARCHITECTURE](ARCHITECTURE.md#architecture) |
| Logging an entry end to end | sequence | [ARCHITECTURE](ARCHITECTURE.md#life-of-a-request) |
| Layers | flowchart: controller → service → JPA / JdbcTemplate | [ARCHITECTURE](ARCHITECTURE.md#layers-and-conventions) |
| How XP is paid | flowchart: events → Rewards → `xp_event` | [ARCHITECTURE](ARCHITECTURE.md#domain-events-and-rewards) |
| Nightly recurring job | flowchart | [ARCHITECTURE](ARCHITECTURE.md#scheduling) |
| Which security chain | decision tree | [ARCHITECTURE](ARCHITECTURE.md#security) |
| Frontend writes and structure | flowcharts | [ARCHITECTURE](ARCHITECTURE.md#frontend) |
| Schema history | timeline | [DATABASE](DATABASE.md#migrations) |
| Every table | 4 ER diagrams: ledger · debts and quests · game and life · security | [DATABASE](DATABASE.md#entity-relationships) |
| Derived numbers | flowchart: balance, outstanding, mana, level, streak | [DATABASE](DATABASE.md#how-derived-numbers-are-computed) |
| App map | mind map | [FEATURES](FEATURES.md) |
| A day in KrugWorld | user journey | [FEATURES](FEATURES.md#a-day-in-krugworld) |
| The Hall's layout and breakpoints | block diagram + flowchart | [FEATURES](FEATURES.md#the-hall) |
| Recurring entry lifecycle | state diagram | [FEATURES](FEATURES.md#recurring) |
| Sealing a bill | sequence | [FEATURES](FEATURES.md#recurring) |
| Quest lifecycle | state diagram | [FEATURES](FEATURES.md#quests) |
| Debt lifecycle and repayment rules | state diagram + flowchart | [FEATURES](FEATURES.md#debts) |
| Where XP comes from | pie | [FEATURES](FEATURES.md#xp) |
| Level curve | XY chart | [FEATURES](FEATURES.md#levels) |
| Streak flame | state diagram | [FEATURES](FEATURES.md#streaks) |
| Gaby's tool loop | sequence | [GABY](GABY.md#how-a-message-is-answered) |
| Gaby's language rule | flowchart | [GABY](GABY.md#character) |
| Choosing the AI provider | flowchart | [GABY](GABY.md#providers-and-configuration) |
| Spend guards | flowchart | [GABY](GABY.md#spend-guards) |
| Voice on the desk device | sequence | [GABY](GABY.md#voice-desk-device) |
| Local dev setup | flowchart | [DEVELOPMENT](DEVELOPMENT.md#run-it-locally) |
| How the tests are wired | flowchart | [DEVELOPMENT](DEVELOPMENT.md#tests) |
| Adding a feature | flowchart | [DEVELOPMENT](DEVELOPMENT.md#adding-a-feature-checklist) |
| The server | flowchart | [OPERATIONS](OPERATIONS.md#where-it-runs) |
| A release | flowchart: PC → server → health | [OPERATIONS](OPERATIONS.md#how-a-release-works) |
| "Not healthy" troubleshooting | decision tree | [OPERATIONS](OPERATIONS.md#when-redeploysh-says-not-healthy) |
| The 23 Oct incident | git graph + timeline | [OPERATIONS](OPERATIONS.md#lessons) |
| Push-to-deploy (proposed) | flowchart | [OPERATIONS](OPERATIONS.md#lessons) |

The rest are below.

## Signing up and staying signed in

```mermaid
flowchart TD
    open(["open the site"]) --> me{"GET /api/auth/me"}
    me -- 200 --> hall(["the Hall"])
    me -- 401 --> door["the door: Log in · Register"]
    door -- register --> reg["POST /api/auth/register<br/>name 3–40 · passphrase 8+ · email optional"]
    reg -- "409 username_taken / email_taken" --> door
    reg -- ok --> seed["UserRegistered →<br/>'Purse' account + 9 default categories"] --> cookies
    door -- log in --> login["POST /api/auth/login<br/>form: username, password"]
    login -- 401 --> door
    login -- 200 --> cookies["session cookie<br/>+ signed remember-me cookie, 1 year<br/>+ XSRF-TOKEN cookie"]
    cookies --> hall
    hall -. "days later, session expired" .-> rm{"remember-me valid?"}
    rm -- yes --> hall
    rm -- "no · password changed or logged out" --> door
```

## Forgot the passphrase

```mermaid
sequenceDiagram
    autonumber
    actor U as You
    participant W as Web page
    participant A as Reset service
    participant DB as Database
    participant M as Mailer
    participant In as Inbox

    U->>W: email address
    W->>A: POST /password-reset/request
    A->>DB: account with this email? last link over 1 min ago?
    opt account found and not rate-limited
        A->>DB: store SHA-256 of a random token · expires in 30 min
        A-)M: send link with the raw token
        M-)In: "Reset your KrugWorld passphrase"
    end
    A-->>W: 200 · the same answer either way
    Note over W,A: Never reveals whether the email exists,<br/>and the mail goes out on another thread<br/>so timing doesn't give it away either.
    U->>In: open the link
    In->>W: /reset-password?token=…
    U->>W: new passphrase
    W->>A: POST /password-reset/confirm
    A->>DB: hash matches, unused, not expired?
    A->>DB: set password · mark used · kill other links
    A-->>W: 200 · remembered devices are logged out
```

## Pairing a desk Gaby

```mermaid
sequenceDiagram
    autonumber
    actor U as You
    participant S as Settings
    participant API as Server
    participant DB as Database
    participant D as Desk Gaby

    U->>S: Make a pairing code
    S->>API: POST /api/devices/pairing-code
    API->>DB: replace your live code · 6 digits · 10 min
    API-->>S: 482 913
    U->>D: types 482913 on the pairing pad
    D->>API: POST /api/device/pair {code, name}
    API->>DB: code live, under 5 tries? under 5 devices?
    alt wrong code
        API->>DB: every live code loses a try
        API-->>D: 401 bad_code
    else right code
        API->>DB: delete the code · INSERT device with SHA-256 of a new 256-bit token
        API-->>D: {deviceId, token, username} · token kept on the device
    end
    loop every minute
        D->>API: GET /api/device/summary · Bearer token
        API-->>D: today's numbers + Gaby's line
    end
    U->>S: Remove device
    S->>API: DELETE /api/devices/{id}
    Note over D,API: the token's row is gone · next request 401
```

## Importing from Krugger's Tavern

```mermaid
flowchart TD
    exp(["Tavern: export /api/kv"]) --> up["Settings → Import<br/>POST /api/import/tavern"]
    up --> any{"any kt-* sections?"}
    any -- no --> e1["400 nothing_to_import"]
    any -- yes --> twice{"'Tavern purse' account exists?"}
    twice -- yes --> e2["409 already_imported"]
    twice -- no --> purse["create the 'Tavern purse' account"]
    purse --> map["kt-finance → entries, new categories<br/>kt-commitments → recurring rules<br/>kt-calendar → events<br/>kt-calorie-log, goal → meals, goal<br/>kt-workouts → workouts<br/>kt-ideas → ideas"]
    map --> res(["counts: imported · skipped"])
```

## "Budget kept" XP, checked lazily

There's no month-end job: the reward is re-checked every time the Hall loads, and the
unique key on `xp_event` makes re-checking free.

```mermaid
flowchart TD
    load(["GET /api/character"]) --> b{"any expense category<br/>with a budget?"}
    b -- no --> sheet(["build the sheet"])
    b -- yes --> m["for each finished month<br/>since the first entry, up to 12 back"]
    m --> idle{"anything logged<br/>that month?"}
    idle -- no --> next["skip · no reward for idle months"]
    idle -- yes --> c{"spent ≤ budget<br/>for this category?"}
    c -- yes --> aw["award BUDGET_KEPT<br/>ref = month:category · +50<br/>ON CONFLICT DO NOTHING"]
    c -- no --> next
    aw --> next --> m
    m -- done --> sheet
```

## Offline and the PWA

```mermaid
flowchart LR
    req(["request"]) --> kind{"what is it?"}
    kind -- "app shell<br/>html · js · css · icons" --> sw["service worker cache<br/>works offline, updates on next visit"]
    kind -- "/api/*" --> net["always the network<br/>never cached, never stale money"]
    kind -- "weather" --> om["Open-Meteo from the browser<br/>React Query, 15 min"]
```
