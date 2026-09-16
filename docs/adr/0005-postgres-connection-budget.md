# Postgres connection budget: max_connections sizing and PgBouncer deferral

A stress test (see #283) found that the backend previously opened a raw, unpooled
R2DBC connection per query. #283 fixed this by adding R2DBC connection pooling with
a conservative per-instance `max-size` of 10. This ADR sizes Postgres's
`max_connections` to comfortably cover that pool across multiple backend replicas
(#285), and records why PgBouncer is deferred rather than adopted now.

## Update (#295)

At the time, issue #295 tuned the shipped defaults from `max-size=10` / `2` replicas to
`max-size=15` / `3` replicas after a local saturation study. That changes the
default pooled connection budget from `2 x 10 = 20` to **`3 x 15 = 45`**.

This remains inside the original ADR decision boundary:

- Postgres still ships with `max_connections=150`, so that historical 3-replica
  default leaves
  **105 connections of headroom** for Flyway, administration, metrics scrapes,
  and future services.
- The PgBouncer deferral logic is unchanged: this is still a modest replica
  count, not the "tens of replicas" scale where a separate pooler becomes worth
  its operational cost.
- The revisit triggers below still applied unchanged to that 3-replica
  configuration; a pool size of 15 at that scale did not by itself push the
  system near those triggers.

## Update (#308)

Issue #308 reverts the shipped Compose default replica count from **3** back to
**2**.

The follow-up high-concurrency read test used the same **2 vCPU** host class
that originally informed #295/#303. With `BACKEND_CPU_LIMIT=1.0`, the 3-replica
default budgeted **3.0 vCPUs** for the backend alone before Traefik, Postgres,
Redis, and the OS were counted, so the host was oversubscribed. At
**1,500 VUs**, throughput collapsed from **2,435 req/s** (p95 **511 ms**) on
the 2-replica baseline to **381 req/s** (p95 **6.1 s**) on the oversubscribed
3-replica setup, with **0% HTTP errors**. A separate **500-VU** `docker stats`
sample on the same host showed the three backend replicas already using about
45-49% CPU each, with Traefik around 28% CPU, leaving little headroom before
Postgres, Redis, and the OS were counted. That points to CPU backpressure and
queueing, not an application-level failure.

This does not change the core connection-budget decision in this ADR:

- Postgres still ships with `max_connections=150`, which comfortably covers both
  the restored default budget (**`2 x 15 = 30`**) and the documented 3-replica
  opt-in budget (**`3 x 15 = 45`**).
- `BACKEND_REPLICAS=3` remains a reasonable operator opt-in on hosts with at
  least **4 vCPUs**, where the backend CPU budget can stay within the machine's
  capacity while still leaving headroom for the rest of the stack.

## Empirical ceiling evidence after #309 / #328

`docs/capacity-baseline.md` now records two follow-up measurements that are
relevant to this ADR even though they do **not** yet cross the ADR's
"~10 replicas / 300-400 connections" PgBouncer trigger:

- With the shipped default budget restored by #308 (**2 replicas × pool 15 =
  30 connections**), the write-heavy discovery ramp stayed stable at roughly
  **250 VUs** and showed its first persistent failures around **500 VUs**,
  dominated by `R2dbcTimeoutException: Connection acquisition timed out after
  3000ms`.
- Issue #328 then reran the same discovery ramp at a much larger temporary
  budget (**4 replicas × pool 30 = 120 pooled connections**) and still observed
  the first non-zero failure probes in the **few-hundred-VU range** while
  Postgres CPU stayed comparatively low.

That empirical result matters for future re-evaluation: it suggests that
raising raw pool / replica budget alone does **not** guarantee near-linear
concurrency scaling, so any future PgBouncer or connection-budget revisit
should look at the measured application ceiling in `docs/capacity-baseline.md`,
not just `replicas × pool max-size` arithmetic.

## Budget

- R2DBC pool `max-size` per backend instance: **15** (`spring.r2dbc.pool.max-size`,
  see `backend/src/main/resources/application.yml`; updated by #295).
- Target replica count headroom: up to **4** backend replicas (current shipped
  default is 2 after #308; 3 remains the documented opt-in) → `4 x 15 = 60`
  pooled connections at that headroom target.
- Additional headroom for Flyway migrations (short-lived JDBC connections during
  deploys), direct `psql` administration, and future services: **~40** connections.
- Chosen `max_connections`: **150** (`docker-compose.yml`, `postgres` service
  `command`, overridable via `POSTGRES_MAX_CONNECTIONS`). This leaves >100 connections
  of headroom above the current shipped default (**30** pooled connections), still
  covers the documented 3-replica opt-in (**45** pooled connections), and leaves
  about **50 connections** above the 4-replica planning budget (`60 + 40 = 100`),
  which is still sufficient without introducing PgBouncer.
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
