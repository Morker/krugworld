# API reference

The browser API: everything under `/api` except `/api/device/**`, which the desk device
uses with its own token (see [DEVICE-API.md](DEVICE-API.md)).

## Conventions

- **Auth.** A session cookie set by login, plus a signed remember-me cookie. All `/api`
  calls need a login except: `POST /api/auth/login`, `POST /api/auth/register`,
  `POST /api/auth/password-reset/request|confirm`, `GET /api/status`. Without a login the
  answer is a bare `401`.
- **CSRF.** Every non-GET request must send the `XSRF-TOKEN` cookie's value in an
  `X-XSRF-TOKEN` header (`frontend/src/api/client.ts` does this).
- **JSON** in and out. Dates are `YYYY-MM-DD` (local calendar dates), months `YYYY-MM`,
  times `HH:MM[:SS]`.
- **Money** comes back as JSON numbers and should be sent as two-decimal strings
  (`"12.50"`). Amounts are always positive; the entry `type` carries the direction.
- **Ownership.** Ids belonging to another user behave exactly like ids that don't exist:
  `404 not_found`.
- **Status codes.** `200` read/update, `201` create, `204` delete, `400` invalid,
  `401` not logged in, `404` not found, `409` conflict (state has moved on),
  `429` Gaby's daily limit, `502` AI provider failed, `503` feature not configured.

### Errors

```json
{ "code": "more_than_owed", "message": "Only RM 70.00 is left to pay." }
```

Validation failures add the failing fields:

```json
{ "code": "invalid", "message": "Some fields need another look.",
  "fields": { "amount": "must be greater than or equal to 0.01" } }
```

`code` is stable and meant for code to switch on; `message` is for showing to people.

---

## Auth and account

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/auth/login` | form: `username`, `password` | `200`, or `401` |
| POST | `/api/auth/logout` | | `200` |
| POST | `/api/auth/register` | `{username, password, email?}` | `Me`, logged in. `409 username_taken` / `email_taken` |
| GET | `/api/auth/me` | | `Me` |
| PATCH | `/api/auth/me/preferences` | any of `{skin, theme, calorieGoal, tempUnit, email, weather}` | `Me` |
| POST | `/api/auth/password-reset/request` | `{email}` | always `200` (never reveals whether the email exists). `503 reset_unavailable` without SMTP |
| POST | `/api/auth/password-reset/confirm` | `{token, password}` | `200`. `400 reset_invalid` |

`username`: 3–40 letters, digits, `_` or `-`, unique ignoring case. `password`: 8–100
characters. `skin`: `classic|modern`. `theme`: `steel|oak|forest|parchment`.
`calorieGoal`: 500–10000. `tempUnit`: `C|F`. `email`: blank clears it.
`weather`: `{place, lat, lon}`.

```json
// Me
{ "id": 1, "username": "krugger", "email": null, "skin": "modern", "theme": "steel",
  "calorieGoal": 2000, "tempUnit": "C",
  "weather": { "place": "Kuala Lumpur, Malaysia", "lat": 3.139, "lon": 101.6869 } }
```

## Character sheet

`GET /api/character`: everything the Hall shows, derived on read. Opening it also
awards any "budget kept" XP due for finished months.

```json
{ "username": "krugger", "level": 3, "title": "Apprentice", "xp": 340,
  "levelXp": 40, "levelXpNeeded": 300,
  "gold": 6097.60, "incomeThisMonth": 6200.00, "spentThisMonth": 102.40,
  "budget": { /* MonthBudget, see Budgets */ },
  "streak": { "current": 4, "longest": 12, "last7": [false, true, true, true, false, true, true] },
  "badges": [ { "id": "first_coin", "name": "First coin", "description": "Log your first entry.",
                "icon": "coin", "earned": true } ],
  "quests": [ /* up to 3 active QuestView */ ],
  "loot": [ /* 8 most recent EntryView */ ] }
```

`gold` is the total balance across accounts. `last7` ends with today.

## Accounts

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/accounts` | | `AccountView[]` with live balances |
| POST | `/api/accounts` | `{name, kind, openingBalance}` | `201 AccountView` |
| PUT | `/api/accounts/{id}` | same | `AccountView` |
| DELETE | `/api/accounts/{id}` | | `204`; archived instead if it has entries |
| POST | `/api/accounts/{id}/restore` | | `AccountView` |

`kind`: `BANK|CASH|EWALLET|CARD|OTHER`.
`AccountView`: `{id, name, kind, openingBalance, balance, archived}`.

## Categories

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/categories` | | `CategoryView[]` |
| POST | `/api/categories` | `{name, kind, icon, color, monthlyBudget?}` | `201`. `409 category_exists` |
| PUT | `/api/categories/{id}` | same | `CategoryView` |
| DELETE | `/api/categories/{id}` | | `204`; archived (budget cleared) if used |
| POST | `/api/categories/{id}/restore` | | `CategoryView` |

`kind`: `EXPENSE|INCOME`. `color`: `#rrggbb`. `monthlyBudget` only applies to expense
categories. `icon` is one of: `food transport bills fun shopping health home education
gift salary savings travel pet phone coffee coin` (`400 unknown_icon` otherwise).

## Entries (the ledger)

| Method | Path | Body / query | Returns |
|---|---|---|---|
| GET | `/api/entries` | `?month=YYYY-MM&accountId=&categoryId=` (month defaults to now) | `EntryView[]`, newest first |
| POST | `/api/entries` | `EntryRequest` | `201 EntryView`, +5 XP (first 10 a day) |
| PUT | `/api/entries/{id}` | `EntryRequest` | `EntryView` |
| DELETE | `/api/entries/{id}` | | `204` |
| GET | `/api/ledger/summary` | `?month=YYYY-MM` | `MonthSummary` |

```json
// EntryRequest
{ "type": "EXPENSE", "amount": "12.50", "accountId": 3, "categoryId": 7,
  "toAccountId": null, "occurredOn": "2026-10-23", "note": "Nasi lemak" }
```

Rules: income/expense need a category of the matching kind (`category_required`,
`category_kind_mismatch`); a transfer needs `toAccountId` different from `accountId` and
no category (`transfer_needs_two_accounts`); archived accounts can't take new entries
(`account_archived`).

`EntryView`: `{id, type, amount, occurredOn, note, account: {id, name},
toAccount: {id, name} | null, category: {id, name, icon, color} | null, recurring}`.

`MonthSummary`: `{month, income, expense, net, spendingByCategory: [{category, total}]}`.

## Budgets

`GET /api/budgets?month=YYYY-MM` returns a `MonthBudget`:

```json
{ "month": "2026-10", "daysLeft": 8, "totalBudget": 1200.00, "totalSpent": 840.10,
  "left": 359.90, "unbudgetedSpent": 52.00,
  "categories": [ { "category": {"id":7,"name":"Food","icon":"food","color":"#e5606a"},
                    "budget": 600.00, "spent": 512.30, "left": 87.70 } ] }
```

A budget is set through the category's `monthlyBudget`.

## Recurring

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/recurring` | | `RuleView[]` by day of month |
| POST | `/api/recurring` | `RuleRequest` | `201 RuleView`; a past `startOn` catches up at once (auto rules) |
| PUT | `/api/recurring/{id}` | `RuleRequest` | `RuleView` |
| DELETE | `/api/recurring/{id}` | | `204`; entries it made stay |
| POST | `/api/recurring/{id}/confirm` | `{on, amount?}` | `RuleView`; books the awaiting entry, +5 XP |
| POST | `/api/recurring/{id}/skip` | `{on}` | `RuleView`; moves on without booking |

```json
// RuleRequest
{ "type": "EXPENSE", "amount": "180.00", "accountId": 3, "categoryId": 9,
  "dayOfMonth": 20, "startOn": "2026-10-01", "note": "Electricity (TNB)",
  "active": true, "autoPost": false }

// RuleView
{ "id": 4, "type": "EXPENSE", "amount": 180.00, "account": {"id":3,"name":"Maybank"},
  "category": {"id":9,"name":"Bills","icon":"bills","color":"#e2b453"},
  "note": "Electricity (TNB)", "dayOfMonth": 20, "nextRunOn": "2026-10-20",
  "active": true, "autoPost": false, "awaitingConfirmation": true }
```

- `type` is `INCOME` or `EXPENSE` (`400 transfer_not_recurring`).
- `autoPost` defaults to `true` (logged by itself on the day). With `false`, the rule
  becomes `awaitingConfirmation` on its day until confirmed or skipped.
- **confirm/skip** must send `on` = the `nextRunOn` the user saw. If the rule isn't
  awaiting or `on` doesn't match (a double tap, a second tab), the answer is
  `409 not_awaiting` and nothing is booked twice. `amount` overrides the amount for that
  one occurrence (bills that vary).
- Switching a rule from confirm to auto books anything that was awaiting.

## Debts

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/debts` | | `DebtView[]`: open first (soonest due on top), then settled (latest first) |
| POST | `/api/debts` | `DebtRequest` | `201 DebtView` |
| PUT | `/api/debts/{id}` | `DebtRequest` | `DebtView` |
| DELETE | `/api/debts/{id}` | | `204` |
| POST | `/api/debts/{id}/settle` | | `DebtView`; tick it off, +10 XP the first time (a second tick changes nothing) |
| POST | `/api/debts/{id}/reopen` | | `DebtView`; untick, it's owed again |

```json
// DebtRequest
{ "direction": "THEY_OWE", "person": "Ali", "amount": "120.00",
  "note": "Concert ticket", "startedOn": "2026-10-10", "dueOn": "2026-10-27" }

// DebtView
{ "id": 2, "direction": "THEY_OWE", "person": "Ali", "amount": 120.00,
  "note": "Concert ticket", "startedOn": "2026-10-10", "dueOn": "2026-10-27",
  "settledOn": null, "overdue": false }
```

- `direction`: `THEY_OWE` (someone owes the user) or `I_OWE`.
- `startedOn` defaults to today; `dueOn` is optional and can't be before it
  (`due_before_start`).
- `settledOn` is the day it was ticked off; `null` while still owed.

## Quests (savings goals)

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/quests` | | `QuestView[]` |
| POST | `/api/quests` | `{name, icon, targetAmount, deadline?}` | `201 QuestView` |
| PUT | `/api/quests/{id}` | same | `QuestView` |
| DELETE | `/api/quests/{id}` | | `204` |
| POST | `/api/quests/{id}/contributions` | `{amount}` (negative takes back) | `QuestView`; reaching the target completes it, +200 XP once |

`QuestView`: `{id, name, icon, targetAmount, savedAmount, deadline, completedAt,
xpReward}`. A completed quest stays completed. `400 zero_amount` for a zero
contribution.

## Calendar

| Method | Path | Body / query | Returns |
|---|---|---|---|
| GET | `/api/events` | `?from=YYYY-MM-DD&to=YYYY-MM-DD` (inclusive, about a year at most) | `EventView[]` |
| POST | `/api/events` | `{occursOn, atTime?, title}` | `201 EventView` |
| PUT | `/api/events/{id}` | same | `EventView` |
| DELETE | `/api/events/{id}` | | `204` |

`EventView`: `{id, occursOn, atTime, title}`; `atTime` null means all day.

## Vitals

| Method | Path | Body / query | Returns |
|---|---|---|---|
| GET | `/api/meals` | `?date=` (default today) | `MealDay` |
| POST | `/api/meals` | `{name, calories, eatenOn}` | `201`; +5 XP once per day with a meal |
| DELETE | `/api/meals/{id}` | | `204` |
| GET | `/api/workouts` | `?date=` | `WorkoutView[]` |
| POST | `/api/workouts` | `{name, days}` | `201 WorkoutView` |
| PUT | `/api/workouts/{id}` | same | `WorkoutView` |
| DELETE | `/api/workouts/{id}` | | `204` |
| POST | `/api/workouts/{id}/done` | `{date, done}` | `WorkoutView`; +10 XP once per workout per day |

`MealDay`: `{date, goal, total, meals: [{id, name, calories}], week: [{date, calories}]}`.
`days`: weekday names (`MONDAY` … `SUNDAY`); empty means any day.
`WorkoutView`: `{id, name, days, scheduled, done, streak, totalDone}`; `streak` counts
planned days in a row that were done.

## Workshop

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/ideas` | | `IdeaView[]`: doing, to do, done |
| POST | `/api/ideas` | `{title, notes?, status?}` | `201 IdeaView` |
| PUT | `/api/ideas/{id}` | same | `IdeaView`; reaching `DONE` gives +15 XP once |
| DELETE | `/api/ideas/{id}` | | `204` |

## Today

`GET /api/life/today`: `{date, calories, calorieGoal, workouts: [{id, name, done}],
upcoming: EventView[], ideasInProgress, ideasWaiting}`; the Hall's Today panel.

## Gaby

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/steward/status` | | `{ready, provider, remaining, limit}` |
| POST | `/api/steward/chat` | `{message, history: [{role, text}]}` | `{reply, subtitle, remaining}` |

`message`: at most 1000 characters (`400 too_long`, `400 empty`). The server keeps no
chat state: the browser sends the recent `history` (`role` is `user` or `assistant`).
`503 not_configured` without an API key, `429 user_limit` / `global_limit` when the day's
allowance is used, `502 steward_failed` if the provider fails. See [GABY.md](GABY.md).

## Desk devices (managed from the browser)

| Method | Path | Returns |
|---|---|---|
| GET | `/api/devices` | `[{id, name, createdAt, lastSeenAt}]` |
| POST | `/api/devices/pairing-code` | `{code, expiresAt}`: six digits, 10 minutes |
| DELETE | `/api/devices/{id}` | `204`; the device's token stops working at once |

## Import and status

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/import/tavern` | Krugger's Tavern `/api/kv` export | `{entries, recurring, categoriesCreated, events, meals, workouts, ideas, skipped}`. `409 already_imported`, `400 nothing_to_import` |
| GET | `/api/status` | | `{app, status, schemaVersion}` (public) |
| GET | `/actuator/health` | | `{"status":"UP"}`; used by `redeploy.sh` |
