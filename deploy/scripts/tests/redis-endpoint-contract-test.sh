#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
APP_RUN_SCRIPT="$REPOSITORY_ROOT/deploy/scripts/app-run.sh"
APP_DEPLOY_SCRIPT="$REPOSITORY_ROOT/deploy/scripts/app-deploy.sh"
TEST_BIN_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_BIN_DIR"' EXIT
cat > "$TEST_BIN_DIR/getent" <<'EOF'
#!/usr/bin/env bash
if [ "${1-}" != ahosts ]; then
  exit 2
fi
query="${2-}"
no_addrconfig=no
if [ "$query" = --no-addrconfig ]; then
  no_addrconfig=yes
  query="${3-}"
fi
case "$query" in
  redis.internal.example) printf '%s STREAM 10.20.30.40\n' '10.20.30.40' ;;
  fallback-only.internal.example)
    if [ "$no_addrconfig" = yes ]; then
      exit 2
    fi
    printf '%s STREAM 10.20.30.41\n' '10.20.30.41'
    ;;
  public-numeric.internal.example) printf '%s STREAM 192.0.2.1\n' '192.0.2.1' ;;
  127.0.0.1.nip.io) printf '%s STREAM 127.0.0.1\n' '127.0.0.1' ;;
  mapped-loopback.internal.example) printf '%s STREAM ::ffff:7f00:1\n' '::ffff:7f00:1' ;;
  link-local.internal.example) printf '%s STREAM 169.254.1.2\n' '169.254.1.2' ;;
  multicast.internal.example) printf '%s STREAM 224.0.0.1\n' '224.0.0.1' ;;
  unspecified.internal.example) printf '%s STREAM 0.0.0.0\n' '0.0.0.0' ;;
  dual-stack.internal.example)
    printf '%s STREAM 10.20.30.40\n' '10.20.30.40'
    if [ "$no_addrconfig" = yes ]; then
      printf '%s STREAM ::1\n' '::1'
      printf '%s STREAM fe80::2\n' 'fe80::2'
    fi
    ;;
  private-dual-stack.internal.example)
    printf '%s STREAM 10.20.30.40\n' '10.20.30.40'
    if [ "$no_addrconfig" = yes ]; then
      printf '%s STREAM fd00::10\n' 'fd00::10'
    fi
    ;;
  *) exit 2 ;;
esac
EOF
chmod +x "$TEST_BIN_DIR/getent"

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
  PATH="$TEST_BIN_DIR:$PATH" bash -c "$contract
validate_shared_redis_endpoint \"\$1\" \"\$2\"" \
    -- "$host" "$port"
}

validated_host() {
  local script="$1"
  local host="$2"
  local port="$3"
  local contract
  contract="$(extract_contract "$script")"
  PATH="$TEST_BIN_DIR:$PATH" bash -c "$contract
validate_shared_redis_endpoint \"\$1\" \"\$2\"
printf '%s\\n' \"\$REDIS_VALIDATED_HOST\"" \
    -- "$host" "$port"
}

assert_validated_host() {
  local script="$1"
  local host="$2"
  local port="$3"
  local expected="$4"
  local actual
  actual="$(validated_host "$script" "$host" "$port")"
  [ "$actual" = "$expected" ] || {
    printf 'unexpected validated host: expected=<redacted>, actual=<redacted>, script=%s\\n' \
      "$(basename "$script")" >&2
    exit 1
  }
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
  assert_rejected "$script" '0177.0.0.1' '6379'
  assert_rejected "$script" '0177.1' '6379'
  assert_rejected "$script" '127.1' '6379'
  assert_rejected "$script" '::1' '6379'
  assert_rejected "$script" '::1%lo' '6379'
  assert_rejected "$script" '0000:0000:0000:0000:0000:0000:0000:0001' '6379'
  assert_rejected "$script" '::ffff:7f00:1' '6379'
  assert_rejected "$script" '0:0:0:0:0:ffff:7f00:1' '6379'
  assert_rejected "$script" '::ffff:127.0.0.1%lo' '6379'
  assert_rejected "$script" '::7f00:1%lo0' '6379'
  assert_rejected "$script" '0:0:0:0:0:0:7f00:1%lo' '6379'
  assert_rejected "$script" '169.254.1.2' '6379'
  assert_rejected "$script" '192.0.2.1' '6379'
  assert_rejected "$script" '198.51.100.1' '6379'
  assert_rejected "$script" '203.0.113.1' '6379'
  assert_rejected "$script" '172.15.255.254' '6379'
  assert_rejected "$script" '172.32.0.1' '6379'
  assert_rejected "$script" '192.167.255.254' '6379'
  assert_rejected "$script" '192.169.0.1' '6379'
  assert_rejected "$script" 'fe80::2%eth0' '6379'
  assert_rejected "$script" 'fe00::1' '6379'
  assert_rejected "$script" '2001:db8::1' '6379'
  assert_rejected "$script" '224.0.0.1' '6379'
  assert_rejected "$script" 'ff02::1%eth0' '6379'
  assert_rejected "$script" '127.0.0.1.nip.io' '6379'
  assert_rejected "$script" 'mapped-loopback.internal.example' '6379'
  assert_rejected "$script" 'link-local.internal.example' '6379'
  assert_rejected "$script" 'multicast.internal.example' '6379'
  assert_rejected "$script" 'unspecified.internal.example' '6379'
  assert_rejected "$script" 'dual-stack.internal.example' '6379'
  assert_rejected "$script" 'public-numeric.internal.example' '6379'
  assert_rejected "$script" '::ffff:c000:201' '6379'
  assert_rejected "$script" '::c000:201%eth0' '6379'
  assert_rejected "$script" 'redis.internal.example' ''
  assert_rejected "$script" 'redis.internal.example' '0'
  assert_rejected "$script" 'redis.internal.example' '65536'
  assert_rejected "$script" 'redis.internal.example' 'not-a-port'
  assert_accepted "$script" 'redis.internal.example' '6379'
  assert_accepted "$script" '10.0.0.1' '6379'
  assert_accepted "$script" '10.20.30.40' '6379'
  assert_accepted "$script" '172.16.0.1' '6379'
  assert_accepted "$script" '172.31.255.254' '6379'
  assert_accepted "$script" '192.168.0.1' '6379'
  assert_accepted "$script" 'fc00::1' '6379'
  assert_accepted "$script" 'fd00::10' '6379'
  assert_accepted "$script" 'fd00::10%eth0' '6379'
  assert_accepted "$script" 'private-dual-stack.internal.example' '6379'
  assert_accepted "$script" 'fallback-only.internal.example' '6379'
  assert_accepted "$script" 'redis.internal.example' '1'
  assert_accepted "$script" 'redis.internal.example' '65535'
  assert_validated_host "$script" 'redis.internal.example' '6379' '10.20.30.40'
  assert_validated_host "$script" 'private-dual-stack.internal.example' '6379' '10.20.30.40'
  assert_validated_host "$script" 'fallback-only.internal.example' '6379' '10.20.30.41'
}

assert_redis_smoke_secret_contract() {
  local script="$1"
  local expected_redis_cli_image='redis@sha256:8096655e437712b07503796fb64d81359256cfcff0ab29d95a7da72863786efb'
  if grep -Fq 'REDISCLI_AUTH' "$script" || grep -Fq 'redis_password' "$script" || \
      grep -Eq -- 'redis-cli .* (--pass|-a)( |$)' "$script"; then
    echo "Redis smoke가 비밀값을 환경 변수/셸 변수로 노출한다: $(basename "$script")" >&2
    exit 1
  fi
  grep -Fq -- '--mount "type=bind,source=$REDIS_PASSWORD_FILE,target=/run/secrets/redis-password,readonly"' "$script" || {
    echo "Redis smoke가 읽기 전용 비밀값 파일 mount를 사용하지 않는다: $(basename "$script")" >&2
    exit 1
  }
  grep -Fq -- '--user "$REDIS_PASSWORD_UID:$REDIS_PASSWORD_GID"' "$script" || {
    echo "Redis smoke가 비밀값 파일 소유자 UID:GID로 실행되지 않는다: $(basename "$script")" >&2
    exit 1
  }
  grep -Fq -- 'redis-cli --askpass' "$script" || {
    echo "Redis smoke가 stdin 기반 redis-cli 인증을 사용하지 않는다: $(basename "$script")" >&2
    exit 1
  }
  grep -Fq -- "REDIS_CLI_IMAGE='$expected_redis_cli_image'" "$script" || {
    echo "Redis smoke가 저장소 고정 digest를 사용하지 않는다: $(basename "$script")" >&2
    exit 1
  }
  if grep -Fq -- '${REDIS_CLI_IMAGE' "$script"; then
    echo "Redis smoke가 임의의 REDIS_CLI_IMAGE override를 허용한다: $(basename "$script")" >&2
    exit 1
  fi
  local validate_line password_file_line redis_cli_line
  validate_line="$(grep -nF 'validate_shared_redis_endpoint "$REDIS_HOST" "$REDIS_PORT"' "$script" | cut -d: -f1)"
  password_file_line="$(grep -nF 'REDIS_PASSWORD_FILE=' "$script" | head -n1 | cut -d: -f1)"
  redis_cli_line="$(grep -nF 'redis_cli() {' "$script" | cut -d: -f1)"
  [ -n "$validate_line" ] && [ -n "$password_file_line" ] && [ -n "$redis_cli_line" ] &&
    [ "$validate_line" -lt "$password_file_line" ] && [ "$validate_line" -lt "$redis_cli_line" ] || {
    echo "Redis smoke가 endpoint 검증 전에 비밀값/Auth 경로를 연다: $(basename "$script")" >&2
    exit 1
  }
}

app_run_contract="$(extract_contract "$APP_RUN_SCRIPT")"
app_deploy_contract="$(extract_contract "$APP_DEPLOY_SCRIPT")"
if [ "$app_run_contract" != "$app_deploy_contract" ]; then
  echo 'app-run.sh와 app-deploy.sh의 Redis endpoint 계약이 다르다' >&2
  exit 1
fi

run_contract_cases "$APP_RUN_SCRIPT"
run_contract_cases "$APP_DEPLOY_SCRIPT"
assert_redis_smoke_secret_contract "$APP_DEPLOY_SCRIPT"
echo 'Redis endpoint contract: PASS'
