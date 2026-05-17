# ADR-0004: KraftLogParser as Internal Sidecar for Plan Import

## Status

Accepted

## Context

Satzwerk needs to support importing WorkoutPlans from `.xlsx` spreadsheets produced by personal trainers. The parsing logic already exists in [KraftLogParser](https://github.com/clertonraf/KraftLogParser), a separate service that accepts a multipart xlsx upload and returns structured JSON (`workouts`, `exercises`, `sets`, `reps`).

Three integration options were considered:

1. **Sidecar** — Satzwerk backend receives the file, calls KraftLogParser internally, maps the result to domain objects, persists atomically.
2. **Frontend-mediated** — Frontend uploads to KraftLogParser directly, receives JSON, POSTs JSON to Satzwerk backend.
3. **Merged** — KraftLogParser parsing logic is copied into the Satzwerk backend.

## Decision

Use option 1: KraftLogParser runs as a sidecar service in the same Docker Compose stack. The Satzwerk backend owns the single upload endpoint (`POST /api/plans/import`). The frontend has no knowledge of KraftLogParser.

## Consequences

- The import is atomic from the user's perspective — one upload, one result.
- KraftLogParser's public Docker image (`ghcr.io/clertonraf/kraftlogparser:latest`) can be pulled directly; no build step required.
- The Satzwerk backend must handle KraftLogParser being unavailable (returns 503 to the client).
- The frontend parser boundary is kept clean — `planService.importFromFile()` calls a single Satzwerk endpoint.
