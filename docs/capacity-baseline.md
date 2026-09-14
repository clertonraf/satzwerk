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
- p95 request latency staying below the **1.5s ad-hoc saturation ceiling used
  for this local study** (`http_req_duration p95 ≈ 1.18s`)

This is the recorded local SLO because it was the highest observed load that
met all three criteria above at once: 0% errors, near-zero pool queueing, and
the study's 1.5s latency ceiling. At **50** concurrent clients, the same
3-replica / pool-15 setup still stayed error-free with zero pending samples,
but crossed the latency ceiling (`p95 ≈ 1.55s`), so 50 is better treated as
the beginning of saturation for this local study rather than the default target
for a modest self-hosted gym tracker.

## Relation to the repo's actual CI perf gate

This local saturation study does **not** use the same latency threshold as the
automated k6 regression gate. The real enforced CI threshold in `perf/stress.js`
for the mixed scenario is **`p(95) < 500 ms`**, not 1.5s.

That difference matters:

- Under the tuned **15 × 3** configuration, the measured mixed-scenario p95 was
  **449 ms at 20 VUs** and **529 ms at 25 VUs**.
- So this workload crosses the repo's actual enforced CI latency gate
  **somewhere between 20 and 25 concurrent clients** on the 2 vCPU / 4 GiB
  Colima host used for this study.
- The documented **40 concurrent clients** SLO therefore means
  "**error-free with near-zero pool queueing under this local study's 1.5s
  latency ceiling**" — **not** "passes the repository's CI perf gate at 40
  concurrent clients."

There is also a topology difference: `.github/workflows/perf.yml` currently runs
the same `perf/stress.js` script on a GitHub-hosted runner against a Compose
stack forced to **`BACKEND_REPLICAS=1`** via `.env` plus
`docker-compose.override.yml`, so it does **not** exercise the same 3-replica
resource-constrained topology measured here. I did not re-run that CI workflow
after changing the defaults, so whether the workflow currently passes with the
new pool default in its single-replica setup remains an open verification gap.

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
| 15 × 3 | 20 | 56.7 | 449 ms | 0.00% | 0.1 / 1 | 8 / 45 | Last measured point that stayed under the CI mixed-scenario 500 ms p95 gate. |
| 15 × 3 | 25 | 67.4 | 529 ms | 0.00% | 0.0 / 0 | 11 / 45 | First measured point above the CI mixed-scenario 500 ms p95 gate. |
| **15 × 3 (chosen default)** | **40** | **78.8** | **1.18 s** | **0.00%** | **0.2 / 2** | **23 / 45** | Best balance of throughput, latency, and near-zero pending connections. |
| 15 × 3 | 50 | 81.2 | 1.55 s | 0.00% | 0.0 / 0 | 38 / 45 | Error-free, but over this study's 1.5s saturation ceiling. |

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
- The prior doc's **unestablished-number gap for this local
  resource-constrained baseline** is now replaced with a real measured SLO and
  saturation table for the shipped default config. The separate
  high-infrastructure 8,000-VU / full-infra exercise remains tracked by #297
  and was not attempted here.

## Write-saturation ceiling test — local execution guide

The table above closes the gap for the **resource-constrained 2 vCPU / 4 GiB**
local baseline, but it does **not** establish the true write-failure ceiling for
the shipped **3 backend replicas / pool max-size 15** topology. The earlier
attempt to ramp this workload toward **8,000 VUs** never reached an
application-level failure point because the **host VM** OOM-killed the k6
harness first (`exit 137`) at roughly **2,000-2,700 VUs**.

This section documents how to rerun that ceiling study locally on a machine
with enough Docker/Colima resources to let the application fail on its own
terms rather than letting the load generator die first.

### 1. Preflight the host VM

Run the new preflight before touching Compose:

```bash
./scripts/check-perf-host-resources.sh
```

It uses the documented failed attempt as the sizing floor:

- Observed host-OOM point: about **2,700 VUs** with a **4 GiB** Colima VM.
- Effective memory floor from that failure point: `4096 MiB / 2700 ≈ 1.52 MiB`
  per VU.
- Linear projection for **8,000 VUs**: `8000 × 1.52 MiB ≈ 12.1 GiB`.
- Recommended starting allocation: **16 GiB RAM**. That is not a claim that
  16 GiB is the final answer; it is the first sensible tier *above* the
  extrapolated floor so the run is not parked on the same OOM cliff.
- CPU scales similarly: `2 vCPU / 2700 × 8000 ≈ 5.9`, rounded up to a
  starting point of **6 vCPU**.

If the script fails, resize the Docker VM before continuing.

### 2. Raise Colima or Docker Desktop resources

For Colima, restart it with at least the documented starting point:

```bash
colima stop
colima start --cpu 6 --memory 16
```

If you use Docker Desktop instead of Colima, set the Docker VM to at least
**6 CPUs** and **16 GiB memory** in **Settings → Resources** before proceeding.

### 3. Start the tuned multi-replica stack

Do **not** use `docker-compose.override.yml` for this run — that local-dev file
forces `backend` back down to **1 replica** so it is intentionally the wrong
topology for this study. Start only the services the write-saturation run needs:
`postgres`, `redis`, `traefik`, and `backend`.

From the repo root:

```bash
cp .env.example .env
# then set DB_PASSWORD and JWT_SECRET if you have not already

docker build -t ghcr.io/clertonraf/satzwerk-backend:latest ./backend

COMPOSE_PROJECT_NAME=satzwerk-perf \
BACKEND_REPLICAS=3 \
R2DBC_POOL_MAX_SIZE=15 \
docker compose -f docker-compose.yml up -d postgres redis traefik backend
```

Using `COMPOSE_PROJECT_NAME=satzwerk-perf` makes the Docker network name
predictable for the k6 container in the next step.

### 4. Run the full local write-saturation ramp

`perf/stress.js` now accepts a custom stage file so the same script can keep
its small default CI gate while also driving the full local ceiling study. The
companion stage file `perf/write-saturation-8000-stages.json` ramps from 250 to
8,000 VUs, holds there for two minutes, then ramps down.

Run it from the repo root:

```bash
docker rm -f satzwerk-k6-ceiling 2>/dev/null || true
docker run --name satzwerk-k6-ceiling \
  --network satzwerk-perf_default \
  -v "$PWD/perf:/perf:ro" \
  grafana/k6 run \
    -e BASE_URL=http://traefik \
    -e SUMMARY_ENABLED=false \
    -e MIXED_P95_THRESHOLD_MS=off \
    -e MIXED_STAGES_FILE=/perf/write-saturation-8000-stages.json \
    /perf/stress.js
```

The `SUMMARY_ENABLED=false` flag removes the read-only summary side-scenario so
this run measures the write-heavy mixed flow only. `MIXED_P95_THRESHOLD_MS=off`
turns off the CI-oriented 500 ms latency gate, which would otherwise fail the
run long before the application actually starts erroring.

### 5. What counts as the actual ceiling

For this study, the ceiling is **not** merely the first stage with higher p95
latency or non-zero R2DBC pending counts. Those signals indicate backpressure
and queueing, which are expected before failure.

Treat the ceiling as the first VU band where the application starts to produce
**persistent request failures**, such as:

- `5xx` responses
- connection resets
- upstream timeouts
- requests that fail because the app can no longer complete them in time

When that happens, record both numbers:

1. the **last fully stable stage** (0% HTTP failures, only backpressure), and
2. the **first failing stage** (errors begin and remain visible in the output).

If the first failing band is too wide for a confident answer, rerun `perf/stress.js`
with a narrower custom `MIXED_STAGES_JSON='[...]'` centered on that band to
pinpoint the transition more precisely.

### 6. Troubleshooting host OOM vs. app-level failure

If the run dies around the low-thousands of VUs with no application errors in
the k6 output, assume **host resource exhaustion first**. The known host-failure
signature from the earlier attempt is:

- the k6 container exits with **137**
- Colima's kernel log shows OOM-killer lines
- backend / Traefik logs do **not** show matching waves of 5xx or connection
  failures because the load generator died before the app did

Useful checks:

```bash
docker inspect satzwerk-k6-ceiling --format '{{.State.ExitCode}}'
docker logs satzwerk-k6-ceiling | tail -50
colima ssh -- sudo dmesg | grep -Ei 'killed process|out of memory|oom' | tail -20
COMPOSE_PROJECT_NAME=satzwerk-perf docker compose -f docker-compose.yml logs --tail=100 backend traefik postgres
```

Interpretation:

- **Exit 137 + OOM log lines** → the Docker VM was still too small; raise
  Colima/Docker Desktop resources and retry. This is **not** the app ceiling.
- **k6 completes but reports persistent 5xx/timeouts/resets at a stage** → that
  is the real application-level ceiling band to record.

### 7. Roll back the temporary perf setup afterward

Tear down the stack, remove the retained k6 container, and return your Docker VM
to its usual size once you are done:

```bash
COMPOSE_PROJECT_NAME=satzwerk-perf docker compose -f docker-compose.yml down -v --remove-orphans
docker rm -f satzwerk-k6-ceiling 2>/dev/null || true
colima stop
colima start --cpu 2 --memory 4
```

If your normal Colima profile uses different values, restore those instead of
blindly using `2 / 4`. For Docker Desktop, revert the CPU and memory settings in
**Settings → Resources** after the test so the machine is not left permanently
over-provisioned.

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

## Redis read-cache A/B baseline for issue #296

This branch also measured the new Redis-backed cache for the per-user
`Exercise` catalog plus analytics `Heatmap` and streak reads.

### Single-backend read-only sanity check

The original local A/B pass used `perf/read-heavy-cache.js` against a single
backend container published on `localhost:8083` with **20 constant VUs for
30 seconds**. That run stayed useful as a sanity check after the coroutine
threading fix, but it was not the best fit for issue #296's actual goal because
it had neither cross-replica sharing nor concurrent write contention.

| Endpoint | Before p95 (`CACHE_ENABLED=false`) | After p95 (`CACHE_ENABLED=true`) | Delta |
| --- | ---: | ---: | ---: |
| `GET /api/exercises` | 32.07 ms | 42.60 ms | +10.53 ms |
| `GET /api/analytics/heatmap` | 49.23 ms | 52.06 ms | +2.83 ms |
| `GET /api/analytics/streak` | 37.19 ms | 47.71 ms | +10.52 ms |
| Overall `http_req_duration` | 38.34 ms | 47.95 ms | +9.61 ms |

That small regression is the cost of the extra Redis version lookup added for
safe post-commit invalidation. It is no longer the earlier broken
triple-digit regression, but it also does not demonstrate the intended benefit.

### Multi-replica mixed workload benchmark

To measure the scenario issue #296 actually targets, I added
`perf/read-cache-under-write-contention.js` and reran the comparison against
the default **3-backend-replica** topology with simultaneous read and write
traffic.

#### Method

- Same Colima host as the rest of this document: **2 vCPUs / 4 GiB RAM**.
- Topology under test: `postgres + redis + 3 backend replicas` from
  `docker-compose.yml`.
- Load generator: a one-off `grafana/k6` container joined to the same Docker
  network, targeting `http://backend:8080` so Docker DNS resolves the scaled
  backend service across all three replicas.
- Read load: **18 constant reader VUs** for 60 seconds.
- Write load: **8 constant writer VUs** for 60 seconds.
- Reader iteration: `GET /api/exercises`,
  `GET /api/analytics/heatmap?from=<today>&to=<today>`,
  `GET /api/analytics/streak`.
- Writer iteration: register a new user, create an `Exercise`, create and
  activate a `WorkoutPlan`, create a `WorkoutGroup`, attach the `Exercise`,
  start a `WorkoutSession`, and add a `SetLog`.
- "Before" run: `CACHE_ENABLED=false`.
- "After" run: `CACHE_ENABLED=true`.
- Both runs completed with **0% HTTP failures**.

#### Measured result

Under 3-replica mixed read/write contention, the shared Redis cache **did**
show the intended improvement:

| Reader metric | Before (`CACHE_ENABLED=false`) | After (`CACHE_ENABLED=true`) | Delta |
| --- | ---: | ---: | ---: |
| Aggregate read p95 (`reader_total_duration`) | 90.51 ms | 22.66 ms | -67.85 ms (-75.0%) |
| `GET /api/exercises` p95 | 155.66 ms | 24.66 ms | -131.00 ms (-84.2%) |
| `GET /api/analytics/heatmap` p95 | 33.21 ms | 21.89 ms | -11.32 ms (-34.1%) |
| `GET /api/analytics/streak` p95 | 27.39 ms | 21.43 ms | -5.96 ms (-21.7%) |
| Total read throughput | 213.27 req/s | 236.39 req/s | +23.12 req/s (+10.8%) |
| Total HTTP throughput | 344.83 req/s | 360.35 req/s | +15.52 req/s (+4.5%) |

#### Interpretation

- The single-backend read-only A/B check understated the benefit because it
  measured only the local cost of an extra Redis read, not the avoided Postgres
  work during concurrent writes.
- In the 3-replica mixed workload, Redis reduced read p95 substantially and
  increased read throughput, which matches the feature's intended value:
  shielding Postgres from repeated read traffic while keeping cache contents
  consistent across replicas.
- The biggest win appears on `GET /api/exercises`, which is both highly reused
  and explicitly invalidated on mutation rather than frequently recomputed.
- The analytics endpoints improved too, but by a smaller margin because their
  short TTL and write-driven invalidations naturally keep them closer to the
  source-of-truth path.

### Investigation notes

- Compose-network RTT from `backend` to `redis` stayed low: `ping redis`
  measured **0.124-0.276 ms**.
- A raw Redis round-trip from inside the `backend` container using `nc` +
  RESP `PING` measured **3.48-4.93 ms**, including process-launch overhead for
  the probe itself.
- Temporary app-level timing around
  `redisTemplate.opsForValue().get(key).awaitFirstOrNull()` showed the actual
  Redis call in the request path at **0.38-3.05 ms**; corresponding `set(...)`
  calls measured **0.47-2.22 ms**. JSON serialize/deserialize for these small
  payloads stayed in the low single-digit milliseconds too.
- Spring Boot's `LettuceConnectionFactory` was configured with
  `shareNativeConnection=true`, so the regression was **not** caused by opening
  a new Redis TCP connection per request.
- The original cache-enabled implementation was fine at **1 VU** (per-endpoint
  p95 roughly **9-17 ms**), but degraded sharply under concurrency:
  **5 VUs** produced cache-hit p95s of **162-223 ms**, and **20 VUs** produced
  **199-283 ms**. That pattern matched thread contention rather than network
  latency.
- After moving cache work off the Lettuce event loop, the same cache-enabled
  benchmark dropped to **21-29 ms p95 at 5 VUs** and **8-12 ms p95 at 20 VUs**.
