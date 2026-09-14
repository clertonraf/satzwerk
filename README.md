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
proxy in front of this stack for public production use — TLS/ACME support was
previously removed and is tracked for a possible future reintroduction in
issue #290. `DOMAIN`/`ACME_EMAIL` in `.env.example` are stale leftovers from
that removed setup — do not rely on them.

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
