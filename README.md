# Satzwerk

Self-hosted gym workout tracker. Log sets, build plans, visualise training with a GitHub-style heatmap.

## Quick start (local dev)

```bash
cp .env.example .env
# Edit .env — set DB_PASSWORD and JWT_SECRET at minimum
docker compose up
```

App: http://localhost:5173  
Backend: http://localhost:8080  
Traefik dashboard: http://localhost:8081

## Production deployment

```bash
cp .env.example .env
# Set DOMAIN, ACME_EMAIL, strong DB_PASSWORD and JWT_SECRET
docker compose -f docker-compose.yml up -d
```

Traefik handles HTTPS via Let's Encrypt automatically.

## Development (without Docker)

**Backend**
```bash
cd backend
./gradlew bootRun
```

**Frontend**
```bash
cd frontend
npm run dev
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
