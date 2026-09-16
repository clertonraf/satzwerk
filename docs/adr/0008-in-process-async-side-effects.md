# ADR-0008: In-process async queue for non-critical post-write side effects

## Status

Accepted

## Context

Issue #333 reviewed whether a distributed broker such as Kafka or RabbitMQ would
help the write-saturation ceiling observed in #309. That bottleneck is not caused
by cross-service messaging. It is the time spent on one hot `SetLog` write path
holding Postgres/R2DBC pool capacity.

Satzwerk is still a self-hosted application with a small Compose topology and no
current fan-out or multi-consumer workflow. A distributed broker would add
another stateful service, new failure modes, and operational tuning without
solving the measured bottleneck.

The backend already has one useful post-write boundary today:

- `transactionRunner.afterCommit { ... }` runs work after the enclosing
  transaction commits when a transaction is active, and runs immediately when
  no transaction is active.
- `R2dbcTransactionRunner` logs and swallows post-commit failures centrally, so a
  side effect failure does not turn an already committed write into an HTTP
  error.
- `SetLogService` uses that pattern for
  `analyticsReadCache.invalidateUser(session.userId)`, which keeps the cache
  invalidation outside the database transaction.

That current pattern is still correct for cheap in-process side effects. The gap
is that there is no established pattern for future best-effort work that should
also be decoupled from the request/response path itself once the transaction has
already committed.

## Decision

- Do **not** add Kafka, RabbitMQ, or another distributed broker at the current
  scale.
- The preferred lightweight pattern for future non-critical asynchronous
  side effects is a **bounded Kotlin `Channel` with a dedicated coroutine
  worker** per backend replica.
- When this pattern is used, enqueue work from
  `transactionRunner.afterCommit { ... }`, not from inside the database
  transaction.
- Keep using plain synchronous `afterCommit` blocks for side effects that are
  already cheap and local, such as the current cache invalidation in
  `SetLogService`.
- Use the async queue pattern only for **best-effort** work whose failure or
  loss is acceptable because it is non-critical, recomputable, or otherwise safe
  to retry later from another trigger.
- The queue must stay **bounded**. If it is full, log and drop the work item
  instead of blocking the request thread/coroutine and reintroducing backpressure
  onto the write path. Overload logging should be sampled or aggregated so the
  drop path does not become its own synchronous bottleneck.
- This pattern is explicitly **single-JVM-per-replica**. Each backend replica
  owns its own worker and in-memory queue; queued items are not shared across
  replicas and are lost on process crash or restart.
- Queue shutdown must be **bounded**. Give the worker a short grace period to
  finish buffered work, then cancel and drop any remaining buffered items rather
  than hanging application shutdown indefinitely.

Minimal reference shape:

```kotlin
transactionRunner.afterCommit {
    asyncWorkQueue.submit(UserAnalyticsRefresh(userId))
}
```

The worker consumes items in the background and handles/logs failures internally.

Re-evaluate this decision if Satzwerk gains any of the following:

- durable delivery requirements
- cross-replica coordination or at-least-once guarantees
- multiple independent consumers of the same event
- integration with external services where dropped work is no longer acceptable

At that point, a transactional outbox and/or distributed broker becomes worth
reconsidering.

## Consequences

- Future contributors have a proportionate default for decoupling slow
  post-write work without reaching for broker infrastructure too early.
- The request/response path can stay narrow: commit the write, then enqueue any
  slow best-effort work after commit.
- Cheap cache invalidations and similar local actions should remain simple
  synchronous `afterCommit` callbacks rather than being pushed through a queue by
  default.
- Any queue built from this ADR must document its drop policy, capacity, and
  shutdown behavior so operators understand that it is an in-memory convenience,
  not durable messaging infrastructure.
- This decision does **not** change any existing production write path in this
  ADR. It establishes the pattern and a minimal example only.
