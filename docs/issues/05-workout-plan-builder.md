# [BE+FE] Workout Plan Builder

**Type:** AFK
**Blocked by:** #04 Exercise Management

## What to build

Full CRUD for `WorkoutPlan`, `WorkoutGroup`, and `WorkoutExercise`, plus plan activation. A user can build a structured workout plan, organise it into named groups, assign exercises with targets, and activate exactly one plan at a time.

Key domain decisions (see CONTEXT.md and ADRs):
- `WorkoutGroup` has `title` and `orderIndex` only — the `code` field was dropped
- `WorkoutExercise.reps` is an integer (fixed target)
- `advancedTechnique` is a nullable enum: `SST`, `REST_PAUSE`, `GVT`, `FST_7`, `GIRONDA`
- `WorkoutPlan.source` is always `MANUAL` for MVP (`WorkoutSource` enum kept for future import)
- Activating a plan deactivates all other plans for that user (exactly-one-active invariant)

### Backend

Flyway migrations for `workout_plans`, `workout_groups`, `workout_exercises`.

WorkoutPlan:
- `POST /api/plans`
- `GET /api/plans` — user's plans
- `GET /api/plans/{id}`
- `PATCH /api/plans/{id}` — rename, change active status
- `DELETE /api/plans/{id}`
- `POST /api/plans/{id}/activate` — sets `isActive = true`, deactivates all others atomically

WorkoutGroup (nested under plan):
- `POST /api/plans/{planId}/groups`
- `PATCH /api/plans/{planId}/groups/{groupId}`
- `DELETE /api/plans/{planId}/groups/{groupId}`

WorkoutExercise (nested under group):
- `POST /api/plans/{planId}/groups/{groupId}/exercises`
- `PATCH /api/plans/{planId}/groups/{groupId}/exercises/{exerciseId}`
- `DELETE /api/plans/{planId}/groups/{groupId}/exercises/{exerciseId}`

### Frontend

- Plan list: shows all plans, active plan highlighted
- Plan builder: create/edit plan name, add/edit/delete WorkoutGroups, add/edit/delete WorkoutExercises per group
- WorkoutExercise form: pick from user's Exercise catalog, set target sets (integer), target reps (integer), optional advancedTechnique
- Activate plan button with confirmation

## Acceptance criteria

- [ ] User can create a WorkoutPlan with one or more WorkoutGroups
- [ ] Each WorkoutGroup has a title and contains one or more WorkoutExercises
- [ ] WorkoutExercise stores integer sets, integer reps, and nullable advancedTechnique
- [ ] Activating a plan sets it as active and deactivates all other plans for that user atomically
- [ ] Only one plan is ever active at a time (enforced at the API level)
- [ ] WorkoutGroups and WorkoutExercises respect `orderIndex` in responses
- [ ] Deleting a plan cascades to its groups and exercises
- [ ] Ownership enforced: users can only access and mutate their own plans
