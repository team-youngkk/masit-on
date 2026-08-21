#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/deploy/scripts/health-metrics.sh"
SECRETS_RENDERER="$ROOT/deploy/scripts/app-secrets-render.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/masiton-health-metrics-docker.XXXXXX")"
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
export DOCKER_CAPTURE="$TEST_ROOT/docker-arguments"
export TLS_CERT="$TEST_ROOT/no-certificate.pem"
export REDIS_CLI_IMAGE=redis:8.8-alpine
ORIGINAL_PATH="$PATH"
CONTROLLED_PATH="$BIN"
old_ifs="$IFS"
IFS=:
for path_entry in $ORIGINAL_PATH; do
  # Keep every original utility directory except directories that expose a
  # system redis-cli. The shims in $BIN remain available to the fixture.
  if [ -e "$path_entry/redis-cli" ] || [ -e "$path_entry/redis-cli.exe" ]; then
    continue
  fi
  CONTROLLED_PATH="$CONTROLLED_PATH:$path_entry"
done
IFS="$old_ifs"
# Do not inherit a system redis-cli: this test must exercise the Docker branch.
export PATH="$CONTROLLED_PATH"
if command -v redis-cli >/dev/null 2>&1; then
  echo 'controlled PATH unexpectedly exposes redis-cli' >&2
  exit 1
fi
unset REDISCLI_AUTH

# Mirror app-secrets-render.sh: the client receives a 0400 uid/gid-owned file.
printf 'test-secret\n' > "$REDIS_PASSWORD_FILE"
chmod 0400 "$REDIS_PASSWORD_FILE"
secret_owner="$(stat -c '%u:%g' "$REDIS_PASSWORD_FILE")"
export SECRET_OWNER="$secret_owner"
secret_mode="$(stat -c '%a' "$REDIS_PASSWORD_FILE")"
if [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* || "$OSTYPE" == win32* ]]; then
  # Git Bash maps chmod to the Windows ACL and reports 0444 for this fixture.
  [[ "$secret_mode" = 400 || "$secret_mode" = 444 ]] || exit 1
else
  [ "$secret_mode" = 400 ] || exit 1
fi
[[ "$secret_owner" =~ ^[0-9]+:[0-9]+$ ]] || exit 1

cat > "$BIN/curl" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
last="${!#}"
case "$last" in
  http://169.254.169.254/latest/api/token) printf 'imds-token' ;;
  http://169.254.169.254/latest/meta-data/instance-id) printf 'i-health-metrics-docker-test' ;;
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

cat > "$BIN/docker" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$@" > "$DOCKER_CAPTURE"
for arg in "$@"; do
  case "$arg" in
    *test-secret*) echo 'secret in docker args' >&2; exit 1 ;;
  esac
done
if env | grep -Fq 'test-secret'; then echo 'secret in docker env' >&2; exit 1; fi
[ "${1:-}" = run ] || exit 1
shift
user=''
mount=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --rm) shift ;;
    --network|--user|--mount)
      option="$1"
      value="$2"
      shift 2
      case "$option" in
        --network) [ "$value" = host ] || exit 1 ;;
        --user) user="$value" ;;
        --mount) mount="$value" ;;
      esac
      ;;
    *) image="$1"; shift; break ;;
  esac
done
[ "$image" = "$REDIS_CLI_IMAGE" ] || exit 1
[ "$user" = "$SECRET_OWNER" ] || exit 1
[ "$mount" = "type=bind,src=$REDIS_PASSWORD_FILE,dst=/run/masiton-redis-password,readonly" ] || exit 1
[ "${1:-}" = sh ] && [ "${2:-}" = -c ] || exit 1
container_command="$3"
case "$container_command" in
  *'/run/masiton-redis-password'*) ;;
  *) exit 1 ;;
esac
case "$container_command" in
  *'tr -d'*) ;;
  *) exit 1 ;;
esac
shift 3
# Simulate the stock image's redis-cli after validating the exact command shape.
# Read through the mounted source here because Git Bash cannot reproduce Alpine
# ash's backslash handling for tr, while the production command is still checked above.
password="$(sed 's/\r//g' < "$REDIS_PASSWORD_FILE")"
[ "$password" = test-secret ] || exit 1
cat "$REDIS_INFO_FIXTURE"
SHIM

chmod 0755 "$BIN/curl" "$BIN/aws" "$BIN/python3" "$BIN/docker"
printf 'used_memory:1048576\nmaxmemory:4194304\n' > "$REDIS_INFO_FIXTURE"
"$SCRIPT" > "$TEST_ROOT/output" 2>&1 || { cat "$TEST_ROOT/output" >&2; exit 1; }
grep -Fq 'MetricName=RedisUsedMemoryBytes,Value=1048576' "$AWS_CAPTURE" || {
  cat "$TEST_ROOT/output" >&2
  cat "$DOCKER_CAPTURE" >&2
  exit 1
}
grep -Fq -- '--user' "$DOCKER_CAPTURE" || exit 1
grep -Fq 'type=bind,src=' "$DOCKER_CAPTURE" || exit 1
grep -Fq 'APP_UID="${APP_UID:-1001}"' "$SECRETS_RENDERER"
grep -Fq 'APP_GID="${APP_GID:-1001}"' "$SECRETS_RENDERER"
grep -Fq 'chown "$APP_UID:$APP_GID" "$path"' "$SECRETS_RENDERER"
grep -Fq 'chmod 0400 "$path"' "$SECRETS_RENDERER"
echo 'health-metrics Docker fallback contract: PASS'
