# [BE+FE] Heatmap & Dashboard

**Type:** AFK
**Blocked by:** #07 Workout Session Tracking

## What to build

The dashboard: a GitHub-style contribution heatmap driven by `SetLog` count per day, a streak counter, and a summary of recent activity. The heatmap is a custom SVG implementation.

Key domain decisions (see CONTEXT.md):
- Heatmap intensity is derived from **set count** (not session count) with fixed tiers.
- The `SetLog` table is the data source, not `WorkoutSession`.

### Backend

- `GET /api/analytics/heatmap?from=YYYY-MM-DD&to=YYYY-MM-DD`
  Response: array of `{ date, count, intensity }` where `intensity` is bucketed (e.g. 0 = none, 1 = 1–4 sets, 2 = 5–9, 3 = 10–14, 4 = 15+)
- `GET /api/analytics/streak` — returns `{ currentStreak, longestStreak }` based on consecutive days with at least one `SetLog`
- Both endpoints scoped to the authenticated user

### Frontend

- Dashboard page as the post-login home screen
- Custom SVG heatmap component (`ContributionHeatmap`): GitHub-style square grid, colour intensity from the `intensity` field, tooltip on hover showing date + set count
- `StreakCard` showing current and longest streak
- Last workout summary (most recent completed `WorkoutSession`)
- Weekly set volume summary

## Acceptance criteria

- [ ] Heatmap displays one square per day for the trailing 52 weeks
- [ ] Days with no sets are rendered in the lowest intensity colour
- [ ] Intensity tiers match the backend bucketing logic consistently
- [ ] Hovering a day shows the date and total set count
- [ ] Streak counter updates correctly after a new session is completed
- [ ] Heatmap queries `SetLog` count, not `WorkoutSession` count
- [ ] Dashboard loads in under 2 seconds on a local network
- [ ] All analytics endpoints return empty/zero results gracefully when no data exists
