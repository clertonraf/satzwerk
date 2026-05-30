# Copilot Instructions

Satzwerk is a self-hosted, multi-user gym workout tracker. Users build workout plans, log exercise sets during sessions, and visualise training history as a GitHub-style contribution heatmap.

## Domain language

The canonical domain vocabulary is defined in `CONTEXT.md` at the repo root. Always use those terms — never the synonyms marked as _Avoid_. Key terms: **WorkoutPlan**, **WorkoutGroup**, **WorkoutExercise**, **Exercise**, **WorkoutSession**, **SetLog**, **Heatmap**, **PlanImport**.

A few rules that aren't obvious:
- All weights are always stored and transmitted in **kg**. UI display may convert to lbs via `useSessionStore`.
- Exactly **one active WorkoutPlan** per user at a time; activating one deactivates all others.
- Exactly **one open WorkoutSession** per user at a time; starting a new one requires the existing one to be resumed or discarded first.
- **Exercises are per-user** — there is no shared global catalog.

## Architecture decisions

See `docs/adr/` for rationale. Key decisions:
- **ADR-0001**: Spring WebFlux + R2DBC + coroutines is intentional (not MVC + JPA). Don't revert.
- **ADR-0003**: Offline support covers only active WorkoutSession survival (Dexie `queuedSetLogs` → `offlineQueue.flush()`). All other screens require a live connection.
- **ADR-0004**: Plan import (`POST /api/plans/import`) calls the satzwerk-parser sidecar internally. The frontend has no knowledge of the parser service.

## Backend (Kotlin + Spring Boot WebFlux)

**Pattern**: `Router` → `Handler` → `Service` → `Repository`. There are no `@RestController` classes — routes are declared via `coRouter { }` in `*Router.kt` files.

Packages under `src/main/kotlin/com/satzwerk/`:
- `config/` — security filter chain, JWT filter, R2DBC config
- `auth/` — registration, login, refresh token rotation
- `workouts/` — exercises, workout plans, groups, workout exercises, plan import
- `sessions/` — workout session lifecycle and set logs
- `analytics/` — heatmap and streak calculations

Database migrations live in `src/main/resources/db/migration/` (Flyway, versioned `V<N>__<desc>.sql`).

**Build / run / test:**
```bash
cd backend
./gradlew bootRun           # start with application-local.yml profile
./gradlew test              # all tests (uses Testcontainers — Docker required)
./gradlew test --tests "com.satzwerk.sessions.WorkoutSessionIntegrationTest"  # single test class
./gradlew ktlintCheck       # lint
./gradlew ktlintFormat      # auto-format
./gradlew detekt            # static analysis (maxIssues: 0 — zero tolerance)
```

Integration tests use **Testcontainers** (real PostgreSQL, not H2). Tests run serially (`maxParallelForks = 1`).

## Frontend (React + TypeScript + Vite)

**Feature-based structure** under `src/`:
- `features/{auth,workouts,sessions,analytics}/` — each feature owns its pages, components, hooks, and `__tests__/`
- `services/` — all API calls; `queryKeys.ts` is the single source of truth for TanStack Query cache keys
- `store/` — Zustand stores (`auth.ts` for JWT state, `session.ts` for weight unit preference)
- `lib/db.ts` — Dexie (IndexedDB) schema; only `queuedSetLogs` table for offline queue

Always use the constants from `services/queryKeys.ts` when writing TanStack Query calls — never inline string keys.

**Build / run / test:**
```bash
cd frontend
npm run dev                 # start Vite dev server (http://localhost:5173)
npm test                    # run all tests (Vitest, jsdom)
npx vitest run src/features/workouts/__tests__/PlansPage.test.tsx  # single test file
npm run lint                # ESLint
npm run format              # Prettier
```

## Issue tracker

Issues live as markdown files under `docs/issues/` (not GitHub Issues). Filename convention: `docs/issues/<NN>-<slug>.md`, numbered sequentially. Triage labels are defined in `docs/agents/triage-labels.md`.

## Local dev (full stack with Docker)

```bash
cp .env.example .env        # set DB_PASSWORD and JWT_SECRET at minimum
docker compose up
```
App: http://localhost:5173 · API: http://localhost:8080 · Traefik dashboard: http://localhost:8081
