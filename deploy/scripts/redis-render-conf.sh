#!/usr/bin/env bash
# 운영 Redis 기동 직전에 실행한다. 저장소의 redis.conf에 S3 객체의
# requirepass를 붙여 tmpfs(/run)에 렌더링한다.
#
# /run을 쓰는 이유는 두 가지다.
#   - tmpfs라 재기동 시 사라진다. 자격 증명이 디스크나 볼륨 스냅샷에 남지 않는다
#     (NFR-SECURITY-003, M2-07 완료 조건).
#   - 그래서 매 기동마다 다시 렌더링해야 하고, 그 실행을 systemd unit의
#     ExecStartPre가 보장한다. 컨테이너의 --restart 정책에 맡기면 재기동 후
#     마운트 대상이 없는 상태로 되살아난다.
#
# 값을 표준 출력이나 프로세스 인자에 노출하지 않는다. 명령행에 붙이면
# 같은 인스턴스의 `ps`에서 읽힌다.
set -euo pipefail

REGION="${AWS_REGION:-ap-northeast-2}"
BASE_CONF="${BASE_CONF:-/opt/masiton/redis/redis.conf}"
RUN_DIR="${RUN_DIR:-/run/masiton}"
RUN_CONF="${RUN_CONF:-$RUN_DIR/redis.conf}"
# redis:8.8-alpine의 redis 사용자 uid·gid다. 컨테이너가 비루트로 실행되므로
# 렌더링한 설정의 소유자를 이 uid로 맞춰야 읽을 수 있다. 읽기 전용으로 마운트해
# 이미지 entrypoint의 chown이 실패하기 때문에 호스트에서 미리 맞춘다.
REDIS_UID="${REDIS_UID:-999}"
REDIS_GID="${REDIS_GID:-999}"

# /run/masiton은 0711로 통일한다. 여러 스크립트가 같은 디렉터리를 만드는데
# 권한이 엇갈리면 마지막에 만든 쪽이 이긴다. 실제로 0700으로 만들어져 Nginx
# worker가 htpasswd에 도달하지 못해 인증 통과 요청이 500이 된 적이 있다.
# 0711은 탐색만 허용하고 목록 열거를 막으며, 파일 내용은 각 파일의 0400이 지킨다.
install -d -m 0711 "$RUN_DIR"

umask 077
password_file=''
password=''
cleanup() {
  if [ -n "$password_file" ]; then
    rm -f -- "$password_file"
  fi
  unset password
  unset password_file
}
trap cleanup EXIT

# 이전 렌더링이 남아 있으면 새 비밀값을 읽지 못한 경우에도 소비자가 낡은
# 설정을 집어들 수 있다. 항상 먼저 제거하고, 새 렌더링이 성공한 경우에만 만든다.
rm -f -- "$RUN_CONF"

if [ ! -f "$BASE_CONF" ]; then
  echo "기준 설정이 없다: $BASE_CONF" >&2
  exit 1
fi

if [ -z "${REDIS_PASSWORD_BUCKET:-}" ] || [ -z "${REDIS_PASSWORD_OBJECT_KEY:-}" ]; then
  echo "Redis 비밀번호 S3 위치가 설정되지 않았다" >&2
  exit 1
fi

# S3 CLI는 객체 본문을 파일에 직접 쓰고 메타데이터 출력은 버린다. 비밀값은
# 명령행 인자나 표준 출력에 넣지 않는다.
password_file=$(mktemp "$RUN_DIR/.redis-password.XXXXXX")
chmod 0400 "$password_file"
if ! aws s3api get-object \
  --region "$REGION" \
  --bucket "$REDIS_PASSWORD_BUCKET" \
  --key "$REDIS_PASSWORD_OBJECT_KEY" \
  "$password_file" >/dev/null; then
  echo "S3에서 Redis 비밀번호 객체를 읽지 못했다" >&2
  exit 1
fi

password=$(tr -d '\r\n' < "$password_file")

if [ -z "$password" ]; then
  echo "S3의 Redis 비밀번호 객체가 비어 있다" >&2
  exit 1
fi

# Windows 셸에서 생성한 객체가 CR·LF를 물고 있으면 Redis 설정 파서와 클라이언트의
# 값이 어긋날 수 있으므로 객체를 읽을 때 제거한다. 실제로 M2-05에서 한 번 어긋났다.

# 공백이 있으면 설정 한 줄로 표현할 수 없다. 조용히 잘리지 않게 여기서 막는다.
case "$password" in
  *[[:space:]]*)
    echo "S3의 Redis 비밀번호 객체에 공백이 있어 requirepass 한 줄로 쓸 수 없다" >&2
    exit 1
    ;;
esac

{
  cat "$BASE_CONF"
  printf 'requirepass %s\n' "$password"
} > "$RUN_CONF"

unset password

# uid 999만 읽을 수 있게 둔다. root는 소유권과 무관하게 읽으므로 운영자 접근은 남는다.
chown "$REDIS_UID:$REDIS_GID" "$RUN_CONF"
chmod 0400 "$RUN_CONF"

echo "렌더링 완료: $RUN_CONF ($(stat -c '%U:%G %a' "$RUN_CONF"))"
