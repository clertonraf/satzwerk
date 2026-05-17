# [BE+FE] Workout Session Tracking

**Type:** AFK
**Blocked by:** #05 Workout Plan Builder

## What to build

The core training loop: start a `WorkoutSession` against a `WorkoutGroup`, log `SetLog` entries as the user trains, and complete the session ("push workout"). Exactly one session may be open at a time — starting a new one when one exists prompts resume or discard.

Key domain decisions (see CONTEXT.md):
- `SetLog` records `exerciseId`, `setNumber`, `weight` (always in kg — display layer converts), `reps`. The `rir` field was **dropped**.
- "Push workout" is UX copy; the domain entity is `WorkoutSession`.
- Weight is always stored as kg; lb display is a frontend concern.

### Backend

Flyway migrations for `workout_sessions`, `set_logs`.

- `POST /api/sessions` — start a session against a `workoutGroupId`; returns 409 if an open session already exists for that user
- `GET /api/sessions/open` — returns the current open session (if any), or 404
- `POST /api/sessions/{id}/set-logs` — append a `SetLog` (`exerciseId`, `setNumber`, `weight`, `reps`)
- `DELETE /api/sessions/{id}` — discard (hard delete open session + its set logs)
- `POST /api/sessions/{id}/complete` — mark session as completed (`completedAt = now`)
- `GET /api/sessions/history` — paginated list of completed sessions for the user

### Frontend

- On "Start Workout": check for open session → if found, show resume/discard modal
- Session screen:
  - Exercise checklist derived from the WorkoutGroup's WorkoutExercises
  - `SetInput` per exercise: weight (with kg/lb toggle stored as display preference only), reps, set number
  - Rest timer (client-side countdown, no backend involvement)
  - "Push Workout" button → calls complete endpoint, navigates to history or dashboard
- Zustand slice for active session state (survives background — feeds into #09 offline slice)

## Acceptance criteria

- [ ] Starting a session creates a `WorkoutSession` linked to the selected `WorkoutGroup`
- [ ] Starting a second session while one is open returns 409 and the UI shows a resume/discard prompt
- [ ] Discarding a session deletes it and all its `SetLog` entries
- [ ] Each logged set is stored with `exerciseId`, `setNumber`, `weight` (kg), `reps`
- [ ] Completing a session sets `completedAt` and it appears in history
- [ ] Completed sessions cannot have more set logs appended
- [ ] Weight is stored as kg regardless of the display unit selected
- [ ] History endpoint returns only the authenticated user's completed sessions
