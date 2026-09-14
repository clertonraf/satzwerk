# Backend capacity baseline (resource-constrained)

Records the local capacity baseline for the resource-constrained Docker stack
after explicit container limits (#286), Prometheus pool metrics (#294), and the
pool/replica tuning pass from #295.

## Test environment and method

- Host runtime: Colima (Docker) with **2 vCPUs** and **4 GiB RAM** available to
  the VM (`docker info`: `NCPU=2`, `MemTotal≈4.1 GB`).
- Topology under test: Postgres + Traefik + backend replicas from
  `docker-compose.yml`. The frontend and parser were omitted because the k6 test
  hits the backend API directly through Traefik.
- Backend image: built locally from the checked-out branch so the measured
  config exactly matches the committed `application.yml`.
- Workload: the existing `perf/stress.js` mixed write-heavy flow
  (register → create `Exercise` → list `Exercise`s → fetch analytics summary),
  rerun as short **constant-VU** bursts after a 10-second warm-up. This is
  heavier than typical `SetLog` traffic because every iteration also includes
  registration and exercise creation, so treat the numbers below as a
  conservative floor for real WorkoutSession write traffic.
- Metrics: `r2dbc_pool_acquired_connections`,
  `r2dbc_pool_pending_connections`, and
  `r2dbc_pool_max_allocated_connections` were scraped every 3 seconds from each
  backend replica's `/actuator/prometheus` endpoint and summed per sample.

## Tuned default configuration

- `docker-compose.yml` `backend.deploy.resources.limits`: `cpus: 1.0`,
  `memory: 768M` (per replica), reservations `cpus: 0.5`, `memory: 512M`.
- JVM flags baked into `backend/Dockerfile` via `JAVA_OPTS`:
  `-XX:+UseContainerSupport -Xms256m -Xmx512m -XX:MaxMetaspaceSize=128m`.
- **R2DBC pool `max-size=15` per backend replica** (#295).
- **3 backend replicas behind Traefik by default** (#295).
- Postgres `max_connections=150` (#284), so the default backend pool budget is
  `3 × 15 = 45` connections, leaving **105 connections of headroom** for
  Flyway, health checks, PAT/JWT-authenticated Prometheus scrapes, and other
  non-backend clients.

## Documented local write-throughput SLO

For the resource-constrained self-hosted baseline above, Satzwerk should
support **40 concurrent write-heavy API clients** against the existing k6 mixed
scenario with:

- `http_req_failed = 0%`
- aggregated `r2dbc_pool_pending_connections` staying effectively at zero
  (observed average `0.2`, peak `2`)
- p95 request latency staying below the existing local guardrail
  (`http_req_duration p95 ≈ 1.18s`, under the 1.5s threshold)

This is the recorded local SLO because it was the highest observed load that
kept errors at 0% and pool queueing near zero. At **50** concurrent clients,
the same 3-replica / pool-15 setup stayed error-free but crossed the latency
guardrail (`p95 ≈ 1.55s`), so 50 is better treated as the beginning of
saturation rather than the default target for a modest self-hosted gym tracker.

## Measured combinations

All runs below used 30-second measured windows after the same 10-second warm-up
on the 2 vCPU / 4 GiB Colima VM described above.

| Pool / replicas | Load (VUs) | Req/s | p95 req | Errors | Pending avg / peak | Acquired peak / max | Notes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 × 2 (old default) | 25 | 65.0 | 584 ms | 0.00% | 0.0 / 0 | 10 / 20 | Healthy baseline. |
| 10 × 2 (old default) | 30 | 60.7 | 1.06 s | 0.00% | 1.0 / 4 | 18 / 20 | First clear pool queueing. |
| 10 × 2 (old default) | 35 | 19.9 | 5.26 s | 0.00% | 5.6 / 16 | 19 / 20 | Throughput collapse at pool ceiling. |
| 20 × 2 | 40 | 81.5 | 1.11 s | 0.00% | 1.0 / 8 | 28 / 40 | Fastest 2-replica run, but queueing no longer stays near zero. |
| 10 × 3 | 40 | 74.0 | 1.23 s | 0.00% | 0.2 / 2 | 24 / 30 | Third replica helps; still slower than 15 × 3. |
| **15 × 3 (chosen default)** | **40** | **78.8** | **1.18 s** | **0.00%** | **0.2 / 2** | **23 / 45** | Best balance of throughput, latency, and near-zero pending connections. |
| 15 × 3 | 50 | 81.2 | 1.55 s | 0.00% | 0.0 / 0 | 38 / 45 | Error-free, but over the 1.5s latency guardrail. |

## What was verified locally

- `docker inspect` on a running `backend` container still confirms the resource
  limit is applied: `Memory: 805306368` (768 MiB), `NanoCpus: 1000000000`
  (1.0 CPU).
- `java -XX:+PrintFlagsFinal` inside the built image still confirms the JVM
  honors the explicit flags: `MaxHeapSize=536870912` (512MB),
  `InitialHeapSize=268435456` (256MB), `MaxMetaspaceSize=134217728` (128MB),
  `UseContainerSupport=true`.
- All three tuned-default replicas started and passed `/actuator/health`
  checks together with Postgres and Traefik.
- The old "8,000 VU number is not yet established" gap is now closed for this
  **local resource-constrained baseline**: we have a real recorded SLO and
  measured saturation onset for the shipped default config. The separate
  high-infrastructure 8,000-VU exercise remains tracked by #297 and was not
  attempted here.

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
