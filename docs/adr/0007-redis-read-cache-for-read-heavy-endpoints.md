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
- Cache keys are always scoped by **user ID**:
  - `workouts:exercises:list:{userId}:{muscleGroup|all}`
  - `analytics:heatmap:{userId}:{from}:{to}`
  - `analytics:streak:{userId}`
- Cache values are serialized as JSON with the existing Jackson `ObjectMapper`.
- `Exercise` list entries use **write-driven invalidation** plus a long TTL
  (12 hours). Any create, update, or delete for that user evicts all cached
  list variants for that user.
- `Heatmap` and streak entries use a **short TTL** (45 seconds) plus explicit
  invalidation on `SetLog` writes and `WorkoutSession` discard for that user.
  `WorkoutSession` completion relies on the same short TTL because the cached
  analytics data is already invalidated during the preceding `SetLog` writes.
- Redis in local Docker Compose runs as a **non-persistent cache**
  (`redis-server --save "" --appendonly no`). Cache data is disposable and does
  not need to survive restarts.
- Expose Redis health through Spring Boot's auto-configured Redis health
  indicator while keeping component-level health details on authenticated
  `/actuator/health/**` requests.
- Record cache hit/miss counters in Micrometer under
  `satzwerk.cache.requests{cache=<name>,result=hit|miss}`.

## Consequences

- Multiple backend replicas now share one cache and converge on the same cached
  values and invalidations.
- Redis becomes an operational dependency for the backend runtime and local
  Compose stack.
- The cache implementation stays predictable for coroutine code because reads
  and invalidations are explicit in the service layer.
- Local single-node latency may not improve materially; the main benefit is
  reduced repeated Postgres reads and cross-replica cache consistency.
- `CACHE_ENABLED=false` is available as an operational bypass and for local
  A/B benchmarking.
