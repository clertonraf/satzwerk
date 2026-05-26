# ADR-0004: satzwerk-parser as Internal Sidecar for Plan Import

## Status

Accepted

## Context

Satzwerk needs to support importing WorkoutPlans from `.xlsx` spreadsheets produced by personal trainers. The parsing logic already exists in [satzwerk-parser](https://github.com/clertonraf/satzwerk-parser), a separate service that accepts a multipart xlsx upload and returns structured JSON (`workouts`, `exercises`, `sets`, `reps`).

Three integration options were considered:

1. **Sidecar** — Satzwerk backend receives the file, calls satzwerk-parser internally, maps the result to domain objects, persists atomically.
2. **Frontend-mediated** — Frontend uploads to satzwerk-parser directly, receives JSON, POSTs JSON to Satzwerk backend.
3. **Merged** — satzwerk-parser parsing logic is copied into the Satzwerk backend.

## Decision

Use option 1: satzwerk-parser runs as a sidecar service in the same Docker Compose stack. The Satzwerk backend owns the single upload endpoint (`POST /api/plans/import`). The frontend has no knowledge of satzwerk-parser.

## Consequences

- The import is atomic from the user's perspective — one upload, one result.
- satzwerk-parser's public Docker image (`ghcr.io/clertonraf/satzwerk-parser:latest`) can be pulled directly; no build step required.
- The Satzwerk backend must handle satzwerk-parser being unavailable (returns 503 to the client).
- The frontend parser boundary is kept clean — `planService.importFromFile()` calls a single Satzwerk endpoint.
