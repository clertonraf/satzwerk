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

**JSON serialization**: `@JsonInclude(NON_NULL)` is not used anywhere in the project — not on classes, not on fields. All null fields are always serialized as `"field": null` and never omitted. Frontend types for nullable fields must use `: T | null`, not `?: T | undefined`.

**Pre-push checklist (backend — run before every push):**
```bash
./gradlew ktlintCheck detekt compileTestKotlin --no-daemon
```
- `ktlintCheck` and `detekt` catch different things — always run both together. detekt enforces `MaxLineLength` (120 chars); ktlint does not.
- After any import block modification (including after running `ktlintFormat`), run `compileTestKotlin` to confirm no imports were accidentally dropped. `ktlintFormat` silently reorders imports and can cause edit collisions.
- Use the rubber-duck agent (`task(rubber-duck)`) before the first push of any non-trivial change to front-load edge-case discovery and reduce Copilot review round-trips. **If the rubber-duck agent does not complete within 3 minutes, cancel it and perform an inline self-review instead — do not retry the agent.**
- Before `git push`, inspect your own diff and verify: (a) SQL rounding/precision is consistent with Kotlin/TypeScript logic, (b) no unused methods or imports left from the change, (c) any defensive guards have an explanatory comment if validation already prevents the case at the API boundary, (d) backend validators (`@Min`, `@DecimalMin`, etc.) are aligned with frontend input constraints, (e) no workaround types or TODO stubs remain in test files.

## Frontend (React + TypeScript + Vite)

**Feature-based structure** under `src/`:
- `features/{auth,workouts,sessions,analytics}/` — each feature owns its pages, components, hooks, and `__tests__/`
- `services/` — all API calls; `queryKeys.ts` is the single source of truth for TanStack Query cache keys
- `store/` — Zustand stores (`auth.ts` for JWT state)
- `lib/db.ts` — Dexie (IndexedDB) schema; only `queuedSetLogs` table for offline queue

Always use the constants from `services/queryKeys.ts` when writing TanStack Query calls — never inline string keys. Keys inside a namespace object use short, unprefixed strings (e.g., `['summary']`, `['weekly-trend', weeks]`) — never prefix the key with the namespace name (e.g., not `['analytics-summary']`).

When adding `useQuery` for an **existing** `queryKey`, the `queryFn` error-handling semantics must match all other uses of that key — React Query shares one cache entry per key and will use whichever `queryFn` mounted last. Specifically, `queryKeys.sessions.open()` maps 404 → `null` but re-throws all other errors; any new consumer must do the same.

**Build / run / test:**
```bash
cd frontend
pnpm dev                    # start Vite dev server (http://localhost:5173)
pnpm test                   # run all tests (Vitest, jsdom)
pnpm test -- src/features/workouts/__tests__/PlansPage.test.tsx  # single test file (use pnpm test --, not npx vitest run)
pnpm lint                   # ESLint
pnpm format                 # Prettier
```

## Session hygiene

Start a **fresh `/new` session** before invoking end-of-session workflows (`/retrospecting`, `/handoff`). These skills inject large context blocks (retrospecting: ~16K chars) — running them at the tail of a long feature session multiplies that cost across all prior turns. Starting clean keeps the skill context as the baseline, not an addition.

After a **planning skill** (`/grill-me`, `/to-prd`) concludes and the plan is agreed, start `/new` before beginning implementation. The Q&A exchange and skill context carry forward otherwise and accumulate input tokens across every subsequent implementation turn.

Never re-invoke the same skill twice in one session. If a skill invocation didn't give the right result, use `/new` before retrying — re-invoking re-sends the full skill context and it persists in the window for every subsequent turn.

## Pre-push gate (mandatory)

Before every `git push`, run all of the following locally. **Do not push if any step fails.**

```bash
# Backend
cd backend && ./gradlew ktlintCheck && ./gradlew detekt

# Frontend
cd frontend && pnpm run lint && tsc -b --noEmit
```

This prevents CI failures that require extra fix-and-push cycles. The backend checks are fast enough to run on every push.

Always push the branch **before** calling `gh pr create` — run them as two separate commands so that push failures (auth, upstream conflicts) surface before PR creation:
```bash
git push -u origin HEAD
gh pr create --fill
```

## Before creating a PR

Run a rubber-duck review against your implementation before opening the PR. For changes touching SVG, CSS layout, or any frontend component, this is **mandatory** — browser rendering edge cases are the most common source of review comments in this repo. **If the rubber-duck agent stalls or does not complete within 3 minutes, cancel it and proceed with the inline self-review checklist from the pre-push section above.**

When the PR touches `useEffect` hooks, run through this checklist before pushing:
- **Cleanup scope**: Does the cleanup function fire only when intended? A cleanup returned from `useEffect(() => { ...; return () => { cleanup(); }; }, [dep])` runs before every re-execution (on every `dep` change), not only on unmount. For unmount-only cleanup, use a separate effect with an empty deps array: `useEffect(() => { return () => { cleanup(); }; }, [])`.
- **Fallback composition**: Does the fallback value produce valid output when composed into surrounding strings? (e.g. `"Satzwerk | Satzwerk"` from a fallback of `"Satzwerk"` in `"${title} | Satzwerk"`).
- **Global state cleanup**: Does `afterEach` in tests reset every piece of global state the component touches (`document.title`, `document.documentElement.classList`, etc.)?

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

Issues live in **GitHub Issues** (`clertonraf/satzwerk`). Use `gh issue view <number>` to read, `gh issue create` to file new ones. See `docs/agents/issue-tracker.md` for full `gh` CLI conventions and triage labels.

## Local dev (full stack with Docker)

```bash
cp .env.example .env        # set DB_PASSWORD and JWT_SECRET at minimum
docker compose up
```
App: http://localhost:5173 · API: http://localhost:8080 · Traefik dashboard: http://localhost:8081
