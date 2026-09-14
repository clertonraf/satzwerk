#!/usr/bin/env bash
set -euo pipefail

TARGET_VUS=${TARGET_VUS:-8000}
OBSERVED_OOM_VUS=${OBSERVED_OOM_VUS:-2700}
OBSERVED_OOM_MEM_GIB=${OBSERVED_OOM_MEM_GIB:-4}
RECOMMENDED_CPUS=${RECOMMENDED_CPUS:-6}
RECOMMENDED_MEM_GIB=${RECOMMENDED_MEM_GIB:-16}
BACKEND_REPLICAS=${BACKEND_REPLICAS:-3}
R2DBC_POOL_MAX_SIZE=${R2DBC_POOL_MAX_SIZE:-15}

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is required for this preflight." >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: python3 is required for this preflight." >&2
  exit 1
fi

docker_cpu=$(docker info --format '{{.NCPU}}')
docker_mem_bytes=$(docker info --format '{{.MemTotal}}')
docker_context_name=$(docker context show 2>/dev/null || true)
docker_context_host=$(docker context inspect "$docker_context_name" --format '{{json (index .Endpoints "docker").Host}}' 2>/dev/null | tr -d '"' || true)

colima_json=''
if command -v colima >/dev/null 2>&1; then
  colima_json=$(colima list --json 2>/dev/null || true)
  if [ -z "$colima_json" ]; then
    colima_json=$(colima status --json 2>/dev/null || true)
  fi
fi

python3 - "$docker_cpu" "$docker_mem_bytes" "$docker_context_name" "$docker_context_host" "$colima_json" <<'PY'
import json
import math
import sys


def gib_to_bytes(value: int) -> int:
    return value * 1024 * 1024 * 1024


def bytes_to_gib(value: int) -> float:
    return value / (1024 * 1024 * 1024)


def bytes_to_mib(value: int) -> float:
    return value / (1024 * 1024)


def render_bytes_gib(value: int) -> str:
    return f"{bytes_to_gib(value):.2f} GiB"


def env_int(name: str, default: int) -> int:
    import os

    return int(os.environ.get(name, default))


docker_cpu = int(sys.argv[1])
docker_mem_bytes = int(sys.argv[2])
docker_context_name = sys.argv[3]
docker_context_host = sys.argv[4]
colima_raw = sys.argv[5]

TARGET_VUS = env_int("TARGET_VUS", 8000)
OBSERVED_OOM_VUS = env_int("OBSERVED_OOM_VUS", 2700)
OBSERVED_OOM_MEM_GIB = env_int("OBSERVED_OOM_MEM_GIB", 4)
RECOMMENDED_CPUS = env_int("RECOMMENDED_CPUS", 6)
RECOMMENDED_MEM_GIB = env_int("RECOMMENDED_MEM_GIB", 16)
BACKEND_REPLICAS = env_int("BACKEND_REPLICAS", 3)
R2DBC_POOL_MAX_SIZE = env_int("R2DBC_POOL_MAX_SIZE", 15)

observed_mem_bytes = gib_to_bytes(OBSERVED_OOM_MEM_GIB)
mem_per_vu_mib = bytes_to_mib(observed_mem_bytes) / OBSERVED_OOM_VUS
projected_mem_bytes = math.ceil(mem_per_vu_mib * TARGET_VUS * 1024 * 1024)
projected_mem_gib = bytes_to_gib(projected_mem_bytes)

cpu_per_vu = 2 / OBSERVED_OOM_VUS
projected_cpu = cpu_per_vu * TARGET_VUS

def normalize_colima_memory(raw_value: int) -> int:
    if raw_value <= 0:
        return 0
    if raw_value < 1024:
        return gib_to_bytes(raw_value)
    return raw_value


def matching_colima_entry(parsed_payload):
    entries = parsed_payload if isinstance(parsed_payload, list) else [parsed_payload]
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        if entry.get("status", "").lower() != "running":
            continue
        socket = entry.get("docker_socket")
        name = entry.get("name", "")
        expected_context_names = {name, f"colima-{name}" if name else ""}
        if name == "default":
            expected_context_names.add("colima")
        if docker_context_host and socket and socket == docker_context_host:
            return entry
        if docker_context_name and docker_context_name in expected_context_names:
            return entry
    return None


colima_cpu = None
colima_mem_bytes = None
if colima_raw:
    parsed = json.loads(colima_raw)
    matched_entry = matching_colima_entry(parsed)
    if matched_entry is not None:
        colima_cpu = int(matched_entry.get("cpu", matched_entry.get("cpus", 0)) or 0)
        colima_mem_bytes = normalize_colima_memory(int(matched_entry.get("memory", 0) or 0))

effective_cpu = docker_cpu
cpu_source = "docker info"
if colima_cpu:
    effective_cpu = min(docker_cpu, colima_cpu)
    cpu_source = "lower of docker info / colima"

effective_mem_bytes = docker_mem_bytes
mem_source = "docker info"
if colima_mem_bytes:
    effective_mem_bytes = min(docker_mem_bytes, colima_mem_bytes)
    mem_source = "lower of docker info / colima"

cpu_ok = effective_cpu >= RECOMMENDED_CPUS
mem_ok = effective_mem_bytes >= gib_to_bytes(RECOMMENDED_MEM_GIB)

print("== Satzwerk write-saturation preflight ==")
print(f"Target workload: {TARGET_VUS} VUs against {BACKEND_REPLICAS} backend replicas (R2DBC pool max-size {R2DBC_POOL_MAX_SIZE})")
print()
print("Sizing basis from the documented failed local attempt:")
print(f"- Host VM OOM-killed the k6 harness at about {OBSERVED_OOM_VUS} VUs with {OBSERVED_OOM_MEM_GIB} GiB allocated (exit 137).")
print(f"- Linear memory floor from that failure point: {OBSERVED_OOM_MEM_GIB * 1024} MiB / {OBSERVED_OOM_VUS} VUs = {mem_per_vu_mib:.2f} MiB per VU.")
print(f"- Extrapolated floor for {TARGET_VUS} VUs: {TARGET_VUS} x {mem_per_vu_mib:.2f} MiB = {projected_mem_gib:.2f} GiB.")
print(f"- Recommended safe minimum: round that floor up to {RECOMMENDED_MEM_GIB} GiB so the VM is not parked on the same OOM cliff.")
print(f"- CPU scales similarly: 2 vCPU / {OBSERVED_OOM_VUS} VUs x {TARGET_VUS} VUs = {projected_cpu:.2f} vCPU, rounded to {RECOMMENDED_CPUS}.")
print()
print("Current runtime allocation:")
print(f"- Docker reports: {docker_cpu} vCPU, {render_bytes_gib(docker_mem_bytes)} (context: {docker_context_name or 'default'})")
if colima_cpu and colima_mem_bytes:
    print(f"- Colima reports: {colima_cpu} vCPU, {render_bytes_gib(colima_mem_bytes)} (matching active Docker socket)")
print(f"- Effective allocation used for the gate: {effective_cpu} vCPU ({cpu_source}), {render_bytes_gib(effective_mem_bytes)} ({mem_source})")
print()
print("Recommended minimum before the 8,000-VU local run:")
print(f"- CPU: >= {RECOMMENDED_CPUS} vCPU")
print(f"- Memory: >= {RECOMMENDED_MEM_GIB} GiB")
print()

if cpu_ok and mem_ok:
    print("PASS: host allocation meets the documented starting point for the local ceiling run.")
    sys.exit(0)

print("FAIL: host allocation is below the documented starting point for the local ceiling run.")
if not cpu_ok:
    print(f"- Increase CPU from {effective_cpu} to at least {RECOMMENDED_CPUS} vCPU.")
if not mem_ok:
    print(f"- Increase memory from {bytes_to_gib(effective_mem_bytes):.2f} GiB to at least {RECOMMENDED_MEM_GIB} GiB.")
print()
print("Suggested Colima reset:")
print(f"  colima stop && colima start --cpu {RECOMMENDED_CPUS} --memory {RECOMMENDED_MEM_GIB}")
print("If you use Docker Desktop instead, raise the VM to the same minimum values in Settings > Resources.")
sys.exit(1)
PY
