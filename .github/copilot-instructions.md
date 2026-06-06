# Copilot Instructions

Satzwerk is a self-hosted, multi-user gym workout tracker. Users build workout plans, log exercise sets during sessions, and visualise training history as a GitHub-style contribution heatmap.

## Domain language

The canonical domain vocabulary is defined in `CONTEXT.md` at the repo root. Always use those terms — never the synonyms marked as _Avoid_. Key terms: **WorkoutPlan**, **WorkoutGroup**, **WorkoutExercise**, **Exercise**, **WorkoutSession**, **SetLog**, **Heatmap**, **PlanImport**.

A few rules that aren't obvious:
- All weights are always stored and transmitted in **kg**. UI display may convert to lb via per-exercise unit state in `SessionPage` (`exerciseUnits: Record<exerciseId, 'kg'|'lb'>`); there is no global unit store.
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

**JSON serialization**: There is no `@JsonInclude(NON_NULL)` at the project level. All null fields are always serialized as `"field": null` — they are never omitted. Frontend types for nullable fields must use `: T | null`, not `?: T | undefined`.

**Pre-push checklist (backend — run before every push):**
```bash
./gradlew ktlintCheck detekt compileTestKotlin --no-daemon
```
- `ktlintCheck` and `detekt` catch different things — always run both together. detekt enforces `MaxLineLength` (120 chars); ktlint does not.
- After any import block modification (including after running `ktlintFormat`), run `compileTestKotlin` to confirm no imports were accidentally dropped. `ktlintFormat` silently reorders imports and can cause edit collisions.
- Use the rubber-duck agent (`task(rubber-duck)`) before the first push of any non-trivial change to front-load edge-case discovery and reduce Copilot review round-trips.

## Frontend (React + TypeScript + Vite)

**Feature-based structure** under `src/`:
- `features/{auth,workouts,sessions,analytics}/` — each feature owns its pages, components, hooks, and `__tests__/`
- `services/` — all API calls; `queryKeys.ts` is the single source of truth for TanStack Query cache keys
- `store/` — Zustand stores (`auth.ts` for JWT state)
- `lib/db.ts` — Dexie (IndexedDB) schema; only `queuedSetLogs` table for offline queue

Always use the constants from `services/queryKeys.ts` when writing TanStack Query calls — never inline string keys. Keys inside a namespace object use short, unprefixed strings (e.g., `['summary']`, `['weekly-trend', n]`) — never prefix the key with the namespace name (e.g., not `['analytics-summary']`).

**Build / run / test:**
```bash
cd frontend
npm run dev                 # start Vite dev server (http://localhost:5173)
npm test                    # run all tests (Vitest, jsdom)
npm test -- src/features/workouts/__tests__/PlansPage.test.tsx  # single test file (use npm test --, not npx vitest run)
npm run lint                # ESLint
npm run format              # Prettier
```

## Session hygiene

Start a **fresh `/new` session** before invoking end-of-session workflows (`/retrospecting`, `/handoff`). These skills inject large context blocks (retrospecting: ~16K chars) — running them at the tail of a long feature session multiplies that cost across all prior turns. Starting clean keeps the skill context as the baseline, not an addition.

Never re-invoke the same skill twice in one session. If a skill invocation didn't give the right result, use `/new` before retrying — re-invoking re-sends the full skill context and it persists in the window for every subsequent turn.

## Pre-push gate (mandatory)

Before every `git push`, run all of the following locally. **Do not push if any step fails.**

```bash
# Backend
cd backend && ./gradlew ktlintCheck && ./gradlew detekt

# Frontend
cd frontend && npm run lint
```

This prevents CI failures that require extra fix-and-push cycles. The backend checks are fast enough to run on every push.

## Before creating a PR

Run a rubber-duck review against your implementation before opening the PR. For changes touching SVG, CSS layout, or any frontend component, this is **mandatory** — browser rendering edge cases are the most common source of review comments in this repo.

Always surface the PR URL immediately after `gh pr create` so the user doesn't have to ask.

## Worktree cleanup

After a PR is merged, proactively remove the associated worktree and local branch without waiting to be asked:

```bash
git worktree remove /path/to/worktree --force
git branch -D feat/branch-name
```

If multiple stale worktrees exist for this repo, clean them all up in the same step.

## Session artifacts

Files under `.copilot/` (retrospective reports, session state) are local-only artifacts. **Never commit them or include them in a PR.** They are gitignored.

## Issue tracker

Issues live as markdown files under `docs/issues/` (not GitHub Issues). Filename convention: `docs/issues/<NN>-<slug>.md`, numbered sequentially. Triage labels are defined in `docs/agents/triage-labels.md`.

## Local dev (full stack with Docker)

```bash
cp .env.example .env        # set DB_PASSWORD and JWT_SECRET at minimum
docker compose up
```
App: http://localhost:5173 · API: http://localhost:8080 · Traefik dashboard: http://localhost:8081
