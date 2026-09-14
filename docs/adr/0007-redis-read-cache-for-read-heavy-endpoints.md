# ADR-0007: Redis-backed read cache for Exercise and analytics reads

## Status

Accepted

## Context

Issue #296 targets two read-heavy areas that are fetched much more often than
they change:

- the per-user `Exercise` catalog (`GET /api/exercises`)
- analytics reads for `Heatmap` and streaks

Satzwerk's production Compose topology can run multiple backend replicas behind
Traefik. A per-instance in-memory cache would fragment the cache across
replicas, increase cold misses, and allow stale entries to diverge until each
replica receives the same write traffic.

The backend stack already uses Spring WebFlux, coroutines, and R2DBC.
Annotation-driven `@Cacheable` support is a poor fit here because these read
paths are implemented as `suspend` functions and Reactor/coroutine return types.
Issue #296 explicitly chose Redis over in-memory caching.

## Decision

- Use **Redis** as the shared cache for the targeted read-heavy endpoints.
- Use **explicit service-layer caching** via `ReactiveStringRedisTemplate`
  wrapped in a small JSON cache service. Do not rely on `@Cacheable`.
- Cache keys are always scoped by **user ID** and a per-user **version counter**:
  - `workouts:exercises:list:{userId}:v{version}:{__unfiltered__|muscle-group:<value>}`
  - `analytics:heatmap:{userId}:v{version}:{from}:{to}`
  - `analytics:streak:{userId}:v{version}`
- Invalidation is **O(1)**: writes bump the relevant per-user version key
  instead of scanning Redis for matching keys. This also prevents stale
  in-flight cache fills from re-populating the active namespace after a newer
  invalidation.
- If a version key is malformed or corrupted, discard it and repair it to a
  fresh monotonic generation instead of reusing `v0`, so previously-written
  `v0` entries can never become live again after repair.
- If a version bump fails, retry it once. If it still fails, fall back to a
  direct Redis key scan/delete for just that user's cache namespace. If that
  rare fallback also fails, log an ERROR and continue serving the already
  committed write; the risk is bounded stale reads until TTL expiry, not a
  failed write.
- Cache values are serialized as JSON with the existing Jackson `ObjectMapper`.
- `Exercise` list entries use **write-driven invalidation** plus a long TTL
  (12 hours). Any create, update, or delete for that user bumps that user's
  Exercise cache version after the write transaction commits.
- `Heatmap` and streak entries use a **short TTL** (45 seconds) plus explicit
  invalidation on `SetLog` writes, imported `SetLog`s, and `WorkoutSession`
  discard for that user.
  `WorkoutSession` completion relies on the same short TTL because the cached
  analytics data is already invalidated during the preceding `SetLog` writes.
- Redis in local Docker Compose runs as a **non-persistent cache**
  (`redis-server --save "" --appendonly no`) with a **256 MiB maxmemory cap**
  and `allkeys-lru` eviction. Cache data is disposable and does not need to
  survive restarts, so bounding memory and evicting least-recently-used keys is
  preferred over risking unbounded growth from long-tail key variants.
- Expose Redis health through Spring Boot's auto-configured Redis health
  indicator only when cache is enabled, while keeping component-level health
  details on authenticated `/actuator/health` requests. Docker liveness uses a
  separate `/actuator/health/backend` group that excludes Redis so the backend
  stays healthy when Redis is unavailable and reads/writes fall back as
  designed.
- Record cache hit/miss counters in Micrometer under
  `satzwerk.cache.requests{cache=<name>,result=hit|miss}`.

## Consequences

- Multiple backend replicas now share one cache and converge on the same cached
  values and invalidations.
- Redis becomes an operational dependency for the backend runtime and local
  Compose stack.
- Docker quick start keeps `CACHE_ENABLED=true` by default because the Compose
  stack includes Redis. Local non-Docker backend development still keeps
  `CACHE_ENABLED=false` by default so the backend does not try to connect to
  Redis unless the developer opts in.
- The cache implementation stays predictable for coroutine code because reads
  and invalidations are explicit in the service layer and register post-commit
  work against the enclosing transaction when one exists, including partner API
  writes wrapped by `PartnerWritePolicyService`. Post-commit side effects are
  logged and swallowed centrally so they never turn a committed write into an
  HTTP error.
- Local single-node latency may not improve materially; the main benefit is
  reduced repeated Postgres reads and cross-replica cache consistency.
- `CACHE_ENABLED` is available both as an operational bypass and for local
  A/B benchmarking.
