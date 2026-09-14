# Satzwerk

Self-hosted gym workout tracker. Log sets, build plans, visualise training with a GitHub-style heatmap.

## Quick start (local dev)

```bash
cp .env.example .env
# Edit .env — set DB_PASSWORD and JWT_SECRET at minimum
docker compose up
```

App: http://localhost:5173
Backend (direct, bypassing Traefik — dev only): http://localhost:8083
Traefik dashboard: http://localhost:8081

Requests from the frontend to `/api/*` are proxied through Traefik, which load-balances
across all `backend` replicas (see `BACKEND_REPLICAS` below).

## Production deployment

```bash
cp .env.example .env
# Set BACKEND_REPLICAS (default 2), strong DB_PASSWORD and JWT_SECRET
docker compose -f docker-compose.yml up -d
```

Traefik load-balances requests across `BACKEND_REPLICAS` backend instances over
plain HTTP; it does not currently terminate TLS. Put a TLS-terminating reverse
proxy or managed load balancer in front of this stack for public production use.

## Development (without Docker)

**Backend**
```bash
cd backend
./gradlew bootRun
```

**Frontend**
```bash
cd frontend
pnpm dev
```

Requires a local PostgreSQL instance. Copy `.env.example` and set `DB_*` variables.

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React + TypeScript + Vite + Tailwind + shadcn/ui |
| Backend | Kotlin + Spring Boot WebFlux + R2DBC |
| Database | PostgreSQL |
| Auth | JWT + refresh token rotation |
| Deployment | Docker Compose + Traefik |

## Performance testing

The repository includes a bounded k6 regression script at `perf/stress.js`. It targets the backend directly and mixes:

- `POST /api/auth/register`
- `POST /api/exercises` + `GET /api/exercises`
- `GET /api/analytics/summary`

Run it locally against the Docker Compose backend port:

```bash
BASE_URL=http://localhost:8083 k6 run perf/stress.js
```

If `BASE_URL` is omitted, the script defaults to `http://localhost:8083`, which matches `docker-compose.override.yml`.

GitHub Actions also runs the same script in `.github/workflows/perf.yml` on `workflow_dispatch` and on a weekly schedule. The workflow boots a local Docker Compose stack, waits for `backend` health, then lets k6 enforce the regression gate through thresholds.

Treat a workflow failure as a performance regression signal, not just a flaky smoke test. The current gate fails when HTTP failures exceed 1%, when the mixed scenario p95 latency rises above 500 ms, or when the read-only summary spike p95 latency rises above 350 ms. Start by checking the k6 threshold output and the Docker Compose service logs in the workflow job.
