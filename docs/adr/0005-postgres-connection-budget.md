# Postgres connection budget: max_connections sizing and PgBouncer deferral

A stress test (see #283) found that the backend previously opened a raw, unpooled
R2DBC connection per query. #283 fixed this by adding R2DBC connection pooling with
a conservative per-instance `max-size` of 10. This ADR sizes Postgres's
`max_connections` to comfortably cover that pool across multiple backend replicas
(#285), and records why PgBouncer is deferred rather than adopted now.

## Update (#295)

Issue #295 tuned the shipped defaults from `max-size=10` / `2` replicas to
`max-size=15` / `3` replicas after a local saturation study. That changes the
default pooled connection budget from `2 x 10 = 20` to **`3 x 15 = 45`**.

This remains inside the original ADR decision boundary:

- Postgres still ships with `max_connections=150`, so the tuned default leaves
  **105 connections of headroom** for Flyway, administration, metrics scrapes,
  and future services.
- The PgBouncer deferral logic is unchanged: this is still a modest replica
  count, not the "tens of replicas" scale where a separate pooler becomes worth
  its operational cost.
- The revisit triggers below still apply unchanged; a default of 3 replicas and
  pool size 15 does not by itself push the system near those triggers.

## Budget

- R2DBC pool `max-size` per backend instance: **15** (`spring.r2dbc.pool.max-size`,
  see `backend/src/main/resources/application.yml`; updated by #295).
- Target replica count headroom: up to **4** backend replicas (current default is 3,
  after #285 and #295) → `4 x 15 = 60` pooled connections at that headroom target.
- Additional headroom for Flyway migrations (short-lived JDBC connections during
  deploys), direct `psql` administration, and future services: **~40** connections.
- Chosen `max_connections`: **150** (`docker-compose.yml`, `postgres` service
  `command`, overridable via `POSTGRES_MAX_CONNECTIONS`). This leaves >100 connections
  of headroom above the current shipped default (45 pooled connections) and about
  **50 connections** above the 4-replica planning budget (`60 + 40 = 100`), which
  is still sufficient without introducing PgBouncer.
- `shared_buffers` bumped to **256MB** (from the Postgres default of 128MB) since
  raising `max_connections` increases Postgres's per-connection memory overhead and a
  slightly larger buffer pool keeps read performance stable under the higher
  connection ceiling. Both values are overridable via environment variables
  (`POSTGRES_MAX_CONNECTIONS`, `POSTGRES_SHARED_BUFFERS`) for environment-specific
  tuning without editing `docker-compose.yml`.

## Considered Options

- **Increase `max_connections` only (chosen)** — simplest change, sufficient
  headroom for the currently planned replica count (3-4), no new moving parts to
  operate.
- **Add PgBouncer in transaction-pooling mode** — deferred. PgBouncer earns its
  operational cost (another process to run, monitor, and reason about failure modes
  for) once replica counts get large enough that `replicas x pool max-size` would
  otherwise force `max_connections` uncomfortably high (order of magnitude: tens of
  replicas). At the currently planned scale (3-4 replicas), a direct `max_connections`
  increase is simpler and has no additional operational surface.

## Revisit trigger

Re-evaluate this decision (and consider adding PgBouncer) if:
- Planned backend replica count exceeds ~10, or
- `R2DBC_POOL_MAX_SIZE` is increased significantly per instance, or
- `max_connections` would need to exceed roughly 300-400 to keep the same headroom
  (a widely cited practical ceiling for vanilla Postgres before connection-management
  overhead becomes its own bottleneck).
