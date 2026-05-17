# [BE+FE] WorkoutExercise Reordering

**Type:** AFK
**Blocked by:** #05 Workout Plan Builder

## What to build

Up/down controls to reorder `WorkoutExercise` entries within a `WorkoutGroup`. Drag-and-drop is **explicitly excluded** (see ADR); the interaction is simple up/down buttons.

### Backend

- `PATCH /api/plans/{planId}/groups/{groupId}/exercises/{exerciseId}/order`
  - Body: `{ "direction": "UP" | "DOWN" }`
  - Swaps `orderIndex` with the adjacent exercise; no-ops if already first/last
- Alternatively: `PATCH …/exercises/reorder` accepting a full ordered list of IDs (batch reorder)
- `GET` responses for WorkoutGroups always return exercises sorted by `orderIndex` ascending

### Frontend

- Up ▲ / Down ▼ buttons on each `WorkoutExercise` row in the plan builder
- First item's Up button and last item's Down button are disabled
- Optimistic UI update; rollback on API error

## Acceptance criteria

- [ ] Pressing Up moves a WorkoutExercise one position up within its group
- [ ] Pressing Down moves a WorkoutExercise one position down within its group
- [ ] The first exercise has Up disabled; the last has Down disabled
- [ ] Order persists after page reload
- [ ] Reordering one group does not affect `orderIndex` values in other groups
- [ ] API returns exercises in `orderIndex` order on every fetch
