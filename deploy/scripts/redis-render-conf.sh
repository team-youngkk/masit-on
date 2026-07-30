#!/usr/bin/env bash
# 운영 Redis 기동 직전에 실행한다. 저장소의 redis.conf에 Parameter Store의
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
PARAMETER_NAME="${REDIS_PASSWORD_PARAMETER:-/masiton/redis/password}"
BASE_CONF="${BASE_CONF:-/opt/masiton/redis/redis.conf}"
RUN_DIR="${RUN_DIR:-/run/masiton}"
RUN_CONF="${RUN_CONF:-$RUN_DIR/redis.conf}"
# redis:8.8-alpine의 redis 사용자 uid·gid다. 컨테이너가 비루트로 실행되므로
# 렌더링한 설정의 소유자를 이 uid로 맞춰야 읽을 수 있다. 읽기 전용으로 마운트해
# 이미지 entrypoint의 chown이 실패하기 때문에 호스트에서 미리 맞춘다.
REDIS_UID="${REDIS_UID:-999}"
REDIS_GID="${REDIS_GID:-999}"

if [ ! -f "$BASE_CONF" ]; then
  echo "기준 설정이 없다: $BASE_CONF" >&2
  exit 1
fi

install -d -m 0700 "$RUN_DIR"

password=$(aws ssm get-parameter \
  --region "$REGION" \
  --name "$PARAMETER_NAME" \
  --with-decryption \
  --query 'Parameter.Value' --output text)

if [ -z "$password" ] || [ "$password" = "None" ]; then
  echo "Parameter Store에서 $PARAMETER_NAME 를 읽지 못했다" >&2
  exit 1
fi

# 값에 섞인 CR·LF를 걷어낸다. Windows 셸에서 생성한 값이 `\r`을 물고 등록되면
# Redis 설정 파서가 줄 끝의 `\r`을 떼어내는 반면 클라이언트는 그대로 보내
# WRONGPASS가 된다. 실제로 M2-05에서 이 형태로 한 번 어긋났다.
password=$(printf %s "$password" | tr -d '\r\n')

# 공백이 있으면 설정 한 줄로 표현할 수 없다. 조용히 잘리지 않게 여기서 막는다.
case "$password" in
  *[[:space:]]*)
    echo "$PARAMETER_NAME 값에 공백이 있어 requirepass 한 줄로 쓸 수 없다" >&2
    exit 1
    ;;
esac

umask 077
{
  cat "$BASE_CONF"
  printf 'requirepass %s\n' "$password"
} > "$RUN_CONF"

unset password

# uid 999만 읽을 수 있게 둔다. root는 소유권과 무관하게 읽으므로 운영자 접근은 남는다.
chown "$REDIS_UID:$REDIS_GID" "$RUN_CONF"
chmod 0400 "$RUN_CONF"

echo "렌더링 완료: $RUN_CONF ($(stat -c '%U:%G %a' "$RUN_CONF"))"
