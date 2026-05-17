# [BE+FE] Docker Compose Deployment

**Type:** AFK
**Blocked by:** #08 Heatmap & Dashboard

## What to build

Production-ready self-hosted deployment via Docker Compose. All four services — frontend, backend, database, reverse proxy — run together with a single `docker compose up`.

### Services

- **frontend**: React app built to static files, served by Nginx
- **backend**: Spring Boot JVM container; health check on `/actuator/health`
- **postgres**: PostgreSQL with a named persistent volume; Flyway runs on backend startup
- **traefik**: Reverse proxy + automatic HTTPS via Let's Encrypt; routes `/api/*` to backend, everything else to frontend

### Configuration

- All secrets (DB password, JWT secret) via environment variables / `.env` file (never committed)
- `docker-compose.yml` for production; `docker-compose.override.yml` for local dev (hot reload, exposed ports)
- Backend `prod` Spring profile activated in the container

### CI/CD (GitHub Actions)

Pipeline triggered on push to `main`:
1. Lint (ktlint/detekt + ESLint/Prettier)
2. Test (JUnit + Testcontainers / Vitest)
3. Build frontend static assets
4. Build backend fat JAR
5. Build and push Docker images
6. (Optional) Deploy step — placeholder, self-hosted deploy out of scope for this issue

## Acceptance criteria

- [ ] `docker compose up` starts all four services with no manual steps beyond providing a `.env` file
- [ ] Frontend is reachable over HTTPS via Traefik
- [ ] `GET /api/actuator/health` returns `{"status":"UP"}` through the reverse proxy
- [ ] PostgreSQL data persists across container restarts
- [ ] Secrets are not hardcoded; app reads them from environment variables
- [ ] GitHub Actions pipeline passes on a clean push to `main`
- [ ] `docker compose down -v` cleanly tears down all services
