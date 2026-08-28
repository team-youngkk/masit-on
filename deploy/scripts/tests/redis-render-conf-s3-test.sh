#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
SCRIPT="$REPOSITORY_ROOT/deploy/scripts/redis-render-conf.sh"
TEST_ROOT="$(mktemp -d)"
FAKE_BIN="$TEST_ROOT/bin"
mkdir -p "$FAKE_BIN"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

cat > "$FAKE_BIN/aws" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

args=("$@")
printf '%s\n' "${args[@]}" > "$AWS_CALL_LOG"

if [ "${args[0]-}" != 's3api' ] || [ "${args[1]-}" != 'get-object' ]; then
  exit 91
fi

last_index=$((${#args[@]} - 1))
output_file="${args[$last_index]}"
printf '%s\n' "$(stat -c '%a' "$output_file")" > "$AWS_OUTPUT_MODE_LOG"

case "$AWS_FIXTURE_MODE" in
  success)
    printf '%s\r\n' 'fixture-only-value' > "$output_file"
    ;;
  empty)
    : > "$output_file"
    ;;
  whitespace)
    printf '%s\r\n' 'fixture only value' > "$output_file"
    ;;
  fail)
    exit 42
    ;;
  *)
    exit 43
    ;;
esac
EOF
chmod 0755 "$FAKE_BIN/aws"

TEST_UID="$(id -u)"
TEST_GID="$(id -g)"

fail() {
  printf 'Redis S3 render fixture failed: %s\n' "$1" >&2
  exit 1
}

write_base_conf() {
  local case_root="$1"
  printf 'protected-mode yes\n' > "$case_root/base.conf"
}

run_render() {
  local case_root="$1" mode="$2"
  mkdir -p "$case_root/run"
  write_base_conf "$case_root"
  : > "$case_root/aws-call.log"
  env \
    "PATH=$FAKE_BIN:$PATH" \
    "AWS_CALL_LOG=$case_root/aws-call.log" \
    "AWS_OUTPUT_MODE_LOG=$case_root/aws-output-mode.log" \
    "AWS_FIXTURE_MODE=$mode" \
    AWS_REGION=fixture-region \
    REDIS_PASSWORD_BUCKET=fixture-bucket \
    REDIS_PASSWORD_OBJECT_KEY=fixture/key \
    "BASE_CONF=$case_root/base.conf" \
    "RUN_DIR=$case_root/run" \
    "RUN_CONF=$case_root/run/redis.conf" \
    "REDIS_UID=$TEST_UID" \
    "REDIS_GID=$TEST_GID" \
    bash "$SCRIPT" > "$case_root/script-output.log" 2>&1
}

assert_no_password_in_logs() {
  local case_root="$1"
  if grep -Fq 'fixture-only-value' "$case_root/script-output.log" "$case_root/aws-call.log"; then
    fail 'fixture value was logged'
  fi
}

assert_no_password_temp() {
  local run_dir="$1"
  [ -z "$(find "$run_dir" -mindepth 1 -maxdepth 1 -name '.redis-password.*' -print -quit)" ] ||
    fail 'temporary password file was not removed'
}

assert_success() {
  local case_root="$TEST_ROOT/success"
  run_render "$case_root" success || fail 'successful S3 render returned failure'

  local -a call_args
  mapfile -t call_args < "$case_root/aws-call.log"
  [ "${#call_args[@]}" -eq 9 ] || fail 'unexpected aws argument count'
  [ "${call_args[0]}" = 's3api' ] || fail 'aws service was not s3api'
  [ "${call_args[1]}" = 'get-object' ] || fail 'aws operation was not get-object'
  [ "${call_args[2]}" = '--region' ] && [ "${call_args[3]}" = 'fixture-region' ] ||
    fail 'S3 region arguments were not passed as expected'
  [ "${call_args[4]}" = '--bucket' ] && [ "${call_args[5]}" = 'fixture-bucket' ] ||
    fail 'S3 bucket arguments were not passed as expected'
  [ "${call_args[6]}" = '--key' ] && [ "${call_args[7]}" = 'fixture/key' ] ||
    fail 'S3 object key arguments were not passed as expected'
  case "${call_args[8]}" in
    "$case_root/run/.redis-password."*) ;;
    *) fail 'S3 output was not written below the run fixture' ;;
  esac
  [ "$(cat "$case_root/aws-output-mode.log")" = '400' ] ||
    fail 'temporary password file was not 0400 during download'

  local rendered_conf="$case_root/run/redis.conf"
  [ -f "$rendered_conf" ] || fail 'rendered Redis configuration is missing'
  grep -Fqx 'requirepass fixture-only-value' "$rendered_conf" ||
    fail 'CR/LF-normalized requirepass was not rendered'
  if grep -Fq $'\r' "$rendered_conf"; then
    fail 'rendered Redis configuration still contains CR'
  fi
  [ "$(stat -c '%a' "$rendered_conf")" = '400' ] || fail 'rendered config is not 0400'
  [ "$(stat -c '%u:%g' "$rendered_conf")" = "$TEST_UID:$TEST_GID" ] ||
    fail 'rendered config does not have Redis UID/GID'
  [ "$(stat -c '%a' "$case_root/run")" = '711' ] || fail 'run fixture is not 0711'
  assert_no_password_temp "$case_root/run"
  assert_no_password_in_logs "$case_root"
}

assert_fail_closed() {
  local case_root="$TEST_ROOT/$1" mode="$2"
  mkdir -p "$case_root/run"
  write_base_conf "$case_root"
  printf 'protected-mode yes\nrequirepass stale-value\n' > "$case_root/run/redis.conf"

  if run_render "$case_root" "$mode"; then
    fail "$mode fixture unexpectedly succeeded"
  fi
  [ ! -e "$case_root/run/redis.conf" ] || fail "$mode fixture left a usable config"
  assert_no_password_temp "$case_root/run"
}

assert_missing_location_fails_closed() {
  local case_root="$TEST_ROOT/$1" unset_name="$2"
  mkdir -p "$case_root/run"
  write_base_conf "$case_root"
  printf 'protected-mode yes\nrequirepass stale-value\n' > "$case_root/run/redis.conf"
  : > "$case_root/aws-call.log"

  local -a render_env=(
    "PATH=$FAKE_BIN:$PATH"
    "AWS_CALL_LOG=$case_root/aws-call.log"
    "AWS_OUTPUT_MODE_LOG=$case_root/aws-output-mode.log"
    AWS_FIXTURE_MODE=success
    AWS_REGION=fixture-region
    "BASE_CONF=$case_root/base.conf"
    "RUN_DIR=$case_root/run"
    "RUN_CONF=$case_root/run/redis.conf"
    "REDIS_UID=$TEST_UID"
    "REDIS_GID=$TEST_GID"
  )
  if [ "$unset_name" != 'REDIS_PASSWORD_BUCKET' ]; then
    render_env+=(REDIS_PASSWORD_BUCKET=fixture-bucket)
  fi
  if [ "$unset_name" != 'REDIS_PASSWORD_OBJECT_KEY' ]; then
    render_env+=(REDIS_PASSWORD_OBJECT_KEY=fixture/key)
  fi

  if env -u "$unset_name" "${render_env[@]}" bash "$SCRIPT" > "$case_root/script-output.log" 2>&1; then
    fail "unset $unset_name unexpectedly succeeded"
  fi
  [ ! -e "$case_root/run/redis.conf" ] || fail "unset $unset_name left a usable config"
  [ ! -s "$case_root/aws-call.log" ] || fail "unset $unset_name still called AWS"
}

if grep -Fq 'ssm' "$SCRIPT"; then
  fail 'redis render script still contains an SSM path'
fi

assert_success
assert_fail_closed download-failure fail
assert_fail_closed empty-object empty
assert_fail_closed whitespace-object whitespace
assert_missing_location_fails_closed missing-bucket REDIS_PASSWORD_BUCKET
assert_missing_location_fails_closed missing-object-key REDIS_PASSWORD_OBJECT_KEY

echo 'Redis S3 render contract: PASS'
