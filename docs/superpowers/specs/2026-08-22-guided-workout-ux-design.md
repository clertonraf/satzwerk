# Guided workout UX for Satzwerk

## Goal

Make an open **WorkoutSession** feel guided without changing Satzwerk's current backend session model. The user should see one current **WorkoutExercise** at a time, know what to do next, and move through the **WorkoutGroup** with less scanning and less hesitation.

## Reference product behavior from openGym

openGym's guided workout flow keeps the session centered on one active step.

- The user can launch today's planned workout directly.
- The active workout view focuses on one exercise or superset at a time.
- The screen shows the most relevant context for that step: current position, last-time performance, working values, and immediate next action.
- Navigation between steps is explicit, and progress through the workout is always visible.

The result is a training flow that feels like guidance, not a checklist.

## Current Satzwerk behavior

Satzwerk already has the right core domain and transport behavior for this feature.

- A **WorkoutSession** can be started, resumed, completed, or discarded.
- **SetLog** entries can be created, updated, and deleted.
- The UI already knows the ordered **WorkoutExercise** list in the current **WorkoutGroup**.
- The frontend already fetches reference weights per exercise.
- Offline queue behavior already protects set logging while disconnected.

Today, `SessionPage` shows the whole **WorkoutGroup** at once. Every exercise competes for attention. The user must decide where to look next, which set to log next, and whether a given exercise is already complete.

## Proposed product behavior

Once a **WorkoutSession** is open, Satzwerk should treat one **WorkoutExercise** as current.

The main session surface should show:

- current **WorkoutExercise** name
- current position inside the **WorkoutGroup**
- prescribed sets and reps
- logged sets for the current step
- reference-weight hint for the current **Exercise**
- one clear next action for logging the next set

Secondary actions should remain available but less prominent:

- move to previous **WorkoutExercise**
- move to next **WorkoutExercise**
- jump to another **WorkoutExercise**
- forfeit the **WorkoutSession**

When all prescribed work is done, the UI should shift emphasis to completing the **WorkoutSession**.

## Recommended scope for the first slice

This slice should cover guided workout execution only.

It should not include:

- rest timer
- body-weight prompt before training
- keep-screen-awake behavior
- progression rules
- supersets
- timed exercises
- workout-day auto-selection beyond the current start flow

Those can follow later as separate slices.

## Frontend design

### 1. Guided session state

Add a UI-only guided-session layer on top of `useWorkoutSessionMachine`.

Responsibilities:

- derive the ordered **WorkoutExercise** list from the current **WorkoutGroup**
- compute which **WorkoutExercise** is the first incomplete one
- track the current step index
- expose navigation actions: previous, next, jump
- expose completion state for the current step and the whole **WorkoutGroup**

This state should not be persisted to the backend in v1. If the page reloads, the current step can be recomputed from existing **SetLog** data.

### 2. SessionWorkout layout

Replace the all-exercises-at-once layout with a focused guided layout.

Recommended structure:

1. workout progress header
2. current-step card
3. current `ExerciseSection`
4. previous/next navigation
5. optional jump list or drawer for other **WorkoutExercise** steps
6. session-level actions

The current-step card should communicate:

- "**WorkoutExercise** 2 of 5"
- exercise name
- target set count
- current completion status
- plan name or **WorkoutGroup** title only if it adds orientation

### 3. Step advancement rules

- When the session opens, focus the first incomplete **WorkoutExercise**.
- After a **SetLog** is added, stay on the current **WorkoutExercise** until its prescribed sets are complete.
- When the current **WorkoutExercise** becomes complete, advance automatically to the next incomplete one.
- If the user navigates manually, respect that choice until the next completion event.
- When every **WorkoutExercise** is complete, keep the last step visible and promote the complete-session action.

### 4. Reference-weight usage

Use the existing reference-weight query as the lightweight guidance mechanism for v1.

Do not add progression logic yet. The current guided step only needs to answer, "What should I do now?" and "What did I do last time?"

## Data flow

The feature should reuse the existing session flow.

- `useWorkoutSessionMachine` remains the source of truth for **WorkoutSession** state and **SetLog** mutations.
- `useSessionQueries` continues to provide the **WorkoutGroup** catalog and reference weights.
- The new guided-session layer derives step state from `session.setLogs`, pending set logs, and ordered **WorkoutExercise** metadata.
- The existing offline queue remains unchanged. Guided progression reacts to confirmed and pending logs the same way the current UI does.

## Error handling and edge cases

- If the current **WorkoutGroup** cannot be loaded, keep the existing error state.
- If a **WorkoutExercise** has no reference-weight history, show no hint rather than a fake default.
- If a user deletes a logged set, recompute the current guided step immediately.
- If the session is resumed after interruption, recompute the current step from saved **SetLog** data.
- If the **WorkoutGroup** is empty, keep the current empty-state behavior.

## Validation

The feature is successful when all of the following are true:

- A user can complete a **WorkoutSession** while interacting with one focused **WorkoutExercise** at a time.
- The user does not need to scan the full **WorkoutGroup** to know the next action.
- Navigation backward and forward works without corrupting **SetLog** data.
- Existing offline queue behavior still works for guided logging.
- Completing or forfeiting a **WorkoutSession** still behaves exactly as it does today.

## Non-goals

- changing backend routes or persistence for **WorkoutSession**
- changing **SetLog** structure
- adding timers or notifications
- introducing progression prescriptions
- redesigning the startup flow beyond what is needed to enter an open session

## Next implementation slices after this one

After the focused guided-workout slice lands, the next best follow-up slices are:

1. rest timer inside guided session flow
2. startup improvements for "today's workout"
3. body-weight prompt before session start
4. keep-screen-awake behavior during an open **WorkoutSession**
