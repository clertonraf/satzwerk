# Backend capacity baseline (resource-constrained)

Records the capacity baseline established after adding explicit container
resource limits and JVM heap tuning to the `backend` service (issue #286),
so future load tests (see the automated k6 CI gate, issue #287) have a fixed
configuration to compare against.

## Configuration under test

- `docker-compose.yml` `backend.deploy.resources.limits`: `cpus: 1.0`,
  `memory: 768M` (per replica), reservations `cpus: 0.5`, `memory: 512M`.
- JVM flags baked into `backend/Dockerfile` via `JAVA_OPTS`:
  `-XX:+UseContainerSupport -Xms256m -Xmx512m -XX:MaxMetaspaceSize=128m`.
- R2DBC pool `max-size=10` per instance (#283), Postgres `max_connections=150`
  (#284), 2 backend replicas behind Traefik by default (#285).

## What was verified locally

- `docker inspect` on a running `backend` container confirms the limit is
  actually applied: `Memory: 805306368` (768 MiB), `NanoCpus: 1000000000`
  (1.0 CPU).
- `java -XX:+PrintFlagsFinal` inside the built image confirms the JVM honors
  the explicit flags: `MaxHeapSize=536870912` (512MB), `InitialHeapSize=268435456`
  (256MB), `MaxMetaspaceSize=134217728` (128MB), `UseContainerSupport=true`.
- Both replicas started and passed their `/actuator/health` check under this
  configuration with Postgres and Traefik in the loop.

## Establishing a throughput/latency number

This change verifies the *configuration* is correctly enforced end-to-end; it
does not itself re-run the original manual k6 saturation test (ramping to
8,000 concurrent VUs) against this specific resource-constrained
configuration — repeating that scale of test isn't practical to run
unattended in this environment. The automated k6 CI gate introduced in #287
is the intended mechanism for establishing and continuously re-verifying a
repeatable throughput/latency baseline under this resource configuration;
run it manually (`workflow_dispatch` on `.github/workflows/perf.yml`) against
this configuration to record the first official numeric baseline.

## Observing pool saturation via Prometheus metrics

When running a local or CI k6 load test against this baseline, scrape the
backend's `/actuator/prometheus` endpoint alongside the usual latency/error
summary so you can correlate request pressure with pool behavior. A simple
loop with `curl http://localhost:8080/actuator/prometheus` (or your ingress
URL) is enough for ad-hoc sampling; a Prometheus server can scrape the same
endpoint continuously during longer runs.

Focus on the R2DBC pool meters and HTTP request timer:

- `r2dbc.pool.acquired` / Prometheus `r2dbc_pool_acquired*`: current in-use
  connections.
- `r2dbc.pool.pending` / Prometheus `r2dbc_pool_pending*`: requests waiting
  for a connection; sustained non-zero values indicate saturation.
- `r2dbc.pool.max-allocated-size` / Prometheus `r2dbc_pool_max_allocated*`:
  the configured upper bound for allocated connections.
- `http.server.requests` / Prometheus `http_server_requests_seconds*`:
  per-route request count/latency so you can line up pool pressure with the
  specific endpoints under load.

During a healthy run, `r2dbc.pool.pending` should stay near zero and
`r2dbc.pool.acquired` should oscillate below `r2dbc.pool.max-allocated-size`. If
pending requests climb and stay high while `http.server.requests` latency
degrades, treat that as evidence that the pool is saturated before adjusting
any sizing values.
