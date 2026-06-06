# Dashboard Enrichment Design

**Date:** 2026-06-03  
**Status:** Approved — implementation decisions locked (grilling complete)

## Implementation Decisions

| # | Decision |
|---|----------|
| 1 | `WorkoutSessionResponse` gets a `setCount: Int` field. The `setLogs` array stays empty in history/list responses. `getById` populates it via `setLogs.size`. |
| 2 | A new `SessionQueryRepository` (DatabaseClient-based, parallel to `AnalyticsRepository`) handles the enriched history JOIN. |
| 3 | Single V8 migration: add `is_pr BOOLEAN NOT NULL DEFAULT FALSE` to `set_logs` and immediately backfill. |
| 4 | `isPr` is computed on both `addSetLog` and `updateSetLog` (updated row only, no cascading). |
| 5 | `WeeklyTrendChart` is pure SVG — no chart library introduced. |
| 6 | "Sets this week" = current ISO week (Monday 00:00 UTC → now), matching the weekly trend chart buckets. |
| 7 | Recent PRs endpoint includes SetLogs from open sessions (not filtered to completed sessions only). |

## Overview

Enrich the Satzwerk dashboard with a comprehensive training summary following the "Hero + Progressive Disclosure" layout pattern. Key stats appear at the top for at-a-glance scanning; the heatmap stays in place; richer detail lives below the fold.

---

## Layout

```
┌─────────────────────────────────────────────┐
│  At a Glance (2×3 stat grid)                │
├─────────────────────────────────────────────┤
│  Activity (Contribution Heatmap — unchanged)│
├────────────────────┬────────────────────────┤
│  Last Session      │  Recent PRs            │
├─────────────────────────────────────────────┤
│  Weekly Trend (8-week bar chart)            │
├─────────────────────────────────────────────┤
│  [Start Session]  [View History]            │
└─────────────────────────────────────────────┘
```

---

## Sections

### 1. Hero Stats Grid ("At a Glance")

A 2-row × 3-column grid of stat tiles at the top of the dashboard. Stats:

| Tile | Value | Source |
|------|-------|--------|
| 🔥 Current Streak | Days in a row | New `/analytics/summary` |
| Sessions This Month | Count of completed WorkoutSessions in current calendar month | New `/analytics/summary` |
| 🏆 PRs This Month | Count of personal records set this month | New `/analytics/summary` |
| ⚡ Longest Streak | All-time best streak | New `/analytics/summary` |
| 🎯 Total Sessions | All-time completed session count | New `/analytics/summary` |
| Sets This Week | Total SetLogs in the current ISO week (Monday–Sunday) | New `/analytics/summary` |

The existing `StreakCard` component is retired; streak data is absorbed into the hero grid. `DashboardPage` drops the `useQuery(streak)` call and uses `useQuery(summary)` for all six tiles. The `/analytics/streak` endpoint is not removed (other clients may use it) but is no longer called from `DashboardPage`.

### 2. Activity Heatmap

Unchanged. The existing `ContributionHeatmap` SVG component stays. It uses an 11-tier green intensity palette and fills the full container width via `width="100%"`.

### 3. Last Session Card

Replaces the current bare date card. Shows:
- **WorkoutGroup title** (e.g. "Push Day A")
- **Date** (localised)
- **Duration** — computed client-side from `startedAt` and `completedAt` (shown as "52 min"; hidden if session has no `completedAt`)
- **Set count** — `setCount` from the history response (history items always have empty `setLogs` by design; the count comes from a server-side JOIN)

The session history API response must be enriched to include `workoutGroupTitle: String` alongside the existing `workoutGroupId`. This is a denormalised field resolved server-side.

### 4. Recent PRs Card

A list of the 5 most recent personal records. Each row shows:
- Exercise name
- New max weight (kg)
- Date achieved

A **Personal Record** is defined as a SetLog whose `weight` exceeds all previous SetLogs for the same `exerciseId` by the same user at the time it was logged. The server computes and persists this; the frontend does not derive it client-side.

### 5. Weekly Trend Chart

An 8-week bar chart. Each bar represents one ISO week.

- **Bar height** = total SetLogs logged that week
- **Number above bar** = WorkoutSessions completed that week
- X-axis labels = ISO week numbers (e.g. W22)
- Green bars (`#22c55e`) matching the heatmap palette
- Chart label: "Bars = sets logged · Numbers = sessions"

---

## Backend Changes

### New endpoint: `GET /api/analytics/summary`

Returns all hero stat values in one call to minimise round-trips.

```json
{
  "currentStreak": 12,
  "longestStreak": 34,
  "sessionsThisMonth": 9,
  "setsThisWeek": 72,
  "totalSessions": 147,
  "prsThisMonth": 3
}
```

- `sessionsThisMonth`: count of WorkoutSessions with `completedAt` in the current UTC calendar month.
- `setsThisWeek`: count of SetLogs with `loggedAt` in the current ISO week (Monday–Sunday UTC).
- `totalSessions`: count of all completed WorkoutSessions for the user.
- `prsThisMonth`: count of SetLogs marked as a PR with `loggedAt` in the current UTC calendar month.

### New endpoint: `GET /api/analytics/weekly-trend?weeks=N` (default N=8)

Returns one entry per ISO week, ordered oldest → newest.

```json
[
  { "week": "2026-W19", "setCount": 38, "sessionCount": 2 },
  { "week": "2026-W20", "setCount": 52, "sessionCount": 3 }
]
```

- Weeks with no activity are included with `setCount: 0, sessionCount: 0`.
- `week` format: `YYYY-Www` (ISO 8601 week).

### New endpoint: `GET /api/analytics/personal-records?limit=N` (default N=5)

Returns the N most recently achieved PRs, ordered by `achievedAt` descending.

```json
[
  { "exerciseId": "...", "exerciseName": "Bench Press", "weightKg": 102.5, "achievedAt": "2026-06-02T18:34:00Z" }
]
```

PRs are computed by comparing each SetLog's weight against the running max for that exercise at the time of logging. The result is stored as a boolean flag on SetLog (`isPr`) populated at write time in `SessionService.addSetLog`.

A Flyway migration must backfill `isPr` for all existing SetLogs by running the same max-weight comparison over historical data ordered by `loggedAt`. Until the migration runs, existing users will see an empty PRs card.

### Modified: `GET /api/sessions/history`

Add `workoutGroupTitle: String` to each `WorkoutSession` in the response. Resolved by joining `workout_groups` on `workout_group_id` at query time. This is a read-time denormalisation — no schema migration required.

---

## Frontend Changes

### New components

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `DashboardSummaryGrid` | `features/analytics/` | Renders the 2×3 hero stat tiles |
| `LastSessionCard` | `features/sessions/` | Enriched last session display |
| `RecentPRsCard` | `features/analytics/` | Last 5 PRs list |
| `WeeklyTrendChart` | `features/analytics/` | 8-week bar chart (SVG) |

### Updated: `analyticsService`

Add three new service methods:

```ts
summary: () => api.get<DashboardSummary>('/analytics/summary').then(r => r.data)
weeklyTrend: (weeks?: number) => api.get<WeeklyTrendEntry[]>('/analytics/weekly-trend', { params: { weeks } }).then(r => r.data)
personalRecords: (limit?: number) => api.get<PersonalRecord[]>('/analytics/personal-records', { params: { limit } }).then(r => r.data)
```

### Updated: `queryKeys`

Add to `analytics` namespace:
```ts
summary: () => [...analytics(), 'summary'] as const
weeklyTrend: (weeks: number) => [...analytics(), 'weekly-trend', weeks] as const
personalRecords: (limit: number) => [...analytics(), 'personal-records', limit] as const
```

### Updated: `sessionService`

Add `workoutGroupTitle: string` to the `WorkoutSession` interface.

### Updated: `DashboardPage`

- Replace `StreakCard` import with `DashboardSummaryGrid` (streak data comes from the summary endpoint).
- Add queries for `summary`, `weeklyTrend(8)`, `personalRecords(5)`.
- Render all four new sections in the layout order described above.

---

## Data Flow

```
DashboardPage
  ├── useQuery(summary)       → DashboardSummaryGrid
  ├── useQuery(heatmap)       → ContributionHeatmap (unchanged)
  ├── useQuery(history)       → LastSessionCard (history[0])
  ├── useQuery(personalRecs)  → RecentPRsCard
  └── useQuery(weeklyTrend)   → WeeklyTrendChart
```

All queries are independent and run in parallel. The dashboard renders each section as soon as its data arrives; no global loading gate.

---

## Error & Empty States

- **Hero grid**: if `summary` fails, show skeleton tiles (not an error banner — stale data is not critical).
- **Last Session card**: if `history` is empty, show "No sessions yet" placeholder and hide the card. If `completedAt` is null (open session edge case), omit duration.
- **Recent PRs**: if empty, show "No PRs recorded yet."
- **Weekly Trend**: weeks with zero sets render as a minimal 2px bar so the x-axis label remains visible.

---

## Testing

- Unit tests for each new component with mock data covering empty state and populated state.
- Integration test on `GET /api/analytics/summary` covering: no sessions, sessions present, month boundary.
- Integration test on `GET /api/analytics/weekly-trend` covering: empty weeks included, correct ISO week bucketing.
- Integration test on `GET /api/analytics/personal-records` covering: no PRs, PR detection on `addSetLog`.
- Integration test on `GET /api/sessions/history` verifying `workoutGroupTitle` is present.

---

## Out of Scope

- Muscle group breakdown chart (not selected during design)
- Top exercises by frequency (not selected during design)
- Global unit toggle (lb display): per existing architecture, unit state lives per-exercise in `SessionPage` only
- Push notifications or reminders based on streak data
