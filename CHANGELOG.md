## v0.3.0 (2026-06-03)

## What's Changed
* feat(analytics): limit dashboard heatmap to last 3 months by @clertonraf in https://github.com/clertonraf/satzwerk/pull/14


**Full Changelog**: https://github.com/clertonraf/satzwerk/compare/v0.2.0...v0.3.0

---

## v0.2.0 (2026-06-03)

## What's Changed
* feat(history): session drill-down with set logs in HistoryPage by @clertonraf in https://github.com/clertonraf/satzwerk/pull/15


**Full Changelog**: https://github.com/clertonraf/satzwerk/compare/v0.1.0...v0.2.0

---

## v0.1.0 (2026-06-02)

## What's Changed
* feat(sessions): per-exercise kg/lb unit toggle with live conversion hint by @clertonraf in https://github.com/clertonraf/satzwerk/pull/11
* fix: silent session restore on page reload by @clertonraf in https://github.com/clertonraf/satzwerk/pull/12
* feat(sessions): allow editing a logged set during active session by @clertonraf in https://github.com/clertonraf/satzwerk/pull/13


**Full Changelog**: https://github.com/clertonraf/satzwerk/compare/v0.0.3...v0.1.0

---

## v0.0.3 (2026-05-30)

## What's Changed
* refactor(plan-import): extract createGroupsAndExercises pipeline stage by @clertonraf in https://github.com/clertonraf/satzwerk/pull/5
* refactor(analytics): replace awaitSingle with asFlow().toList() in AnalyticsRepository by @clertonraf in https://github.com/clertonraf/satzwerk/pull/6
* refactor(sessions): split useWorkoutSession into lifecycle and conflict-resolution hooks by @clertonraf in https://github.com/clertonraf/satzwerk/pull/7
* refactor(auth): centralize token lifecycle in tokenService by @clertonraf in https://github.com/clertonraf/satzwerk/pull/8
* ci(deploy): fix Portainer 504 false failures and duplicate deployments by @clertonraf in https://github.com/clertonraf/satzwerk/pull/10


**Full Changelog**: https://github.com/clertonraf/satzwerk/compare/v0.0.2...v0.0.3

---

## v0.0.2 (2026-05-30)

## What's Changed
* ci: optimize pipeline with path filters, artifact passing, and buildx cache by @clertonraf in https://github.com/clertonraf/satzwerk/pull/9


**Full Changelog**: https://github.com/clertonraf/satzwerk/compare/v0.0.1...v0.0.2

---

# Changelog

## v0.0.1 (2026-05-30)

## What's Changed
* feat(sessions): add workout group exercise preview before starting session by @clertonraf in https://github.com/clertonraf/satzwerk/pull/1
* perf(workouts): batch-load WorkoutExercises for plan detail by @clertonraf in https://github.com/clertonraf/satzwerk/pull/2
* refactor(workouts): consolidate response mapping into WorkoutResponseMapper by @clertonraf in https://github.com/clertonraf/satzwerk/pull/3
* refactor(common): extract requireOwnership helper to eliminate inline checks by @clertonraf in https://github.com/clertonraf/satzwerk/pull/4

## New Contributors
* @clertonraf made their first contribution in https://github.com/clertonraf/satzwerk/pull/1

**Full Changelog**: https://github.com/clertonraf/satzwerk/commits/v0.0.1
