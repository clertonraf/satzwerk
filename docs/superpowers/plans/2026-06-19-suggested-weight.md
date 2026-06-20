# Suggested Weight Based on 1RM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display a "Suggested" weight in `ExerciseReferenceRow` during a live WorkoutSession, derived from the estimated 1RM and the WorkoutExercise's target reps and AdvancedTechnique.

**Architecture:** Extend the existing `ExerciseReferenceWeights` data flow with a `suggestedWeightKg` field computed on the backend. A new `SuggestionCalculator.kt` holds the pure calculation logic (testable via unit tests). `SessionQueryRepository.findReferenceWeights` and `WorkoutSessionService.getReferenceWeights` are updated to carry the WorkoutExercise data needed to compute the suggestion. The frontend adds one display label in `ExerciseReferenceRow`.

**Tech Stack:** Kotlin, Spring WebFlux, R2DBC, JUnit 5, TypeScript, React, Vitest

---

## Files

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `backend/src/main/kotlin/com/satzwerk/sessions/SuggestionCalculator.kt` | Pure `computeSuggestedWeight` function |
| Create | `backend/src/test/kotlin/com/satzwerk/sessions/SuggestionCalculatorTest.kt` | Unit tests for all technique paths |
| Modify | `backend/src/main/kotlin/com/satzwerk/sessions/SessionModels.kt` | Add `suggestedWeightKg: BigDecimal?` field |
| Modify | `backend/src/main/kotlin/com/satzwerk/sessions/SessionQueryRepository.kt` | Accept `workoutExerciseMap`, call `computeSuggestedWeight` |
| Modify | `backend/src/main/kotlin/com/satzwerk/sessions/WorkoutSessionService.kt` | Keep full `WorkoutExercise` list, build map |
| Modify | `backend/src/test/kotlin/com/satzwerk/sessions/WorkoutSessionIntegrationTest.kt` | Add integration tests for `suggestedWeightKg` |
| Modify | `frontend/src/services/sessionService.ts` | Add `suggestedWeightKg: number \| null` to interface |
| Modify | `frontend/src/features/sessions/ExerciseReferenceRow.tsx` | Display "Suggested: X kg/lb" |
| Modify | `frontend/src/features/sessions/__tests__/ExerciseReferenceRow.test.tsx` | Add suggested weight tests; update existing mocks |

---

## Task 1: `SuggestionCalculator` — unit tests first, then implementation

**Files:**
- Create: `backend/src/main/kotlin/com/satzwerk/sessions/SuggestionCalculator.kt`
- Create: `backend/src/test/kotlin/com/satzwerk/sessions/SuggestionCalculatorTest.kt`

### Technique rules

| Condition | Formula | Expected value for 1RM=116.67 |
|-----------|---------|-------------------------------|
| `workoutExercise == null` | `null` | — |
| `toFailure = true` | `null` | — |
| `GIRONDA` | `1RM × 0.55` | 64.17 |
| `FST_7` | `1RM × 0.65` | 75.84 |
| `GVT` | `1RM × 0.60` | 70.00 |
| `SST`, `REST_PAUSE`, `null` | `1RM ÷ (1 + reps÷30)` | 92.11 (8 reps), 83.34 (10 reps) |

### Steps

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/kotlin/com/satzwerk/sessions/SuggestionCalculatorTest.kt`:

```kotlin
package com.satzwerk.sessions

import com.satzwerk.workouts.WorkoutExercise
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

private fun exercise(
    reps: Int,
    toFailure: Boolean = false,
    advancedTechnique: String? = null,
) = WorkoutExercise(
    id = UUID.randomUUID(),
    workoutGroupId = UUID.randomUUID(),
    exerciseId = UUID.randomUUID(),
    sets = 3,
    reps = reps,
    toFailure = toFailure,
    advancedTechnique = advancedTechnique,
)

class SuggestionCalculatorTest {
    private val oneRepMax = BigDecimal("116.67")

    @Test
    fun `returns null when workoutExercise is null`() {
        assertNull(computeSuggestedWeight(oneRepMax, null))
    }

    @Test
    fun `returns null when toFailure is true`() {
        assertNull(computeSuggestedWeight(oneRepMax, exercise(reps = 8, toFailure = true)))
    }

    @Test
    fun `uses Epley inverse for no technique`() {
        // 116.67 / (1 + 8/30) = 92.11
        assertEquals(BigDecimal("92.11"), computeSuggestedWeight(oneRepMax, exercise(reps = 8)))
    }

    @Test
    fun `uses Epley inverse for SST`() {
        // 116.67 / (1 + 10/30) = 116.67 / 1.3333333333 = 87.50
        assertEquals(BigDecimal("87.50"), computeSuggestedWeight(oneRepMax, exercise(reps = 10, advancedTechnique = "SST")))
    }

    @Test
    fun `uses Epley inverse for REST_PAUSE`() {
        // 116.67 / (1 + 8/30) = 92.11
        assertEquals(BigDecimal("92.11"), computeSuggestedWeight(oneRepMax, exercise(reps = 8, advancedTechnique = "REST_PAUSE")))
    }

    @Test
    fun `uses 55 percent for GIRONDA`() {
        // 116.67 * 0.55 = 64.1685 -> 64.17
        assertEquals(BigDecimal("64.17"), computeSuggestedWeight(oneRepMax, exercise(reps = 8, advancedTechnique = "GIRONDA")))
    }

    @Test
    fun `uses 65 percent for FST_7`() {
        // 116.67 * 0.65 = 75.8355 -> 75.84
        assertEquals(BigDecimal("75.84"), computeSuggestedWeight(oneRepMax, exercise(reps = 12, advancedTechnique = "FST_7")))
    }

    @Test
    fun `uses 60 percent for GVT`() {
        // 116.67 * 0.60 = 70.002 -> 70.00
        assertEquals(BigDecimal("70.00"), computeSuggestedWeight(oneRepMax, exercise(reps = 10, advancedTechnique = "GVT")))
    }
}
```

- [ ] **Step 2: Verify tests fail**

```bash
cd backend && ./gradlew test --tests "com.satzwerk.sessions.SuggestionCalculatorTest" --no-daemon 2>&1 | tail -20
```

Expected: compilation failure — `computeSuggestedWeight` does not exist yet.

- [ ] **Step 3: Create `SuggestionCalculator.kt`**

Create `backend/src/main/kotlin/com/satzwerk/sessions/SuggestionCalculator.kt`:

```kotlin
package com.satzwerk.sessions

import com.satzwerk.workouts.AdvancedTechnique
import com.satzwerk.workouts.WorkoutExercise
import java.math.BigDecimal
import java.math.RoundingMode

private const val SUGGESTION_REPS_SCALE = 10
private const val GIRONDA_PERCENT = "0.55"
private const val FST_7_PERCENT = "0.65"
private const val GVT_PERCENT = "0.60"
private const val EPLEY_DIVISOR = "30"

internal fun computeSuggestedWeight(
    oneRepMaxKg: BigDecimal,
    workoutExercise: WorkoutExercise?,
): BigDecimal? {
    if (workoutExercise == null) return null
    if (workoutExercise.toFailure) return null

    val technique = workoutExercise.advancedTechnique?.let { AdvancedTechnique.valueOf(it) }

    return when (technique) {
        AdvancedTechnique.GIRONDA ->
            oneRepMaxKg.multiply(BigDecimal(GIRONDA_PERCENT)).setScale(2, RoundingMode.HALF_UP)

        AdvancedTechnique.FST_7 ->
            oneRepMaxKg.multiply(BigDecimal(FST_7_PERCENT)).setScale(2, RoundingMode.HALF_UP)

        AdvancedTechnique.GVT ->
            oneRepMaxKg.multiply(BigDecimal(GVT_PERCENT)).setScale(2, RoundingMode.HALF_UP)

        null, AdvancedTechnique.SST, AdvancedTechnique.REST_PAUSE -> {
            val ratio = workoutExercise.reps.toBigDecimal()
                .divide(BigDecimal(EPLEY_DIVISOR), SUGGESTION_REPS_SCALE, RoundingMode.HALF_UP)
            val divisor = BigDecimal.ONE.add(ratio)
            oneRepMaxKg.divide(divisor, 2, RoundingMode.HALF_UP)
        }
    }
}
```

- [ ] **Step 4: Run tests and verify they pass**

```bash
cd backend && ./gradlew test --tests "com.satzwerk.sessions.SuggestionCalculatorTest" --no-daemon 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all 8 tests pass.

- [ ] **Step 5: Commit**

```bash
cd backend
git add src/main/kotlin/com/satzwerk/sessions/SuggestionCalculator.kt \
        src/test/kotlin/com/satzwerk/sessions/SuggestionCalculatorTest.kt
git commit -m "feat(sessions): add SuggestionCalculator for 1RM-based weight suggestions

Computes suggested training weight from estimated 1RM:
- GIRONDA: 55% of 1RM
- FST_7: 65% of 1RM
- GVT: 60% of 1RM
- SST, REST_PAUSE, no technique: Epley inverse (1RM / (1 + reps/30))
- toFailure exercises: null (no rep target)

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 2: Backend — wire `suggestedWeightKg` through model, service, and repository

**Files:**
- Modify: `backend/src/main/kotlin/com/satzwerk/sessions/SessionModels.kt`
- Modify: `backend/src/main/kotlin/com/satzwerk/sessions/SessionQueryRepository.kt`
- Modify: `backend/src/main/kotlin/com/satzwerk/sessions/WorkoutSessionService.kt`

- [ ] **Step 1: Add `suggestedWeightKg` to `ExerciseReferenceWeights`**

In `backend/src/main/kotlin/com/satzwerk/sessions/SessionModels.kt`, change:

```kotlin
data class ExerciseReferenceWeights(
    val exerciseId: UUID,
    val previousWeightKg: BigDecimal?,
    val prWeightKg: BigDecimal?,
    val estimatedOneRepMaxKg: BigDecimal?,
)
```

To:

```kotlin
data class ExerciseReferenceWeights(
    val exerciseId: UUID,
    val previousWeightKg: BigDecimal?,
    val prWeightKg: BigDecimal?,
    val estimatedOneRepMaxKg: BigDecimal?,
    val suggestedWeightKg: BigDecimal?,
)
```

- [ ] **Step 2: Update `SessionQueryRepository.findReferenceWeights` to accept exercise map and compute suggestion**

In `backend/src/main/kotlin/com/satzwerk/sessions/SessionQueryRepository.kt`:

Change the signature of `findReferenceWeights` from:
```kotlin
suspend fun findReferenceWeights(
    userId: UUID,
    exerciseIds: List<UUID>,
    currentSessionId: UUID,
): List<ExerciseReferenceWeights>
```

To:
```kotlin
suspend fun findReferenceWeights(
    userId: UUID,
    exerciseIds: List<UUID>,
    currentSessionId: UUID,
    workoutExerciseMap: Map<UUID, com.satzwerk.workouts.WorkoutExercise>,
): List<ExerciseReferenceWeights>
```

And update the body to pass the suggestion:
```kotlin
return exerciseIds.map { exerciseId ->
    val previousWeight = previousWeights[exerciseId]?.previousWeight
    val personalRecord = personalRecords[exerciseId]
    val oneRepMax = personalRecord.toEstimatedOneRepMaxKg()
    ExerciseReferenceWeights(
        exerciseId = exerciseId,
        previousWeightKg = previousWeight,
        prWeightKg = personalRecord?.prWeight,
        estimatedOneRepMaxKg = oneRepMax,
        suggestedWeightKg = oneRepMax?.let {
            computeSuggestedWeight(it, workoutExerciseMap[exerciseId])
        },
    )
}
```

Also add the import at the top of the file. The full updated `findReferenceWeights` function (the public method and the private helpers are already in the file — only change the public method's signature and the mapping block):

```kotlin
suspend fun findReferenceWeights(
    userId: UUID,
    exerciseIds: List<UUID>,
    currentSessionId: UUID,
    workoutExerciseMap: Map<UUID, com.satzwerk.workouts.WorkoutExercise>,
): List<ExerciseReferenceWeights> {
    if (exerciseIds.isEmpty()) {
        return emptyList()
    }

    val previousWeights = findPreviousWeights(userId, exerciseIds, currentSessionId)
    val personalRecords = findPersonalRecords(userId, exerciseIds)

    return exerciseIds.map { exerciseId ->
        val previousWeight = previousWeights[exerciseId]?.previousWeight
        val personalRecord = personalRecords[exerciseId]
        val oneRepMax = personalRecord.toEstimatedOneRepMaxKg()
        ExerciseReferenceWeights(
            exerciseId = exerciseId,
            previousWeightKg = previousWeight,
            prWeightKg = personalRecord?.prWeight,
            estimatedOneRepMaxKg = oneRepMax,
            suggestedWeightKg = oneRepMax?.let {
                computeSuggestedWeight(it, workoutExerciseMap[exerciseId])
            },
        )
    }
}
```

- [ ] **Step 3: Update `WorkoutSessionService.getReferenceWeights` to build and pass the exercise map**

In `backend/src/main/kotlin/com/satzwerk/sessions/WorkoutSessionService.kt`, change:

```kotlin
suspend fun getReferenceWeights(
    userId: UUID,
    sessionId: UUID,
): List<ExerciseReferenceWeights> {
    val session = getOwnedSession(userId, sessionId)
    val exerciseIds =
        workoutExerciseRepository.findAllByWorkoutGroupIdOrderByOrderIndex(session.workoutGroupId)
            .map { it.exerciseId }

    return sessionQueryRepository.findReferenceWeights(userId, exerciseIds, sessionId)
}
```

To:

```kotlin
suspend fun getReferenceWeights(
    userId: UUID,
    sessionId: UUID,
): List<ExerciseReferenceWeights> {
    val session = getOwnedSession(userId, sessionId)
    val workoutExercises =
        workoutExerciseRepository.findAllByWorkoutGroupIdOrderByOrderIndex(session.workoutGroupId)
    val exerciseIds = workoutExercises.map { it.exerciseId }
    val workoutExerciseMap = workoutExercises.associateBy { it.exerciseId }

    return sessionQueryRepository.findReferenceWeights(userId, exerciseIds, sessionId, workoutExerciseMap)
}
```

- [ ] **Step 4: Compile to check for errors**

```bash
cd backend && ./gradlew compileTestKotlin --no-daemon 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Run pre-push checks**

```bash
cd backend && ./gradlew ktlintCheck detekt --no-daemon 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`. If ktlint reports import ordering issues, run `./gradlew ktlintFormat --no-daemon` then re-run `compileTestKotlin` to confirm no imports were dropped.

- [ ] **Step 6: Commit**

```bash
cd backend
git add src/main/kotlin/com/satzwerk/sessions/SessionModels.kt \
        src/main/kotlin/com/satzwerk/sessions/SessionQueryRepository.kt \
        src/main/kotlin/com/satzwerk/sessions/WorkoutSessionService.kt
git commit -m "feat(sessions): wire suggestedWeightKg through ExerciseReferenceWeights

- Add suggestedWeightKg field to ExerciseReferenceWeights
- getReferenceWeights keeps full WorkoutExercise list and builds map
- findReferenceWeights accepts exercise map, computes suggestion via
  computeSuggestedWeight

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 3: Backend integration tests for `suggestedWeightKg`

**Files:**
- Modify: `backend/src/test/kotlin/com/satzwerk/sessions/WorkoutSessionIntegrationTest.kt`

The existing `createGroup` helper at the bottom of the test class creates a `WorkoutExercise` with `reps = 8` and no `advancedTechnique`. New tests add a `createGroupWithTechnique` helper.

**Epley inverse for 8 reps from 1RM 116.67:**
`116.67 / (1 + 8/30) = 116.67 / 1.2666666667 = 92.11`

**Gironda 55% from 1RM 116.67:**
`116.67 × 0.55 = 64.17`

- [ ] **Step 1: Add tests for `suggestedWeightKg` in `WorkoutSessionIntegrationTest`**

Add the following tests to the `WorkoutSessionIntegrationTest` class body (before the private helpers):

```kotlin
@Test
fun `reference weights returns null suggested weight when exercise has no history`() {
    val session = startSession()

    client
        .get()
        .uri("/api/sessions/${session.id}/reference-weights")
        .header("Authorization", "Bearer $authToken")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$[0].suggestedWeightKg").isEmpty
}

@Test
fun `reference weights calculates suggested weight using Epley inverse for no technique`() {
    // createGroup uses reps=8, no technique
    // log 100kg x 5 reps -> 1RM = 116.67 -> suggested for 8 reps = 92.11
    val completedSession = startSession()
    addSetLog(completedSession.id, BigDecimal("100.0"), reps = 5)
    completeSession(completedSession.id)

    val currentSession = startSession()

    client
        .get()
        .uri("/api/sessions/${currentSession.id}/reference-weights")
        .header("Authorization", "Bearer $authToken")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$[0].suggestedWeightKg").isEqualTo(92.11)
}

@Test
fun `reference weights calculates suggested weight at 55 percent for GIRONDA technique`() {
    // log 100kg x 5 reps -> 1RM = 116.67 -> Gironda 55% = 64.17
    val suffix = UUID.randomUUID()
    val token = registerAndLogin("gironda-$suffix@test.com", "password123", "Gironda User")
    val eid = createExercise(token, "Cable Crossover", "CHEST")
    val planId = createPlan(token, "Gironda Plan")
    activatePlan(token, planId)
    val gid = createGroupWithTechnique(token, planId, "Gironda Day", eid, reps = 8, technique = "GIRONDA")

    val completedSession = startSessionFor(token, gid)
    addSetLogFor(token, completedSession.id, eid, BigDecimal("100.0"), reps = 5)
    completeSessionFor(token, completedSession.id)

    val currentSession = startSessionFor(token, gid)

    client
        .get()
        .uri("/api/sessions/${currentSession.id}/reference-weights")
        .header("Authorization", "Bearer $token")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$[0].suggestedWeightKg").isEqualTo(64.17)
}
```

- [ ] **Step 2: Add private helpers for the Gironda test**

Add these private helper methods at the bottom of `WorkoutSessionIntegrationTest`, alongside the existing helpers:

```kotlin
private fun createGroupWithTechnique(
    token: String,
    planId: UUID,
    title: String,
    exerciseId: UUID,
    reps: Int,
    technique: String,
): UUID {
    val groupResponse =
        client
            .post()
            .uri("/api/plans/$planId/groups")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("title" to title))
            .exchange()
            .expectStatus().isCreated
            .expectBody(WorkoutGroupResponse::class.java)
            .returnResult()
            .responseBody!!

    val groupId = groupResponse.id

    client
        .post()
        .uri("/api/plans/$planId/groups/$groupId/exercises")
        .header("Authorization", "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            mapOf(
                "exerciseId" to exerciseId,
                "sets" to 4,
                "reps" to reps,
                "advancedTechnique" to technique,
            ),
        ).exchange()
        .expectStatus().isCreated

    return groupId
}

private fun startSessionFor(
    token: String,
    groupId: UUID,
): WorkoutSessionResponse =
    client
        .post()
        .uri("/api/sessions")
        .header("Authorization", "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(mapOf("workoutGroupId" to groupId))
        .exchange()
        .expectStatus().isCreated
        .expectBody(WorkoutSessionResponse::class.java)
        .returnResult()
        .responseBody!!

private fun addSetLogFor(
    token: String,
    sessionId: UUID,
    exerciseId: UUID,
    weight: BigDecimal,
    reps: Int,
): SetLogResponse =
    client
        .post()
        .uri("/api/sessions/$sessionId/set-logs")
        .header("Authorization", "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            mapOf(
                "exerciseId" to exerciseId,
                "setNumber" to 1,
                "weight" to weight,
                "reps" to reps,
            ),
        ).exchange()
        .expectStatus().isCreated
        .expectBody(SetLogResponse::class.java)
        .returnResult()
        .responseBody!!

private fun completeSessionFor(
    token: String,
    sessionId: UUID,
): WorkoutSessionResponse =
    client
        .post()
        .uri("/api/sessions/$sessionId/complete")
        .header("Authorization", "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(mapOf("notes" to null))
        .exchange()
        .expectStatus().isOk
        .expectBody(WorkoutSessionResponse::class.java)
        .returnResult()
        .responseBody!!
```

- [ ] **Step 3: Run the integration tests**

```bash
cd backend && ./gradlew test --tests "com.satzwerk.sessions.WorkoutSessionIntegrationTest" --no-daemon 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`, all tests pass (Docker must be running for Testcontainers).

- [ ] **Step 4: Run all backend tests**

```bash
cd backend && ./gradlew test --no-daemon 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
cd backend
git add src/test/kotlin/com/satzwerk/sessions/WorkoutSessionIntegrationTest.kt
git commit -m "test(sessions): add integration tests for suggestedWeightKg

Covers: null when no history, Epley inverse for no technique,
Gironda 55% fixed percentage.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 4: Frontend — type and display

**Files:**
- Modify: `frontend/src/services/sessionService.ts`
- Modify: `frontend/src/features/sessions/ExerciseReferenceRow.tsx`
- Modify: `frontend/src/features/sessions/__tests__/ExerciseReferenceRow.test.tsx`

- [ ] **Step 1: Add `suggestedWeightKg` to the TypeScript type**

In `frontend/src/services/sessionService.ts`, change:

```typescript
export interface ExerciseReferenceWeights {
  exerciseId: string
  previousWeightKg: number | null
  prWeightKg: number | null
  estimatedOneRepMaxKg: number | null
}
```

To:

```typescript
export interface ExerciseReferenceWeights {
  exerciseId: string
  previousWeightKg: number | null
  prWeightKg: number | null
  estimatedOneRepMaxKg: number | null
  suggestedWeightKg: number | null
}
```

- [ ] **Step 2: Display "Suggested" in `ExerciseReferenceRow`**

In `frontend/src/features/sessions/ExerciseReferenceRow.tsx`, change the `values` array:

```typescript
const values = [
  referenceWeights.previousWeightKg != null
    ? `Previous: ${formatDisplayWeight(referenceWeights.previousWeightKg, unit)}`
    : null,
  referenceWeights.prWeightKg != null ? `PR: ${formatDisplayWeight(referenceWeights.prWeightKg, unit)}` : null,
  referenceWeights.estimatedOneRepMaxKg != null
    ? `Est. 1RM: ${formatDisplayWeight(referenceWeights.estimatedOneRepMaxKg, unit)}`
    : null,
].filter((value): value is string => value !== null)
```

To:

```typescript
const values = [
  referenceWeights.previousWeightKg != null
    ? `Previous: ${formatDisplayWeight(referenceWeights.previousWeightKg, unit)}`
    : null,
  referenceWeights.prWeightKg != null ? `PR: ${formatDisplayWeight(referenceWeights.prWeightKg, unit)}` : null,
  referenceWeights.estimatedOneRepMaxKg != null
    ? `Est. 1RM: ${formatDisplayWeight(referenceWeights.estimatedOneRepMaxKg, unit)}`
    : null,
  referenceWeights.suggestedWeightKg != null
    ? `Suggested: ${formatDisplayWeight(referenceWeights.suggestedWeightKg, unit)}`
    : null,
].filter((value): value is string => value !== null)
```

- [ ] **Step 3: Update existing tests to include `suggestedWeightKg: null`**

In `frontend/src/features/sessions/__tests__/ExerciseReferenceRow.test.tsx`, update every `referenceWeights` object literal to include `suggestedWeightKg: null`. There are 4 render calls that supply a `referenceWeights` object:

**Test: "renders nothing when all reference weight fields are null and not loading"** — change:
```typescript
referenceWeights={{
  exerciseId: 'exercise-1',
  previousWeightKg: null,
  prWeightKg: null,
  estimatedOneRepMaxKg: null,
}}
```
To:
```typescript
referenceWeights={{
  exerciseId: 'exercise-1',
  previousWeightKg: null,
  prWeightKg: null,
  estimatedOneRepMaxKg: null,
  suggestedWeightKg: null,
}}
```

**Test: "shows all reference weights in kilograms"** — change:
```typescript
referenceWeights={{
  exerciseId: 'exercise-1',
  previousWeightKg: 80,
  prWeightKg: 100,
  estimatedOneRepMaxKg: 116.67,
}}
```
To:
```typescript
referenceWeights={{
  exerciseId: 'exercise-1',
  previousWeightKg: 80,
  prWeightKg: 100,
  estimatedOneRepMaxKg: 116.67,
  suggestedWeightKg: null,
}}
```

**Test: "converts reference weights to pounds when unit is lb"** — change:
```typescript
referenceWeights={{
  exerciseId: 'exercise-1',
  previousWeightKg: 80,
  prWeightKg: 100,
  estimatedOneRepMaxKg: 116.67,
}}
```
To:
```typescript
referenceWeights={{
  exerciseId: 'exercise-1',
  previousWeightKg: 80,
  prWeightKg: 100,
  estimatedOneRepMaxKg: 116.67,
  suggestedWeightKg: null,
}}
```

**Test: "shows only available reference weight fields"** — change:
```typescript
referenceWeights={{
  exerciseId: 'exercise-1',
  previousWeightKg: null,
  prWeightKg: 100,
  estimatedOneRepMaxKg: 116.67,
}}
```
To:
```typescript
referenceWeights={{
  exerciseId: 'exercise-1',
  previousWeightKg: null,
  prWeightKg: 100,
  estimatedOneRepMaxKg: 116.67,
  suggestedWeightKg: null,
}}
```

- [ ] **Step 4: Add new tests for `suggestedWeightKg`**

Add these tests inside the `describe('ExerciseReferenceRow', ...)` block in `ExerciseReferenceRow.test.tsx`:

```typescript
it('shows suggested weight in kg when suggestedWeightKg is non-null', () => {
  render(
    <ExerciseReferenceRow
      referenceWeights={{
        exerciseId: 'exercise-1',
        previousWeightKg: null,
        prWeightKg: null,
        estimatedOneRepMaxKg: null,
        suggestedWeightKg: 92.11,
      }}
      isLoading={false}
      unit="kg"
    />
  )

  expect(screen.getByText(`Suggested: ${formatDisplayWeight(92.11, 'kg')}`)).toBeInTheDocument()
})

it('converts suggested weight to pounds when unit is lb', () => {
  render(
    <ExerciseReferenceRow
      referenceWeights={{
        exerciseId: 'exercise-1',
        previousWeightKg: null,
        prWeightKg: null,
        estimatedOneRepMaxKg: null,
        suggestedWeightKg: 92.11,
      }}
      isLoading={false}
      unit="lb"
    />
  )

  expect(screen.getByText(`Suggested: ${formatDisplayWeight(92.11, 'lb')}`)).toBeInTheDocument()
})

it('does not show suggested weight when suggestedWeightKg is null', () => {
  render(
    <ExerciseReferenceRow
      referenceWeights={{
        exerciseId: 'exercise-1',
        previousWeightKg: null,
        prWeightKg: null,
        estimatedOneRepMaxKg: null,
        suggestedWeightKg: null,
      }}
      isLoading={false}
      unit="kg"
    />
  )

  expect(screen.queryByText(/suggested/i)).not.toBeInTheDocument()
})

it('renders nothing when all fields including suggestedWeightKg are null', () => {
  render(
    <ExerciseReferenceRow
      referenceWeights={{
        exerciseId: 'exercise-1',
        previousWeightKg: null,
        prWeightKg: null,
        estimatedOneRepMaxKg: null,
        suggestedWeightKg: null,
      }}
      isLoading={false}
      unit="kg"
    />
  )

  expect(screen.queryByText(/suggested/i)).not.toBeInTheDocument()
  expect(screen.queryByText(/previous/i)).not.toBeInTheDocument()
})
```

- [ ] **Step 5: Run frontend tests**

```bash
cd frontend && pnpm test -- src/features/sessions/__tests__/ExerciseReferenceRow.test.tsx 2>&1 | tail -20
```

Expected: all tests pass.

- [ ] **Step 6: Run full frontend test suite and lint**

```bash
cd frontend && pnpm test 2>&1 | tail -20 && pnpm lint 2>&1 | tail -10
```

Expected: all tests pass, no lint errors.

- [ ] **Step 7: Run TypeScript type check**

```bash
cd frontend && npx tsc -b --noEmit 2>&1 | tail -20
```

Expected: no errors.

- [ ] **Step 8: Commit**

```bash
cd frontend
git add src/services/sessionService.ts \
        src/features/sessions/ExerciseReferenceRow.tsx \
        src/features/sessions/__tests__/ExerciseReferenceRow.test.tsx
git commit -m "feat(sessions): display suggested weight in ExerciseReferenceRow

Add suggestedWeightKg to ExerciseReferenceWeights type and show
'Suggested: X kg/lb' in ExerciseReferenceRow alongside Previous,
PR, and Est. 1RM labels.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Self-Review Checklist

- [x] `SuggestionCalculator` covers all 5 techniques + `toFailure` + null 1RM
- [x] `suggestedWeightKg` field added to backend model and frontend type
- [x] `WorkoutSessionService` keeps full WorkoutExercise list (not just IDs)
- [x] `findReferenceWeights` accepts exercise map and computes suggestion
- [x] Integration tests cover: null history, Epley inverse, Gironda 55%
- [x] All existing `ExerciseReferenceRow` test objects updated with `suggestedWeightKg: null`
- [x] New frontend tests use `formatDisplayWeight` (not hardcoded strings)
- [x] No `@Suppress`, `@ts-ignore`, or `// eslint-disable` added
- [x] No Flyway migration needed (computed value, not persisted)
- [x] Offline behaviour unaffected (reference weights excluded from offline path)
