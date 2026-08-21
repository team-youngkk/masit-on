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
  assert_rejected "$script" 'fe80::2%eth0' '6379'
  assert_rejected "$script" '224.0.0.1' '6379'
  assert_rejected "$script" 'ff02::1%eth0' '6379'
  assert_rejected "$script" '127.0.0.1.nip.io' '6379'
  assert_rejected "$script" 'mapped-loopback.internal.example' '6379'
  assert_rejected "$script" 'link-local.internal.example' '6379'
  assert_rejected "$script" 'multicast.internal.example' '6379'
  assert_rejected "$script" 'unspecified.internal.example' '6379'
  assert_rejected "$script" 'dual-stack.internal.example' '6379'
  assert_rejected "$script" 'redis.internal.example' ''
  assert_rejected "$script" 'redis.internal.example' '0'
  assert_rejected "$script" 'redis.internal.example' '65536'
  assert_rejected "$script" 'redis.internal.example' 'not-a-port'
  assert_accepted "$script" 'redis.internal.example' '6379'
  assert_accepted "$script" '10.20.30.40' '6379'
  assert_accepted "$script" 'fd00::10' '6379'
  assert_accepted "$script" 'fd00::10%eth0' '6379'
  assert_accepted "$script" '::ffff:c000:201' '6379'
  assert_accepted "$script" '::c000:201%eth0' '6379'
  assert_accepted "$script" 'private-dual-stack.internal.example' '6379'
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
