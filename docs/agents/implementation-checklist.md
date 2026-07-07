# Implementation Checklist

Practices derived from retrospectives. Apply these before raising a PR.

## SVG responsive sizing

When changing any SVG component, verify **all three** are present before pushing:

1. `width="100%"` on the `<svg>` element
2. A `viewBox` covering the full coordinate space
3. `style={{ display: 'block', aspectRatio: '<w> / <h>' }}` matching the viewBox dimensions

> **Why**: `width="100%"` + `viewBox` alone falls back to the browser's 150px intrinsic default inside flex or grid containers. The `aspectRatio` style is required to anchor the rendered height.
>
> Also verify that all text inside the SVG uses a **SVG attribute** (`fontSize={N}`) rather than a Tailwind/CSS class (`text-[Npx]`). CSS font-size does not scale with the viewBox.

## Test helpers that mirror domain formulas

When a domain formula is validated (e.g., via a quick script), extract it as a named utility instead of re-implementing it inline in test helpers:

1. Create a small utility (e.g., `frontend/src/test/heatmapUtils.ts`) exporting the formula
2. Import it in test files — never hand-roll a "similar" expression
3. Keep the utility next to the implementation so drift is obvious in code review

> **Why**: Inline re-implementations silently diverge (off-by-one, wrong boundary) and make tests pass for the wrong reason, masking real regressions.

## Exact-value assertions for colours and tier mappings

When testing colour/tier mappings, always assert the **exact expected value**, not "any non-zero value":

```ts
// ✅ Good
expect(cell.getAttribute('fill')).toBe('#f0fdf4');

// ❌ Bad — passes even if the wrong tier is returned
expect(cell.getAttribute('fill')).not.toBe('#1e293b');
```

## Deterministic dates in integration tests

Never use `LocalDate.now()` (or equivalent) in tests that make many sequential DB writes:

- Use a fixed historical date: `logSetsOnDate(fixedDate = LocalDate.of(2025, 6, 1), count = N)`
- Using `now()` across multiple HTTP calls risks straddling UTC midnight in CI

## Rubber-duck review for layout and visual changes

Before pushing a PR that touches SVG, CSS layout, or responsive behaviour, run a rubber-duck review. These areas have invisible browser-rendering edge cases that are cheap to catch early and expensive to fix after a review cycle.

## Kotlin `assert()` is not a test assertion

Never use Kotlin's built-in `assert(...)` in integration tests:

```kotlin
// ❌ Bad — silently skipped when JVM assertions are disabled (the default in test JVMs)
assert(!result.responseHeaders.containsKey(HttpHeaders.WWW_AUTHENTICATE))

// ✅ Good — WebTestClient assertions always execute
.expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)

// ✅ Also good — JUnit assertions always execute
assertFalse(result.responseHeaders.containsKey(HttpHeaders.WWW_AUTHENTICATE))
```

> **Why**: Kotlin's `assert(...)` is a stdlib function. While it doesn't literally compile to the JVM `assert` bytecode instruction, it is implemented via `java.lang.RuntimeException` when assertions are enabled — but it is a no-op unless the JVM system property `kotlin.assert.always` is set or assertions are explicitly enabled at the call site. In Testcontainers/Spring Boot test suites, neither flag is set by default, so `assert(cond)` silently passes regardless of `cond`.

## Delay `URL.revokeObjectURL` in download helpers

After triggering a programmatic file download via an anchor click, never call `URL.revokeObjectURL` synchronously:

```ts
// ❌ Bad — can cancel the download before the browser reads the blob
a.click()
URL.revokeObjectURL(url)

// ✅ Good — 100ms delay gives the browser time to start reading the blob
a.click()
setTimeout(() => URL.revokeObjectURL(url), 100)
```

> **Why**: The browser's download machinery is asynchronous. Revoking the URL synchronously after `click()` can race with the browser's fetch of the blob data, causing empty or truncated downloads in Firefox and some versions of Safari.

## Introduce a `*Port` facade when a service needs ≥ 4 repositories

When a service class requires 4 or more repository constructor parameters, group related repositories into a `*Port` or `*Facade` class first:

```kotlin
// ❌ Bad — triggers LongParameterList (≥ 7 params) and TooManyFunctions (> 10 methods)
class ExportService(
    private val userRepository: UserRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val workoutGroupRepository: WorkoutGroupRepository,
    private val workoutExerciseRepository: WorkoutExerciseRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val setLogRepository: SetLogRepository,
)

// ✅ Good — group workout/session repos into a port; service stays within detekt thresholds
class WorkoutDataPort(
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val workoutGroupRepository: WorkoutGroupRepository,
    private val workoutExerciseRepository: WorkoutExerciseRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
)

class ExportService(
    private val userRepository: UserRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutDataPort: WorkoutDataPort,
    private val setLogRepository: SetLogRepository,
)
```

> **Why**: detekt's `LongParameterList.constructorThreshold = 7` triggers at 7+ parameters; `TooManyFunctions.thresholdInClasses = 11` triggers at 11+ class-level methods. Splitting into a port/facade keeps both thresholds clear and improves cohesion. Plan the port upfront — retrofitting it after detekt failures costs ~3 extra gate cycles.

