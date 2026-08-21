#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/deploy/scripts/health-metrics.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/masiton-health-metrics-endpoint.XXXXXX")"
BIN="$TEST_ROOT/bin"
mkdir -p "$BIN"
trap 'rm -rf "$TEST_ROOT"' EXIT

export DEPLOYMENT_ENV_FILE="$TEST_ROOT/nonexistent-deployment.env"
export HEALTH_BASE=http://health-metrics.test
export REDIS_PASSWORD_FILE="$TEST_ROOT/redis-password"
export REDIS_INFO_FIXTURE="$TEST_ROOT/redis-info"
export AWS_CAPTURE="$TEST_ROOT/aws-arguments"
export REDIS_CLI_CAPTURE="$TEST_ROOT/redis-cli-arguments"
export DOCKER_CAPTURE="$TEST_ROOT/docker-arguments"
export TLS_CERT="$TEST_ROOT/no-certificate.pem"
export PATH="$BIN:$PATH"
unset REDISCLI_AUTH

grep -Fq 'A colon is never valid in the hostname form' "$SCRIPT" || exit 1

printf 'test-secret\n' > "$REDIS_PASSWORD_FILE"
printf 'used_memory:1048576\nmaxmemory:4194304\n' > "$REDIS_INFO_FIXTURE"

cat > "$BIN/curl" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
last="${!#}"
case "$last" in
  http://169.254.169.254/latest/api/token) printf 'imds-token' ;;
  http://169.254.169.254/latest/meta-data/instance-id) printf 'i-health-metrics-endpoint-test' ;;
  "$HEALTH_BASE/internal/health/live") printf '{"status":"UP"}' ;;
  "$HEALTH_BASE/internal/health/ready") printf '{"status":"UP"}' ;;
  "$HEALTH_BASE/internal/health/dependencies") printf '{"components":{"db":{"status":"UP"},"redis":{"status":"UP"}}}' ;;
  *) echo "unexpected curl target: $last" >&2; exit 1 ;;
esac
SHIM

cat > "$BIN/aws" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
if [ "${1:-}" = cloudwatch ] && [ "${2:-}" = put-metric-data ]; then
  printf '%s\n' "$@" > "$AWS_CAPTURE"
  exit 0
fi
echo 'unexpected aws call' >&2
exit 1
SHIM

cat > "$BIN/python3" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
args=("$@")
body="${args[$((${#args[@]} - 2))]}"
component="${args[$((${#args[@]} - 1))]}"
if [[ "$body" == *'"status":"UP"'* ]] || [[ "$body" == *"\"$component\":{\"status\":\"UP\"}"* ]]; then
  printf '1\n'
else
  printf '0\n'
fi
SHIM

cat > "$BIN/getent" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
[ "${1:-}" = ahostsv4 ] || exit 2
case "${2:-}" in
  redis.safe.test) printf '10.42.0.15 STREAM redis.safe.test\n' ;;
  redis.unsafe.test) printf '127.0.0.1 STREAM redis.unsafe.test\n' ;;
  redis.mixed.test) printf '10.42.0.15 STREAM redis.mixed.test\n169.254.1.1 STREAM redis.mixed.test\n' ;;
  *) exit 2 ;;
esac
SHIM

cat > "$BIN/redis-cli" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
[ -z "${REDISCLI_AUTH:-}" ] || { echo 'REDISCLI_AUTH must not carry direct-path credentials' >&2; exit 1; }
[ "$(cat)" = test-secret ] || { echo 'password was not read from the protected file through stdin' >&2; exit 1; }
printf '%s\n' "$@" > "$REDIS_CLI_CAPTURE"
cat "$REDIS_INFO_FIXTURE"
SHIM

cat > "$BIN/docker" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$@" > "$DOCKER_CAPTURE"
exit 1
SHIM

chmod 0755 "$BIN/curl" "$BIN/aws" "$BIN/python3" "$BIN/getent" "$BIN/redis-cli" "$BIN/docker"

assert_contains() {
  local expected="$1" file="$2"
  grep -Fq -- "$expected" "$file" || { echo "expected '$expected' in $file" >&2; exit 1; }
}

assert_not_called() {
  local file="$1"
  [ ! -e "$file" ] || { echo "unexpected client invocation: $file" >&2; exit 1; }
}

run_valid() {
  local host="$1" expected_address="$2"
  rm -f "$REDIS_CLI_CAPTURE" "$DOCKER_CAPTURE"
  REDIS_HOST="$host" REDIS_PORT=6379 "$SCRIPT" > "$TEST_ROOT/valid.output" 2>&1
  assert_contains "$expected_address" "$REDIS_CLI_CAPTURE"
  assert_not_called "$DOCKER_CAPTURE"
}

run_rejected() {
  local host="$1" port="$2"
  rm -f "$REDIS_CLI_CAPTURE" "$DOCKER_CAPTURE"
  REDIS_HOST="$host" REDIS_PORT="$port" "$SCRIPT" > "$TEST_ROOT/rejected.output" 2>&1
  assert_not_called "$REDIS_CLI_CAPTURE"
  assert_not_called "$DOCKER_CAPTURE"
}

run_valid 10.42.0.15 10.42.0.15
run_valid redis.safe.test 10.42.0.15

for host in \
  127.0.0.1 169.254.1.1 0.0.0.0 224.0.0.1 255.255.255.255 \
  0177.0.0.1 127.1 0x7f000001 ::ffff:10.42.0.15 \
  8.8.8.8 redis.unsafe.test redis.mixed.test; do
  run_rejected "$host" 6379
done
for port in 0 0006379 65536 6379x; do
  run_rejected 10.42.0.15 "$port"
done

echo 'health-metrics endpoint contract: PASS'
