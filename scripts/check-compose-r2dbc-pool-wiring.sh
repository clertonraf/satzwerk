#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd -- "$SCRIPT_DIR/.." && pwd)
COMPOSE_FILE=${1:-"$REPO_ROOT/docker-compose.yml"}
DEFAULT_EXPECTED=${DEFAULT_EXPECTED:-15}
OVERRIDE_EXPECTED=${OVERRIDE_EXPECTED:-30}

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is required to render Compose config." >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: python3 is required to inspect Compose JSON output." >&2
  exit 1
fi

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "ERROR: Compose file not found: $COMPOSE_FILE" >&2
  exit 1
fi

render_pool_size() {
  local override_value=${1-}

  if [ -n "$override_value" ]; then
    COMPOSE_DISABLE_ENV_FILE=1 DB_PASSWORD=check JWT_SECRET=check R2DBC_POOL_MAX_SIZE="$override_value" \
      docker compose -f "$COMPOSE_FILE" config --format json
  else
    env -u R2DBC_POOL_MAX_SIZE COMPOSE_DISABLE_ENV_FILE=1 DB_PASSWORD=check JWT_SECRET=check \
      docker compose -f "$COMPOSE_FILE" config --format json
  fi | python3 -c 'import json, sys
data = json.load(sys.stdin)
services = data.get("services", {})
backend = services.get("backend")
if backend is None:
    raise SystemExit("ERROR: backend service missing from rendered Compose config.")
environment = backend.get("environment")
if not isinstance(environment, dict):
    raise SystemExit("ERROR: backend.environment missing from rendered Compose config.")
value = environment.get("R2DBC_POOL_MAX_SIZE")
if value is None:
    raise SystemExit("ERROR: backend environment is missing R2DBC_POOL_MAX_SIZE.")
print(value)'
}

assert_pool_size() {
  local label=$1
  local expected=$2
  local actual=$3

  if [ "$actual" != "$expected" ]; then
    echo "FAIL: $label rendered R2DBC_POOL_MAX_SIZE=$actual (expected $expected)" >&2
    exit 1
  fi

  echo "PASS: $label rendered R2DBC_POOL_MAX_SIZE=$actual"
}

default_value=$(render_pool_size "")
assert_pool_size "default" "$DEFAULT_EXPECTED" "$default_value"

override_value=$(render_pool_size "$OVERRIDE_EXPECTED")
assert_pool_size "override" "$OVERRIDE_EXPECTED" "$override_value"

echo "Compose R2DBC pool wiring is correct in $COMPOSE_FILE."
