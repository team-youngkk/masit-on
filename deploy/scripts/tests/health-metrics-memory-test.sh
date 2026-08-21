#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/deploy/scripts/health-metrics.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/masiton-health-metrics.XXXXXX")"
BIN="$TEST_ROOT/bin"
mkdir -p "$BIN"
trap 'rm -rf "$TEST_ROOT"' EXIT

export DEPLOYMENT_ENV_FILE="$TEST_ROOT/nonexistent-deployment.env"
export HEALTH_BASE=http://health-metrics.test
export REDIS_HOST=10.42.0.15
export REDIS_PORT=6379
export REDIS_PASSWORD_FILE="$TEST_ROOT/redis-password"
export REDIS_INFO_FIXTURE="$TEST_ROOT/redis-info"
export AWS_CAPTURE="$TEST_ROOT/aws-arguments"
export TLS_CERT="$TEST_ROOT/no-certificate.pem"
export PATH="$BIN:$PATH"
unset REDISCLI_AUTH

printf 'test-secret\n' > "$REDIS_PASSWORD_FILE"

cat > "$BIN/curl" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
last="${!#}"
case "$last" in
  http://169.254.169.254/latest/api/token) printf 'imds-token' ;;
  http://169.254.169.254/latest/meta-data/instance-id) printf 'i-health-metrics-test' ;;
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
echo "unexpected aws call" >&2
exit 1
SHIM

cat > "$BIN/redis-cli" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
[ -z "${REDISCLI_AUTH:-}" ] || { echo 'REDISCLI_AUTH must not carry direct-path credentials' >&2; exit 1; }
[ "$(cat)" = test-secret ] || { echo 'redis-cli did not read auth from stdin' >&2; exit 1; }
if [ "${REDIS_UNAVAILABLE:-0}" = 1 ]; then
  exit 1
fi
cat "$REDIS_INFO_FIXTURE"
SHIM

chmod 0755 "$BIN/curl" "$BIN/aws" "$BIN/redis-cli"

assert_contains() {
  local expected="$1" file="$2"
  grep -Fq -- "$expected" "$file" || {
    echo "expected '$expected' in $file" >&2
    exit 1
  }
}

assert_not_contains() {
  local unexpected="$1" file="$2"
  if grep -Fq -- "$unexpected" "$file"; then
    echo "did not expect '$unexpected' in $file" >&2
    exit 1
  fi
}

run_case() {
  local name="$1" fixture="$2" unavailable="${3:-0}"
  printf '%s\n' "$fixture" > "$REDIS_INFO_FIXTURE"
  : > "$AWS_CAPTURE"
  if ! REDIS_UNAVAILABLE="$unavailable" "$SCRIPT" > "$TEST_ROOT/$name.output" 2>&1; then
    cat "$TEST_ROOT/$name.output" >&2
    echo "$name: health-metrics.sh failed" >&2
    exit 1
  fi

  assert_contains 'MetricName=DependencyRedis,' "$AWS_CAPTURE"
  assert_contains 'MetricName=FleetDependencyRedis,' "$AWS_CAPTURE"
  assert_not_contains 'MetricName=RedisUsedMemoryBytes' "$AWS_CAPTURE"
  assert_not_contains 'MetricName=RedisMaxMemoryBytes' "$AWS_CAPTURE"
  assert_not_contains 'MetricName=RedisMemoryUtilizationPercent' "$AWS_CAPTURE"
}

printf 'used_memory:1048576\nmaxmemory:4194304\n' > "$REDIS_INFO_FIXTURE"
: > "$AWS_CAPTURE"
REDIS_UNAVAILABLE=0 "$SCRIPT" > "$TEST_ROOT/valid.output" 2>&1
assert_contains 'MetricName=RedisUsedMemoryBytes,Value=1048576,Unit=Bytes,Dimensions=[{Name=Environment,Value=asg}]' "$AWS_CAPTURE"
assert_contains 'MetricName=RedisMaxMemoryBytes,Value=4194304,Unit=Bytes,Dimensions=[{Name=Environment,Value=asg}]' "$AWS_CAPTURE"
assert_contains 'MetricName=RedisMemoryUtilizationPercent,Value=25,Unit=Percent,Dimensions=[{Name=Environment,Value=asg}]' "$AWS_CAPTURE"

run_case invalid $'used_memory:not-a-number\nmaxmemory:4194304'
run_case unavailable $'used_memory:1048576\nmaxmemory:4194304' 1
run_case zero $'used_memory:1048576\nmaxmemory:0'

echo 'health-metrics memory contract: PASS'
