# Postgres connection budget: max_connections sizing and PgBouncer deferral

A stress test (see #283) found that the backend previously opened a raw, unpooled
R2DBC connection per query. #283 fixed this by adding R2DBC connection pooling with
a conservative per-instance `max-size` of 10. This ADR sizes Postgres's
`max_connections` to comfortably cover that pool across multiple backend replicas
(#285), and records why PgBouncer is deferred rather than adopted now.

## Budget

- R2DBC pool `max-size` per backend instance: **10** (`spring.r2dbc.pool.max-size`,
  see `backend/src/main/resources/application.yml`).
- Target replica count headroom: up to **4** backend replicas (current default is 2,
  see #285) → `4 x 10 = 40` pooled connections at full scale.
- Additional headroom for Flyway migrations (short-lived JDBC connections during
  deploys), direct `psql` administration, and future services: **~40** connections.
- Chosen `max_connections`: **150** (`docker-compose.yml`, `postgres` service
  `command`, overridable via `POSTGRES_MAX_CONNECTIONS`). This leaves >100 connections
  of headroom above the calculated worst case (40 + 40 = 80), enough to double the
  replica count again without a config change.
- `shared_buffers` bumped to **256MB** (from the Postgres default of 128MB) since
  raising `max_connections` increases Postgres's per-connection memory overhead and a
  slightly larger buffer pool keeps read performance stable under the higher
  connection ceiling. Both values are overridable via environment variables
  (`POSTGRES_MAX_CONNECTIONS`, `POSTGRES_SHARED_BUFFERS`) for environment-specific
  tuning without editing `docker-compose.yml`.

## Considered Options

- **Increase `max_connections` only (chosen)** — simplest change, sufficient
  headroom for the currently planned replica count (2-4), no new moving parts to
  operate.
- **Add PgBouncer in transaction-pooling mode** — deferred. PgBouncer earns its
  operational cost (another process to run, monitor, and reason about failure modes
  for) once replica counts get large enough that `replicas x pool max-size` would
  otherwise force `max_connections` uncomfortably high (order of magnitude: tens of
  replicas). At the currently planned scale (2-4 replicas), a direct `max_connections`
  increase is simpler and has no additional operational surface.

## Revisit trigger

Re-evaluate this decision (and consider adding PgBouncer) if:
- Planned backend replica count exceeds ~10, or
- `R2DBC_POOL_MAX_SIZE` is increased significantly per instance, or
- `max_connections` would need to exceed roughly 300-400 to keep the same headroom
  (a widely cited practical ceiling for vanilla Postgres before connection-management
  overhead becomes its own bottleneck).
