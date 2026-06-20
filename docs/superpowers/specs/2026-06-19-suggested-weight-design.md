# Suggested Weight Based on 1RM — Design

**Date:** 2026-06-19  
**Status:** Draft

## Problem

During a WorkoutSession, users see reference weights (Previous, PR, Est. 1RM) in `ExerciseReferenceRow`.
They have no guidance on what weight to actually load for their next set given the exercise's target reps and AdvancedTechnique.

## Goal

Display a "Suggested" weight in `ExerciseReferenceRow` for each exercise in a live session, derived from the
estimated 1RM and the WorkoutExercise's target reps and AdvancedTechnique.

## Assumptions Made (Autonomous)

- **GVT → 60%:** The in-app description already states "60% of 1RM"; no user clarification needed.
- **SST, REST_PAUSE → Epley inverse:** User specified only Gironda and FST-7 as fixed-% cases; SST/REST_PAUSE are
  failure-based intensity techniques without a canonical fixed %, so the standard rep-based formula applies.
- **Fixed % midpoints:** Gironda (50–60%) → **55%**; FST-7 (60–70%) → **65%**.
- **toFailure exercises:** No suggestion (no meaningful rep target).
- **1RM unavailable:** Suggestion is `null` (user has no prior history for that exercise).

## Approach

Extend the existing `ExerciseReferenceWeights` data flow with a `suggestedWeightKg` field.
The backend computes it (keeping all weight math server-side); the frontend displays it identically to other
reference values.

## Backend Changes

### `SessionModels.kt`

Add one field to `ExerciseReferenceWeights`:

```kotlin
data class ExerciseReferenceWeights(
    val exerciseId: UUID,
    val previousWeightKg: BigDecimal?,
    val prWeightKg: BigDecimal?,
    val estimatedOneRepMaxKg: BigDecimal?,
    val suggestedWeightKg: BigDecimal?,   // NEW
)
```

### `WorkoutSessionService.getReferenceWeights`

Change from discarding the `WorkoutExercise` objects to building a lookup map by `exerciseId`:

```kotlin
val workoutExercises = workoutExerciseRepository
    .findAllByWorkoutGroupIdOrderByOrderIndex(session.workoutGroupId)
val exerciseMap = workoutExercises.associateBy { it.exerciseId }
val exerciseIds = workoutExercises.map { it.exerciseId }
return sessionQueryRepository.findReferenceWeights(userId, exerciseIds, sessionId, exerciseMap)
```

### `SessionQueryRepository.findReferenceWeights`

Accepts an additional `workoutExerciseMap: Map<UUID, WorkoutExercise>` parameter.
After computing the 1RM, calls `computeSuggestedWeight`:

```kotlin
ExerciseReferenceWeights(
    exerciseId = exerciseId,
    previousWeightKg = previousWeight,
    prWeightKg = personalRecord?.prWeight,
    estimatedOneRepMaxKg = oneRepMax,
    suggestedWeightKg = oneRepMax?.let { computeSuggestedWeight(it, workoutExerciseMap[exerciseId]) },
)
```

### Suggestion Formula

New top-level (file-private) function in `SessionQueryRepository.kt`:

| Condition | Formula | Notes |
|-----------|---------|-------|
| `workoutExercise == null` | `null` | No plan data available |
| `toFailure = true` | `null` | No rep target |
| `GIRONDA` | `1RM × 0.55` | Midpoint of 50–60% |
| `FST_7` | `1RM × 0.65` | Midpoint of 60–70% |
| `GVT` | `1RM × 0.60` | Per in-app GVT description |
| `SST`, `REST_PAUSE`, `null` | `1RM ÷ (1 + reps ÷ 30)` | Epley inverse |

Results rounded to 2 decimal places (`RoundingMode.HALF_UP`), matching the existing Epley output.

## Frontend Changes

### `sessionService.ts`

Add field to `ExerciseReferenceWeights` type:

```typescript
export interface ExerciseReferenceWeights {
  exerciseId: string
  previousWeightKg: number | null
  prWeightKg: number | null
  estimatedOneRepMaxKg: number | null
  suggestedWeightKg: number | null   // NEW
}
```

### `ExerciseReferenceRow.tsx`

Add a "Suggested" entry to the `values` array:

```typescript
referenceWeights.suggestedWeightKg != null
  ? `Suggested: ${formatDisplayWeight(referenceWeights.suggestedWeightKg, unit)}`
  : null,
```

### `SessionPage.tsx`

No changes. `ExerciseReferenceRow` already receives `referenceWeights` from the map.

## What Doesn't Change

- No new API endpoints
- No Flyway migration (computed value, not persisted)
- No changes to `SetInput`, `SetLog`, or the offline queue
- `ExerciseReferenceRow` display order: Previous → PR → Est. 1RM → **Suggested**

## Testing

### Backend

- Unit tests for `computeSuggestedWeight` covering all five techniques and `toFailure = true`
- `getReferenceWeights` returns `suggestedWeightKg = null` when 1RM is unavailable (no prior history)
- Integration test confirms the full response shape

### Frontend

- `ExerciseReferenceRow` renders "Suggested: X kg/lb" when `suggestedWeightKg` is non-null
- `ExerciseReferenceRow` renders nothing additional when `suggestedWeightKg` is null
- Weight converts correctly when unit is "lb"
- Uses `formatDisplayWeight` for test assertions (not hardcoded strings)
