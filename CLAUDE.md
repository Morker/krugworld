# KrugWorld — project briefing

KrugWorld is the successor to **Krugger's Tavern** (an older single-file app): a gamified
personal life app styled as an RPG character sheet. Money, calendar, meals, training and
ideas all earn XP; the AI helper **Gaby** can act on all of it.

Setup on a new machine: see `docs/SETUP-NEW-PC.md`. Deploying: `DEPLOY.md`.

## Working with the owner

- Explain the **why** behind each step, not just the commands.
- Answer as plain text in the chat; don't publish pages/artifacts unless asked.
- They are an Analyst Programmer (Java + SQL Server at work) building this partly as a
  portfolio piece for backend roles in Malaysia. Prefer patterns worth talking about in
  interviews (Flyway, tests against real Postgres, idempotent jobs, clean API design).
- They like playful, characterful design; plain/"simple" designs were rejected.

## Stack and layout

- `backend/` — Spring Boot 3.3 (Java 17), Spring Data JPA + JdbcTemplate for aggregates,
  Spring Security, Flyway (`db/migration/V1..V8`), Testcontainers 1.21.4 (pinned: older
  versions fail against Docker 29).
- `frontend/` — React 19 + TypeScript + Vite 8, React Query, react-router, Tabler icons.
- `deploy/` — runtime Dockerfile, production compose, nginx site, `redeploy.sh`.
- Packages: `auth`, `ledger`, `budget`, `recurring`, `quest`, `game`, `calendar`,
  `workshop`, `vitals`, `life`, `steward`, `device`, `importer`, `web`, `common`.
- Two security chains: `/api/device/**` is stateless bearer-token only (`DeviceTokenFilter`,
  `ROLE_DEVICE`, principal `DevicePrincipal`); everything else is the browser chain
  (session + remember-me + CSRF, principal `CurrentUser`). Device API: `docs/DEVICE-API.md`.

## Conventions

- **Never edit a Flyway migration that has run.** Add `V<n>__what.sql`. Hibernate is
  `ddl-auto=validate`.
- Every lookup by id also takes the user id (`findByIdAndUserId`): another user's row is
  indistinguishable from a missing one.
- Money: `NUMERIC(14,2)` / `BigDecimal`; the frontend sends amounts as 2-decimal strings.
- Balances are computed from entries in SQL, never stored.
- Rewards are paid at most once via `xp_event` unique `(user_id, kind, ref)` +
  `INSERT … ON CONFLICT DO NOTHING`.
- All API errors are `{code, message[, fields]}` (`ApiExceptionHandler`).
- Never annotate a `String` column with `@Lob` (breaks Postgres reads outside a transaction).
- Frontend: skins are CSS variables on `<html data-skin data-theme>` (`styles/skins.css`);
  components only use the variables. Keep `npm run lint` at zero warnings.
- Tests: `IntegrationTest` base boots the app against Testcontainers Postgres with the
  scheduler off and the Steward on a scripted `FakeChatProvider` (no network, no cost).

## Design decisions (confirmed by the owner)

- **Layout:** RPG character sheet — balance = HP bar, budgets = mana, savings goals =
  quests, badges, streak flames, entries = "loot log". The Hall fits one laptop screen
  (1366×657 and up) with the Gaby dock closed.
- **Navigation groups:** Hall · Finance (Ledger, Budgets, Quests, Recurring, Accounts) ·
  Life (Calendar, Vitals, Workshop) · Arcane (Steward, Oracle's Eye) · Settings.
- **Skins:** *Classic Krugger* must stay exactly like the old Tavern (Press Start 2P +
  VT323, teal stone, square chunky borders, scanlines). The new skin uses **Pirata One +
  Barlow Condensed**, four backgrounds, **Knight's steel** (`#151a22`) default.
- **Gaby** (was "Barnaby"): female pixel-art steward (`frontend/src/steward/Portrait.tsx`),
  same family as before — burgundy cap + gold feather, gold spectacles, teal coat — with
  auburn braids. Docks in the right column on wide screens. Replies in the user's mix of
  **English and Malay** (rojak), light medieval flavour, short enough to be spoken aloud.
- **Language rule:** only Gaby's *spoken/chat reply* is rojak. Everything shown as screen
  text is English: device labels, her daily line (`GabyLine`), and the reply subtitle
  (the `TRANSLATION:` line). The "You: ..." transcript shows the user's own words as said.
- **AI provider: OpenAI for everything** (brain, speech-to-text, text-to-speech), one
  `OPENAI_API_KEY`. Claude remains available via `KRUGWORLD_STEWARD_PROVIDER=anthropic`
  (official `anthropic-java` SDK; default model `claude-opus-5-5`).

## Status (2026-10-12)

- All features of the old Tavern are built, tested and committed.
- Device step 1 (server) done: pairing codes + revocable device tokens (V8), `/api/device/summary`,
  workout ticking, tap-to-talk voice (`gpt-transcribe` → Steward → `gpt-4o-mini-tts` PCM stream),
  Settings → Desk Gaby panel. 73 backend tests. Tried end to end with real OpenAI
  (2026-10-12): rojak transcribed well with auto language detection, tools ran, ~4.7 s to the
  text reply, first audio byte ~1.1 s later. Owner's OpenAI key lives outside the repo in
  `C:\Users\Krugger\Desktop\key\krugworld api key\` — read it into `OPENAI_API_KEY`, never print it.
- **Live since 2026-10-15 at https://krugworld.veteryr.online** (Let's Encrypt, auto-renew).
  `/opt/krugworld` on the shared Lighthouse box `ubuntu@43.156.150.155`, app on
  `127.0.0.1:8096` behind the host nginx; SSH key `~/.ssh/krugworld_lighthouse`. Never
  touch the other projects on that box (tavern, kerisalus, devtrack). Server `.env` holds
  `POSTGRES_PASSWORD` and `OPENAI_API_KEY`. Releases: see `DEPLOY.md` §3 and its notes.
- Desk Gaby talks HTTPS to the live site (Let's Encrypt roots pinned in
  `firmware/src/ca_certs.h`; LVGL's heap lives in PSRAM so TLS has internal RAM).
- Remote: https://github.com/Morker/krugworld (private). The owner commits and pushes with
  GitHub Desktop.

## Next: Gaby as a physical device

The owner has a **Waveshare ESP32-S3-Touch-LCD-4B** and wants it to be a desk Gaby, so
KrugWorld can be used without opening the web app.

**Board:** ESP32-S3-WROOM-1-N16R8 (16 MB flash, 8 MB PSRAM), 4" 480×480 ST7701 display +
GT911 touch, ES8311 playback + ES7210 echo-cancelling mic, 8 Ω 2 W speaker connector,
AXP2101 battery/charging, PCF85063 RTC, QMI8658 IMU, TCA9554 IO expander.
Waveshare's Arduino demos need esp32 core ≥ 3.2.0 and LVGL 9.3; the ESP-IDF factory
firmware includes an AI-chat demo ("Xiaozhi"). Wiki:
https://www.waveshare.com/wiki/ESP32-S3-Touch-LCD-4B

**Pipeline:** device records a WAV clip → backend → OpenAI `gpt-transcribe` (language hint;
Malay isn't explicitly listed in the docs — test `ms`) → existing `StewardService` +
`StewardTools` (don't build a second assistant) → OpenAI `gpt-4o-mini-tts` with
`instructions` for Gaby's voice (voice `coral`, chosen by the owner; PCM 24 kHz 16-bit LE
is simplest for the ESP32; it streams) → device plays audio and shows the subtitle.

**Planned order:**
1. ✅ Server: device pairing (code shown in Settings → device gets its own revocable token;
   never the user's password), a voice endpoint, and a compact summary endpoint.
2. ✅ Firmware v1 (`firmware/`, see its README): pairing pad, glance screen with blinking
   Gaby, tap-to-tick workouts; runs on the real board, paired against the local backend.
   Still to do from the original idea: quick-log buttons. The board's dev server URL is
   this PC's LAN IP in `firmware/include/secrets.h` (gitignored).
   Was: glance screen (Gaby's face + today's numbers, tap to tick workouts,
   quick-log buttons). PlatformIO + Arduino + LVGL suggested to start.
3. Tap-to-talk voice.
4. Reminders and moods (events soon, workout not done, budget low, streak at risk).
5. "Hey Gaby" wake word on-device (ESP-IDF / ESP-SR or microWakeWord).
