# Setting up KrugWorld on a new PC

Everything you need to go from the zip to a running app.

## 1. Install the tools

| Tool | Version | Why |
|---|---|---|
| **Java JDK** | 17 (e.g. Eclipse Temurin 17) | The backend is Spring Boot 3 on Java 17 |
| **Node.js** | 20 or newer | Builds and runs the React frontend |
| **Docker Desktop** | any recent | Local PostgreSQL, and the backend tests start a throwaway Postgres in Docker |
| **Git** | any | The zip includes the full history |

Maven is **not** needed: the repo has the Maven wrapper (`backend/mvnw`, `mvnw.cmd`).

After installing the JDK, make sure `JAVA_HOME` points at it (`java -version` should say 17).

## 2. Unzip and check

Unzip anywhere, e.g. `Documents\Projects\krugworld`, then:

```bash
cd krugworld
git log --oneline | head
```

You should see the commit history.

## 3. Start the local database

```bash
docker compose up -d db
```

Postgres runs on `127.0.0.1:5434` (user/password/database all `krugworld`, local only).
It starts empty; Flyway creates every table on the backend's first start.

## 4. Run the backend (API on port 8085)

```bash
cd backend
./mvnw spring-boot:run          # Windows PowerShell: .\mvnw.cmd spring-boot:run
```

Wait for "Started KrugWorldApplication". Check: http://localhost:8085/api/status

## 5. Run the frontend (app on port 5174)

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5174 and **create an account** (the database is new, so the
old demo account doesn't exist here).

## 6. Optional: let Gaby (the AI Steward) talk

Set an OpenAI key before starting the backend:

```powershell
$env:OPENAI_API_KEY = "sk-..."
.\mvnw.cmd spring-boot:run
```

Without a key, Gaby's page simply says she's resting.

## 7. Run the tests

```bash
cd backend && ./mvnw test        # 62 tests; needs Docker running
cd frontend && npm run build && npm run lint
```

## 8. Build the deployable jar

```powershell
.\build.ps1                      # builds frontend + backend into deploy\krugworld.jar
```

`build.ps1` falls back to a JDK inside `..\KruggerTavern\.buildtools` when `JAVA_HOME`
isn't set. On a new PC, just set `JAVA_HOME` (step 1) and it uses that.

## Ports used on the old PC

These were picked to avoid other projects on that machine. Change them if needed:

| What | Port | Where it's set |
|---|---|---|
| Postgres | 5434 | `docker-compose.yml`, `application.properties` |
| API | 8085 | `application.properties` (`PORT` env var overrides) |
| Frontend dev server | 5174 | `frontend/vite.config.ts` (also the `/api` proxy target) |

## Continuing with Claude Code

Open the `krugworld` folder in Claude Code. `CLAUDE.md` at the repo root holds the
project briefing (decisions, status, next steps), so a new session starts with context.
