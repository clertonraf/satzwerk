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

**Flyway version collision check**: Before merging any PR that adds a migration file, verify the highest V-number already on `main` and rename if there is a collision:
```bash
git log origin/main --name-only -- "src/main/resources/db/migration/" | grep "V[0-9]" | sort -V | tail -5
```
Two PRs that independently choose the same version number (e.g., both use V11) will break the schema — always rename the later PR's file before merge.

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

**Kotlin coroutine test rule**: All test functions that use expression-body `runBlocking` **must** declare an explicit `: Unit` return type:
```kotlin
// WRONG — Kotlin infers Flow<T> return type if the last expression returns Flow<T>;
// JUnit 5 silently ignores non-void test methods, so the test never runs.
@Test
fun `my test`() = runBlocking { verify(repo).saveAll(any()) }

// CORRECT
@Test
fun `my test`(): Unit = runBlocking { verify(repo).saveAll(any()) }
```
This is the only way to guarantee JUnit 5 discovers the test. ktlint and detekt do not catch this.

**Verifying calls on domain objects with `Instant.now()` defaults**: `Exercise`, `WorkoutSession`, and similar entities have `createdAt`/`updatedAt` fields that default to `Instant.now()`. Data class `equals()` compares all fields, so direct `verify(repo).save(expectedObject)` equality checks always fail. Use `argumentCaptor` to capture and assert individual fields instead:
```kotlin
val captor = argumentCaptor<Iterable<Exercise>>()
verify(exerciseRepository).saveAll(captor.capture())
val saved = captor.firstValue.toList()
assertEquals("Bench Press", saved[0].name)
```

**JSON serialization**: `@JsonInclude(NON_NULL)` is not used anywhere in the project — not on classes, not on fields. All null fields are always serialized as `"field": null` and never omitted. Frontend types for nullable fields must use `: T | null`, not `?: T | undefined`.

**Pre-push checklist (backend — run before every push):**
```bash
./gradlew ktlintCheck detekt compileTestKotlin --no-daemon
```
- `ktlintCheck` and `detekt` catch different things — always run both together. detekt enforces `MaxLineLength` (120 chars); ktlint does not.
- After writing Kotlin test mock blocks (`mock { onBlocking { } doReturn ... }`), run `ktlintFormat` **before** `ktlintCheck` — `standard:multiline-expression-wrapping` violations are auto-correctable and consistently appear in these patterns.
- After any import block modification (including after running `ktlintFormat`), run `compileTestKotlin` to confirm no imports were accidentally dropped. `ktlintFormat` silently reorders imports and can cause edit collisions.
- Use the rubber-duck agent (`task(rubber-duck)`) before the first push of any non-trivial change to front-load edge-case discovery and reduce Copilot review round-trips. **If the rubber-duck agent does not complete within 3 minutes, cancel it and perform an inline self-review instead — do not retry the agent.**
- For diffs that span Kotlin backend + React frontend + SQL (cross-stack changes), explicitly set `model: "claude-sonnet-4.6"` in the rubber-duck task call — it has better cross-language context awareness for this stack than GPT models.
- Before `git push`, inspect your own diff and verify:
  - SQL rounding/precision is consistent with Kotlin/TypeScript logic
  - no unused methods or imports left from the change
  - any defensive guards have an explanatory comment if validation already prevents the case at the API boundary
  - backend validators (`@Min`, `@DecimalMin`, etc.) are aligned with frontend input constraints
  - no workaround types or TODO stubs remain in test files
  - no `@Suppress(...)` annotations added to silence detekt or ktlint findings — fix the underlying issue instead

**Avoiding `TooManyFunctions` (detekt threshold ≥ 11)**: When adding a method would push a class to 11 functions (the threshold triggers AT 11, not above), extract private helpers as **package-level functions** passing dependencies (repositories, services) as parameters. This keeps the class under the limit without `@Suppress`. Example: `requireOwnedSession` and `requireGroupInActivePlan` in `WorkoutSessionService.kt` are package-level.

**Parallel PR file-overlap check**: When batching multiple PRs for sequential merge, check for overlapping files before starting parallel development:
```bash
git diff --name-only origin/main...<branch1>
git diff --name-only origin/main...<branch2>
```
PRs that touch the same file must be merged serially (each fully merged before the next is developed). Parallel development on a shared hub file (e.g., `WorkoutSessionService.kt`) leads to cascading conflicts requiring multiple manual resolutions.

**NOT NULL FK lookups**: When a service method receives an entity referenced via a NOT NULL FK column validated upstream (e.g. `workout_sessions.workout_group_id`), do not add a redundant `repository.findById()` call — the FK constraint guarantees existence. Add lookups only when the ID arrives from unvalidated user input.

## Frontend (React + TypeScript + Vite)

**Feature-based structure** under `src/`:
- `features/{auth,workouts,sessions,analytics}/` — each feature owns its pages, components, hooks, and `__tests__/`
- `services/` — all API calls; `queryKeys.ts` is the single source of truth for TanStack Query cache keys
- `store/` — Zustand stores (`auth.ts` for JWT state)
- `lib/db.ts` — Dexie (IndexedDB) schema; only `queuedSetLogs` table for offline queue

Always use the constants from `services/queryKeys.ts` when writing TanStack Query calls — never inline string keys. Keys inside a namespace object use short, unprefixed strings (e.g., `['summary']`, `['weekly-trend', weeks]`) — never prefix the key with the namespace name (e.g., not `['analytics-summary']`).

When adding `useQuery` for an **existing** `queryKey`, the `queryFn` error-handling semantics must match all other uses of that key — React Query shares one cache entry per key and will use whichever `queryFn` mounted last. Specifically, `queryKeys.sessions.open()` maps 404 → `null` but re-throws all other errors; any new consumer must do the same.

**Never suppress errors**: Do not use `// @ts-ignore`, `// @ts-expect-error`, `/* eslint-disable */`, or any inline directive to silence TypeScript or ESLint errors. Fix the underlying issue. Suppression directives mask real bugs and have been rejected in review every time they appeared in this codebase.

**Test assertions on display values**: When asserting on formatted output (weights, dates, percentages), call the same helper function used in the component (e.g. `formatDisplayWeight(value, unit)`) rather than hardcoding the expected string. Hardcoded raw values drift when formatting logic changes and were a recurring PR review finding.

**Mock isolation**: Always call `vi.mocked(fn).mockReset()` before reassigning mock implementations in `beforeEach`. This clears both the call history and the implementation from the previous test, preventing cross-test coupling. Applies to any mock that is reconfigured per-test (not just set once at the top level).

**Test fixture consistency**: `setCount` must equal `setLogs.length` in `WorkoutSession` test fixtures. Build a `buildSession()` helper that accepts `setLogs` and derives `setCount` from the array length — never set them as independent constants that can drift.

**React async test pattern (hooks with async state + dispatches)**: When testing hooks that mix `useQuery` with async dispatch operations:
- Create the QueryClient with `defaultOptions: { queries: { retry: false, staleTime: Infinity } }` so pre-seeded cache data is not immediately refetched.
- Pre-seed the React Query cache in `beforeEach` via `queryClient.setQueryData(queryKeys.sessions.open(), null)` to prevent the initial query from racing with dispatches.
- Declare `dispatchPromises: Promise<void>[] = []` (initialized, not just declared) to avoid TS2454 under `tsc -b`.
- Wrap promise *resolution* inside `act()` — any code that triggers a React state update (including resolving a deferred transport promise) must be inside `act()`.
- Use `await act(async () => { resolver(); await Promise.all(dispatchPromises); })` for final cleanup in reconciliation tests.
- Never use `null as never` or `undefined as never` in mock setup. For the 404→null mapping use a proper rejection: `mockRejectedValue(Object.assign(new Error('Not Found'), { isAxiosError: true, response: { status: 404 } }))`. For void-returning services use plain `undefined`.

**SetLog type fields**: `SetLog` (`id, exerciseId, setNumber, weight, reps, loggedAt`) has no `pending` or `isPr` field. `SubmittedSetLog = SetLog & { pending: false }` and `PendingSetLog = Omit<SetLog, 'id'> & { id: string; pending: true }`. `isPr` is a backend-only concept and does not exist anywhere in the frontend session types. Do not add `pending` or `isPr` to plain `SetLog` test fixtures.

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

In **batched-merge sessions** (merging many PRs in sequence), start a fresh `/new` session every 5–6 PRs to prevent context compaction from interrupting mid-task. The checkpoint system provides sufficient handoff continuity between waves.

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

**Copilot reviewer on fix commits**: When a follow-up fix commit is pushed to a PR, the Copilot reviewer does not always produce a new inline review — it may only run the `copilot: completed - success` check. Accept the passing `copilot` check as the gate for fix commits rather than waiting for a full re-review that may not arrive.

**Re-triggering stale CI without a dummy commit**: When a PR's CI run is stale (no checks triggered after a push), use `gh run rerun` instead of an empty commit:
```bash
# Re-run the most recent failed run on a branch
gh run list --branch <branch> --limit 1 --json databaseId -q '.[0].databaseId' | xargs -I{} gh run rerun {} --failed --repo clertonraf/satzwerk
# Or trigger a fresh workflow run
gh workflow run CI --ref <branch> --repo clertonraf/satzwerk
```
Empty commits (`git commit --allow-empty -m "ci: trigger CI"`) pollute git history and should be avoided.

**Exception — GHA runner unavailability**: When the CI failure message is "The job was not acquired by Runner of type hosted even after multiple attempts", `gh run rerun` returns "This workflow run cannot be retried" and `gh workflow run CI` triggers a new run but **does not update PR status checks** (workflow_dispatch events are excluded from PR check status). The only path that updates PR checks is a code push. In this specific case, an empty commit is acceptable:
```bash
git commit --allow-empty -m "ci: re-trigger CI after runner failure
Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
git push
```

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
