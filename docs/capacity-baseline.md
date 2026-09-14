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
summary so you can correlate request pressure with pool behavior. In local
Docker dev, `docker-compose.override.yml` maps the backend to host port 8083.
Create a dedicated Personal API Token scoped to `metrics:read`, then use that
long-lived token for scraping:

```bash
METRICS_PAT=$(curl -s http://localhost:8083/api/tokens \
  -H "Authorization: Bearer <jwt-session-token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Prometheus scrape","scopes":["metrics:read"]}' | jq -r '.token')

curl -H "Authorization: Bearer $METRICS_PAT" http://localhost:8083/actuator/prometheus
```

In the production-style multi-replica setup behind Traefik, `docker-compose.yml`
does not publish a host port for `backend` and Traefik's only router matches
`/api`, so `/actuator/prometheus` is **not reachable** from outside the Compose
network today. Scraping it in that topology needs one of: a Prometheus
container joined to the same Docker network (note: scraping `backend:8080`
directly does **not** load-balance or rotate across replicas — plain Docker
DNS resolution is cached, so a target configured this way will repeatedly
hit whichever single replica it first resolved and silently miss the others;
per-replica discovery is required for full coverage), a
dedicated private Traefik router/entrypoint for `/actuator/**` restricted to an
internal network, or an equivalent per-replica private route. That network
setup is out of scope here — this section only covers local/CI k6 runs, where
the host-published port above is sufficient; see #302 for production-topology
Prometheus scraping before relying on it operationally.

For quick ad-hoc manual sampling, a JWT obtained via `/api/auth/login` still
works, but it follows `jwt.expiry-ms` in `application.yml` and expires after
about 15 minutes by default, so it is not suitable for continuous Prometheus
scraping. Note also that each PAT-authenticated scrape updates that token's
`lastUsedAt` timestamp (one small DB write per scrape interval) — negligible
next to real load-test traffic, but worth knowing if you're scrutinizing pool
metrics at very fine granularity.

Focus on the R2DBC pool meters and HTTP request timer:

- `r2dbc.pool.acquired` / Prometheus
  `r2dbc_pool_acquired_connections`: current in-use connections.
- `r2dbc.pool.pending` / Prometheus
  `r2dbc_pool_pending_connections`: requests waiting for a connection;
  sustained non-zero values indicate saturation.
- `r2dbc.pool.max.allocated` / Prometheus
  `r2dbc_pool_max_allocated_connections`: the configured upper bound for
  allocated connections.
- `http.server.requests` / Prometheus `http_server_requests_seconds*`:
  per-route request count/latency so you can line up pool pressure with the
  specific endpoints under load.

During a healthy run, `r2dbc.pool.pending` should stay near zero and
`r2dbc.pool.acquired` should oscillate below `r2dbc.pool.max.allocated`. If
pending requests climb and stay high while `http.server.requests` latency
degrades, treat that as evidence that the pool is saturated before adjusting
any sizing values.
