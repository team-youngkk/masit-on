#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
APP_RUN_SCRIPT="$REPOSITORY_ROOT/deploy/scripts/app-run.sh"
APP_DEPLOY_SCRIPT="$REPOSITORY_ROOT/deploy/scripts/app-deploy.sh"

extract_contract() {
  awk '
    /# BEGIN SHARED REDIS ENDPOINT CONTRACT/ { inside = 1; next }
    /# END SHARED REDIS ENDPOINT CONTRACT/ { exit }
    inside { print }
  ' "$1"
}

run_validation() {
  local script="$1"
  local host="$2"
  local port="$3"
  local contract
  contract="$(extract_contract "$script")"
  bash -c "$contract
validate_shared_redis_endpoint \"\$1\" \"\$2\"" \
    -- "$host" "$port"
}

assert_rejected() {
  local script="$1"
  local host="$2"
  local port="$3"
  if run_validation "$script" "$host" "$port" >/dev/null 2>&1; then
    printf 'expected rejection: host=<redacted>, port=<redacted>, script=%s\n' "$(basename "$script")" >&2
    exit 1
  fi
}

assert_accepted() {
  local script="$1"
  local host="$2"
  local port="$3"
  run_validation "$script" "$host" "$port" >/dev/null
}

run_contract_cases() {
  local script="$1"
  assert_rejected "$script" '' '6379'
  assert_rejected "$script" '127.0.0.1' '6379'
  assert_rejected "$script" '127.42.7.9' '6379'
  assert_rejected "$script" 'localhost' '6379'
  assert_rejected "$script" '0.0.0.0' '6379'
  assert_rejected "$script" '::1' '6379'
  assert_rejected "$script" '0000:0000:0000:0000:0000:0000:0000:0001' '6379'
  assert_rejected "$script" 'redis.internal.example' ''
  assert_rejected "$script" 'redis.internal.example' '0'
  assert_rejected "$script" 'redis.internal.example' '65536'
  assert_rejected "$script" 'redis.internal.example' 'not-a-port'
  assert_accepted "$script" 'redis.internal.example' '6379'
  assert_accepted "$script" '10.20.30.40' '6379'
  assert_accepted "$script" 'fd00::10' '6379'
  assert_accepted "$script" 'redis.internal.example' '1'
  assert_accepted "$script" 'redis.internal.example' '65535'
}

app_run_contract="$(extract_contract "$APP_RUN_SCRIPT")"
app_deploy_contract="$(extract_contract "$APP_DEPLOY_SCRIPT")"
if [ "$app_run_contract" != "$app_deploy_contract" ]; then
  echo 'app-run.sh와 app-deploy.sh의 Redis endpoint 계약이 다르다' >&2
  exit 1
fi

run_contract_cases "$APP_RUN_SCRIPT"
run_contract_cases "$APP_DEPLOY_SCRIPT"
echo 'Redis endpoint contract: PASS'
