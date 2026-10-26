# Features and game rules

A tour of every screen, then the rules of the game: how XP, levels, badges and streaks
work. Navigation is grouped as **Hall · Finance · Life · Arcane · Settings**; on phones
each group is a tab in the bottom bar.

```mermaid
mindmap
  root((KrugWorld))
    Hall
      Character and streak flame
      Vital stats
      Debts
      Awaiting your seal
      Today
      Loot log
    Finance
      Ledger
      Budgets
      Quests
      Recurring
      Debts
      Accounts
    Life
      Calendar
      Vitals
        The feast
        Training grounds
      Workshop
    Arcane
      Steward Gaby
      Oracle's Eye
    Settings
      Skin and background
      Recovery email
      Desk Gaby
      Tavern import
      Weather
```

### A day in KrugWorld

```mermaid
flowchart LR
    subgraph am["🌅 Morning"]
        direction TB
        a1["Open the Hall<br/>flame burns low"] --> a2["Tick morning gym<br/>on Today"] --> a3["Flame lights up<br/>+10 XP"]
    end
    subgraph noon["🍛 Lunch"]
        direction TB
        b1["'Gaby, nasi lemak<br/>12 ringgit'"] --> b2["Gaby logs the entry<br/>and the meal"] --> b3["+5 XP · +5 XP"]
    end
    subgraph pm["🌙 Evening"]
        direction TB
        c1["TNB bill awaiting<br/>your seal"] --> c2["Correct to RM 203.40<br/>and confirm"] --> c3["Ali pays back RM 50"] --> c4["Check budget mana<br/>before bed"]
    end
    am --> noon --> pm
```

## The Hall

The landing page: your life as an RPG character sheet. It fits one laptop screen
(1366×657 and up) with Gaby's dock closed. Read it like a sheet:

- **Left: who you are.** The character card: an avatar ring that fills with XP towards
  the next level, your level and title, the XP bar, and the badge grid (locked badges
  are dashed). The **streak flame** sits on the corner of the avatar with the day count
  inside it:
  - **burning** (flickers): you've already done something today;
  - **low** (dim, breathing): the streak is alive but today is still empty;
  - **cold** (grey, 0): no streak. Hovering shows your best streak and how many of the
    last 7 days you were active.

  Below it, **Active quests**: up to three savings goals with progress bars.
- **Top row: the state of your purse**, two halves side by side:
  - **Vital stats**: *Gold* (total balance) as the HP bar against this month's money,
    *Budget mana* (what's left of this month's budgets, with days left), and this
    month's money in and out.
  - **Debts**: what you're owed and what you owe, a tug-of-war bar between them, and the
    three most pressing debts (overdue first, then soonest due).
- **Below: what wants doing, then what happened.**
  - **Awaiting your seal** (only when something is due): recurring entries set to
    "wait for my confirm" whose day has come. The wax-seal button opens the confirm
    dialog.
  - **Today**: calories against the goal, today's planned workouts (tick them off),
    upcoming events and ideas in progress.
  - **Loot log**: the latest ledger entries.

The title bar shows the greeting, the weather for your saved location (Open-Meteo, called
from the browser) and the **Log entry** button.

On medium widths (for example with Gaby docked) the purse row goes across the top and the
hero sits under it; on phones everything is one column.

```mermaid
flowchart LR
    subgraph L["Left: who you are"]
        direction TB
        hero["🧙 Character<br/>avatar · 🔥 streak<br/>level · XP · badges"] ~~~ quests["🗺️ Active quests"]
    end
    subgraph R["Right: your purse, then your day"]
        direction TB
        subgraph top["Top row, half and half"]
            direction LR
            vitals["❤️ Vital stats<br/>Gold · mana · in/out"] ~~~ debts["🤝 Debts<br/>owed vs owe · top 3"]
        end
        subgraph bottom["Below"]
            direction LR
            deeds["🔏 Awaiting your seal<br/>📅 Today"] ~~~ loot["📜 Loot log<br/>latest entries"]
        end
        top ~~~ bottom
    end
    L ~~~ R
```

The breakpoints, by the room the page actually has:

```mermaid
flowchart LR
    w{"width of main"} -- "under 700px · phone" --> p["one column<br/>hero → purse → today → loot"]
    w -- "700–1039px · tablet or Gaby docked" --> m["purse row across the top<br/>hero beside today and loot"]
    w -- "1040px and up · laptop" --> l["hero column on the left<br/>purse on top, today and loot below"]
```

## Finance

### Ledger

Every movement of money, month by month: income, expenses and transfers between
accounts. Filter by account or category; see the month's income, spending and net.
Logging an entry earns XP (see below). Entries made by a recurring rule are marked.

### Budgets ("mana")

Give an expense category a monthly budget and it appears here as a mana bar: budget,
spent and left, plus the month's total and anything spent outside a budget. Finishing a
month within a category's budget earns +50 XP for that budget.

### Quests

Savings goals: a name, an icon, a target and an optional deadline. Put money towards a
quest (or take some back); reaching the target completes it for +200 XP and the
*Quest complete* badge. A completed quest stays completed.

```mermaid
stateDiagram-v2
    [*] --> Active: start a quest
    Active --> Active: contribute or take back
    Active --> Complete: saved ≥ target · +200 XP
    Complete --> Complete: stays complete
```

### Recurring

Salary, rent, subscriptions: things that happen on the same day every month. Each one
says how it's logged **on the day**:

- **Log it by itself** (*Auto*): the nightly job books it at 00:05. For fixed amounts
  like salary or rent.
- **Wait for my confirm** (*Confirm*): on its day it shows up in the Hall under
  *Awaiting your seal*. Open it to check or correct this month's amount, then
  **Confirm** (it's booked and earns entry XP), or **Skip this time** (nothing is booked;
  it moves to next month). For bills that vary, like electricity, or anything you want
  to check before it's recorded. If several months were missed, they come up one at a
  time, oldest first.

Days 29–31 fall on the last day of shorter months. Pausing a rule stops it; deleting it
keeps the entries it already made. If the server was down over a payday, auto rules catch
up when it starts.

```mermaid
stateDiagram-v2
    [*] --> Waiting: create rule
    Waiting: Waiting for its day
    Awaiting: Awaiting your seal
    Waiting --> Booked: day comes, Auto
    Waiting --> Awaiting: day comes, Confirm
    Awaiting --> Booked: Confirm (amount can change)
    Awaiting --> Skipped: Skip this time
    Booked --> Waiting: next month
    Skipped --> Waiting: next month
    Waiting --> Paused: pause
    Paused --> Waiting: resume
    Waiting --> [*]: delete, entries stay
```

Sealing one, step by step:

```mermaid
sequenceDiagram
    actor U as You
    participant H as Hall
    participant D as Seal dialog
    participant API as API · confirm
    participant DB as Database

    H->>U: Electricity (TNB) · Tue 20 Oct · RM 180.00
    U->>D: tap the seal
    D->>U: amount this time? 180.00
    U->>D: 203.40 · Confirm
    D->>API: { on: 2026-10-20, amount: 203.40 }
    API->>DB: lock the rule row
    API->>API: still awaiting, and on = next_run_on?
    API->>DB: INSERT entry 203.40 on 20 Oct
    API->>DB: next_run_on = 20 Nov · +5 XP
    API-->>D: RuleView · awaiting = false
    D-->>H: panel disappears · toast "Sealed · +5 XP"
    Note over U,API: A second tap sends the same "on" date,<br/>finds the rule already moved to 20 Nov,<br/>and gets 409 not_awaiting. Nothing is booked twice.
```

### Debts

A memory of money between you and other people, kept apart from the ledger (it's about
who owes whom, not money in an account).

- **Owed to you** and **You owe** columns, each debt with the person, what it was for,
  a repaid bar, and a due date (red when overdue).
- **They paid / I paid** records a repayment, in parts if needed. Paying the last of it
  settles the debt: +10 XP the first time and the *Clean slate* badge.
- **Let the rest go** settles it without the rest being paid (forgiven, written off).
  It can be opened again.
- Repayments recorded by mistake can be removed; a debt that's owed again reopens.
- Settled debts move to a collapsible *Settled* list.

```mermaid
stateDiagram-v2
    [*] --> Owing: Record a debt
    state Owing {
        [*] --> Open
        Open --> Overdue: due date passes
    }
    note right of Owing: Partial repayments keep it here
    Owing --> Cleared: last of it repaid, +10 XP once
    Owing --> LetGo: Let the rest go
    Cleared --> Owing: remove a repayment
    LetGo --> Owing: Open it again
    Cleared: Settled · repaid in full
    LetGo: Settled · rest let go
    Cleared --> [*]: delete
    LetGo --> [*]: delete
```

```mermaid
flowchart TD
    pay(["They paid RM 50"]) --> chk{"settled already?"}
    chk -- yes --> c409["409 already_settled"]
    chk -- no --> more{"more than<br/>outstanding?"}
    more -- yes --> b400["400 more_than_owed<br/>'Only RM 70.00 is left'"]
    more -- no --> save["save repayment"] --> zero{"outstanding = 0?"}
    zero -- no --> open(["still open · bar fills"])
    zero -- yes --> settle["settled_on = paid on"] --> xp(["DebtCleared → +10 XP<br/>Clean slate badge"])
```

### Accounts

Where the money is kept: bank, cash, e-wallet, card, other, each with an opening balance.
Balances are always computed from the entries. Deleting an account with history archives
it instead (hidden from pickers, history kept); it can be restored.

## Life

### Calendar

A month grid with dots on busy days; pick a day to see and edit its events. Events can be
all-day or at a time. The next few appear in the Hall's Today panel.

### Vitals

- **The feast**: meals with calories against your daily goal, and the last 7 days as a
  bar chart. Logging any meal on a day earns +5 XP once for that day.
- **Training grounds**: workouts planned on certain weekdays (or any day). Ticking one
  earns +10 XP, once per workout per day. Each workout keeps a streak of *planned* days
  done in a row, so resting on an unplanned day doesn't break it.

### Workshop

An idea board in three columns: to do, doing, done. Finishing an idea earns +15 XP.

## Arcane

### Steward (Gaby)

Chat with Gaby, the realm's steward, who can read and change everything above through
tools: log spending, check budgets, add a recurring bill, confirm one, record that Ali
paid you back, plan a workout. She answers in the mix of English and Malay you use, with
a light medieval flavour, plus a one-line English subtitle. On wide screens she can be
docked in a right-hand column on any page. See [GABY.md](GABY.md).

### Oracle's Eye

Shows what your Anthropic organisation spent on Claude, day by day. It calls the
Anthropic Admin API straight from the browser with an admin key you paste in; the key is
kept in your browser only and never reaches KrugWorld's server.

## Settings

- **Skin**: *Classic Krugger* (the old Tavern exactly: pixel fonts, teal stone, square
  borders, scanlines) or *KrugWorld* (Pirata One + Barlow Condensed) with four
  backgrounds: Knight's steel (default), oak, forest, parchment.
- **Recovery email**: needed to reset a forgotten passphrase by email.
- **Desk Gaby**: make a pairing code, see and remove paired devices.
- **Import from Krugger's Tavern**: one-off import of the old app's data.
- **Weather**: your location and °C/°F.

## Game rules

### XP

| Action | XP | Limit |
|---|---|---|
| Log a ledger entry (incl. confirming a recurring one) | 5 | first 10 entries a day |
| Log a meal | 5 | once per day |
| Tick a workout | 10 | once per workout per day (unticking and re-ticking pays nothing) |
| Finish an idea | 15 | once per idea |
| Keep a budget for a finished month | 50 | per budgeted category per month, up to a year back |
| Complete a quest | 200 | once per quest |
| Clear a debt in full | 10 | once per debt |

Entries booked automatically by the nightly job earn nothing: XP is for things you did.
Every reward is recorded once in `xp_event`, so nothing can be paid twice.

Where a typical active week's XP comes from (an illustration, not a rule):

```mermaid
pie showData
    title A typical week's XP
    "Ledger entries (5 each)" : 150
    "Workouts (10 each)" : 40
    "Meal days (5 each)" : 35
    "Ideas finished (15 each)" : 15
    "A debt cleared" : 10
```

### Levels

Reaching level *n* takes `50 × n × (n − 1)` XP in total: 0, 100, 300, 600, 1000, 1500, …
Each level needs 100 more than the one before, so early levels come fast and later ones
need real habits.

```mermaid
xychart-beta
    title "Total XP needed for each level"
    x-axis "Level" [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
    y-axis "XP" 0 --> 4600
    bar [0, 100, 300, 600, 1000, 1500, 2100, 2800, 3600, 4500]
    line [0, 100, 300, 600, 1000, 1500, 2100, 2800, 3600, 4500]
```

| Level | Title |
|---|---|
| 1 | Wanderer |
| 2 | Villager |
| 3 | Apprentice |
| 4 | Journeyman |
| 5 | Adept |
| 6 | Veteran |
| 7 | Champion |
| 8 | Keeper of the Realm |
| 9+ | Legend of KrugWorld |

### Streaks

A day counts if you earned any *activity* XP that day: an entry, a meal, a workout or a
finished idea. The streak is the number of days in a row up to today; today still counts
as "in progress", so the streak doesn't drop to 0 until a whole day passes empty.
Days follow the app's time zone (Asia/Kuala_Lumpur by default).

The flame on your avatar shows which state you're in:

```mermaid
stateDiagram-v2
    direction LR
    Cold: Cold 🩶 · 0
    Lit: Burning 🔥 · N
    Low: Low 🕯️ · N
    [*] --> Cold
    Cold --> Lit: do something today
    Lit --> Low: midnight passes
    Low --> Lit: do something today
    Low --> Cold: a whole day passes empty
```

### Badges

| Badge | How |
|---|---|
| First coin | Log your first entry |
| Week warrior | Be active 7 days in a row |
| Iron habit | Be active 30 days in a row |
| Budget keeper | Finish a month within a budget |
| Quest complete | Reach a savings goal |
| No-spend Sunday | Spend nothing on a Sunday you were tracking (something logged within 3 days) |
| Hoard of a thousand | Hold RM 1,000 across your accounts |
| First feast | Log a meal |
| Iron body | Finish 10 workouts |
| Idea smith | Finish an idea in the Workshop |
| Clean slate | Have a debt paid back in full |
| Adept | Reach level 5 |
