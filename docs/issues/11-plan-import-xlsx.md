# Issue 11 — PlanImport: Upload xlsx to Create WorkoutPlan

## Goal

Allow a user to upload an `.xlsx` spreadsheet (exported from a personal trainer's tool) to create a WorkoutPlan. Parsing is delegated to the KraftLogParser sidecar service.

## Decisions (from ADR-0004 and grilling session)

| Question | Decision |
|---|---|
| Architecture | Satzwerk backend calls KraftLogParser internally; frontend uploads to one endpoint |
| Plan name | Derived from the xlsx filename (strip extension) |
| Activation | Imported plan starts **inactive**; user activates explicitly |
| Exercise dedup | Match by exact name (case-insensitive) within user's catalog; create if absent |
| Reps "F" | Add `toFailure: Boolean` to `WorkoutExercise`; reps = 0 when true |
| AdvancedTechnique | Best-effort fuzzy mapping; `null` if no match (never fails the import) |
| body_parts | First entry becomes `muscleGroup` for newly created exercises |
| rest_interval | Dropped silently |

## Backend changes

1. **Schema**: Add `to_failure BOOLEAN NOT NULL DEFAULT FALSE` to `workout_exercises` (V6 migration)
2. **Domain**: Add `toFailure: Boolean` to `WorkoutExercise` entity
3. **KraftLogParser client**: WebClient bean calling `POST http://kraftlogparser:8080/api/workout/parse`
4. **ImportService**: Receives `FilePart`, calls parser, maps JSON → domain objects, persists atomically
   - Exercise dedup: `findByUserIdAndNameIgnoreCase` or create
   - AdvancedTechnique mapping: `"rest" / "pause" → REST_PAUSE`, `"strip" → SST`, `"gvt" → GVT`, `"fst" → FST_7`, `"gironda" → GIRONDA`, else `null`
   - Plan name: filename without extension
5. **Endpoint**: `POST /api/plans/import` — `multipart/form-data`, field `file`
6. **Error handling**: KraftLogParser unavailable → 503; parse error → 422

## Frontend changes

1. **planService**: Add `importFromFile(file: File): Promise<WorkoutPlan>`
2. **PlansPage**: Add "Import xlsx" button → opens file picker (`.xlsx` only) → calls service → refreshes list on success
3. **PlanCard**: Show `IMPORTED` badge when `source === 'IMPORTED'`

## Docker Compose changes

1. Add `kraftlogparser` service to `docker-compose.yml` using `ghcr.io/clertonraf/kraftlogparser:latest`
2. Expose on internal network only; Satzwerk backend resolves `http://kraftlogparser:8080`

## Tests (TDD)

### Backend
- Import creates plan + groups + exercises from valid xlsx
- Exercise dedup reuses existing exercise by name
- `reps: "F"` maps to `toFailure = true, reps = 0`
- Unrecognised technique → `null` (no failure)
- KraftLogParser unavailable → 503
- Unauthenticated request → 401

### Frontend
- Import button renders on PlansPage
- Successful import calls service and refreshes plan list
- `IMPORTED` badge visible on PlanCard with source IMPORTED
