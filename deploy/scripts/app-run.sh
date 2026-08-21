#!/usr/bin/env bash
# 운영 애플리케이션 컨테이너를 포그라운드로 실행한다 — M2-09.
# systemd unit의 ExecStart가 이 스크립트를 호출한다.
#
# 사용: app-run.sh backend|frontend
#
# 비밀값은 환경 변수로 넘기지 않는다. `-e VAR` 통과 형식은 명령행 노출은 막지만
# 값이 컨테이너 스펙에 들어가고 Docker가 그것을
# `/var/lib/docker/containers/<id>/config.v2.json`에 평문으로 적는다. `docker inspect`로
# 읽히고 루트 볼륨 스냅샷에도 들어가 ADR-SEC-001 11절의 평문 저장 금지에 걸린다.
#
# 대신 app-secrets-render.sh가 tmpfs에 만든 파일을 읽기 전용으로 마운트하고
# 애플리케이션이 `configtree:`로 읽는다(application-prod.yml). 비밀이 아닌 접속값만
# 환경 변수로 남긴다.
#
# 네트워크는 host를 쓴다. ADR-RUNTIME-001 11절이 운영 설정의 Docker 서비스명을
# 금지하므로 앱은 저장소에 127.0.0.1로 붙어야 하고, Nginx도 127.0.0.1의 8080·3000으로
# 전달한다(M2-08). 브리지 네트워크로는 두 방향 모두 성립하지 않는다.
set -euo pipefail

# BEGIN SHARED REDIS ENDPOINT CONTRACT
is_canonical_ipv4() {
  local candidate="$1"
  local -a octets=()
  [[ "$candidate" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || return 1
  IFS=. read -r -a octets <<< "$candidate"
  [ "${#octets[@]}" -eq 4 ] || return 1
  for octet in "${octets[@]}"; do
    [[ "$octet" == 0 || "$octet" =~ ^[1-9][0-9]{0,2}$ ]] || return 1
    ((10#$octet <= 255)) || return 1
  done
}

is_safe_shared_ipv4() {
  local candidate="$1"
  local -a octets=()
  is_canonical_ipv4 "$candidate" || return 1
  IFS=. read -r -a octets <<< "$candidate"
  local first="${octets[0]}"
  local second="${octets[1]}"
  if (( first == 10 )); then return 0; fi
  if (( first == 172 && second >= 16 && second <= 31 )); then return 0; fi
  if (( first == 192 && second == 168 )); then return 0; fi
  return 1
}

resolve_shared_redis_host() {
  local host="$1" lookup="" address="" resolved=""
  local -a labels=()
  if is_canonical_ipv4 "$host"; then
    is_safe_shared_ipv4 "$host" || return 1
    printf '%s' "$host"
    return 0
  fi
  [[ "$host" =~ ^[0-9.]+$ || "$host" =~ ^[0xX][0-9A-Fa-f]+$ ]] && return 1
  [[ "$host" != *:* ]] || return 1
  [[ "$host" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] || return 1
  [ "${#host}" -le 253 ] || return 1
  [[ "$host" != *..* ]] || return 1
  IFS=. read -r -a labels <<< "$host"
  for label in "${labels[@]}"; do [ "${#label}" -le 63 ] || return 1; done
  lookup=$(getent ahostsv4 "$host" 2>/dev/null) || return 1
  [ -n "$lookup" ] || return 1
  while IFS= read -r address; do
    [ -n "$address" ] || continue
    is_safe_shared_ipv4 "$address" || return 1
    [ -n "$resolved" ] || resolved="$address"
  done < <(printf '%s\n' "$lookup" | awk '{print $1}')
  [ -n "$resolved" ] || return 1
  printf '%s' "$resolved"
}

validate_shared_redis_port() {
  local port="${1-}"
  [[ "$port" =~ ^[1-9][0-9]{0,4}$ ]] || return 1
  ((10#$port <= 65535))
}

validate_shared_redis_endpoint() {
  local host="${1-}" port="${2-}"
  REDIS_VALIDATED_HOST=""; REDIS_VALIDATED_PORT=""
  REDIS_VALIDATED_HOST=$(resolve_shared_redis_host "$host") || {
    echo "공유 Redis host가 안전한 private IPv4 주소로 고정되지 않는다" >&2
    return 1
  }
  validate_shared_redis_port "$port" || {
    echo "공유 Redis port가 유효한 숫자 범위가 아니다" >&2
    return 1
  }
  REDIS_VALIDATED_PORT="$port"
}
# END SHARED REDIS ENDPOINT CONTRACT

DEPLOYMENT_ENV_FILE="${DEPLOYMENT_ENV_FILE:-/etc/masiton/deployment.env}"
if [ -f "$DEPLOYMENT_ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$DEPLOYMENT_ENV_FILE"
  set +a
fi

component="${1:?backend 또는 frontend를 지정한다}"
REGION="${AWS_REGION:-ap-northeast-2}"
IMAGE_REF_FILE="/opt/masiton/etc/${component}.image"
# 컨테이너 안과 밖의 경로를 같게 둔다. application-prod.yml이 이 값을 그대로 읽는다.
SECRETS_DIR="${SECRETS_DIR:-/run/masiton/secrets}"
export SECRETS_DIR

[ -f "$IMAGE_REF_FILE" ] || { echo "배포된 이미지 참조가 없다: $IMAGE_REF_FILE" >&2; exit 1; }
image=$(tr -d ' \r\n' < "$IMAGE_REF_FILE")
[ -n "$image" ] || { echo "이미지 참조가 비어 있다: $IMAGE_REF_FILE" >&2; exit 1; }

# Parameter Store 값을 셸 환경으로만 읽어들인다.
param() {
  aws ssm get-parameter --region "$REGION" --name "$1" --with-decryption \
    --query 'Parameter.Value' --output text
}
optional_param() {
  aws ssm get-parameter --region "$REGION" --name "$1" --with-decryption \
    --query 'Parameter.Value' --output text 2>/dev/null || printf ''
}
optional_bool_param() {
  case "$(optional_param "$1")" in
    true|TRUE|True|1) printf 'true' ;;
    *) printf 'false' ;;
  esac
}

case "$component" in
  backend)
    # 비밀이 아닌 값만 환경 변수로 넘긴다. 접속 주소와 사용자명은 비밀이 아니며
    # 기록 문서에도 그대로 적혀 있다.
    export SPRING_PROFILES_ACTIVE=prod
    DB_URL=$(param /masiton/db/url); export DB_URL
    DB_USERNAME=$(param /masiton/db/username); export DB_USERNAME
    KAKAO_MOBILITY_ENABLED=$(optional_bool_param /masiton/integration/kakao-mobility/enabled); export KAKAO_MOBILITY_ENABLED
    KAKAO_MOBILITY_FREE_TIER_VERIFIED=$(optional_bool_param /masiton/integration/kakao-mobility/free-tier-verified); export KAKAO_MOBILITY_FREE_TIER_VERIFIED
    # 단일 EC2 기본값은 유지하고, ASG에서는 환경 변수 또는 선택적 SSM 값으로
    # Redis endpoint를 바꿀 수 있게 한다. 명시적 환경 변수가 SSM보다 우선한다.
    REDIS_HOST="${REDIS_HOST:-$(optional_param /masiton/redis/host)}"
    REDIS_PORT="${REDIS_PORT:-$(optional_param /masiton/redis/port)}"
    if [ "${REQUIRE_SHARED_REDIS:-false}" = true ]; then
      [ -n "$REDIS_HOST" ] || {
        echo "ASG 배포에서는 공유 Redis endpoint가 필요하다: REDIS_HOST 또는 /masiton/redis/host" >&2
        exit 1
      }
      validate_shared_redis_endpoint "$REDIS_HOST" "$REDIS_PORT" || exit 1
      REDIS_HOST="$REDIS_VALIDATED_HOST"
      REDIS_PORT="$REDIS_VALIDATED_PORT"
    else
      REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
      REDIS_PORT="${REDIS_PORT:-6379}"
      validate_shared_redis_port "$REDIS_PORT" || {
        echo "Redis port가 유효하지 않다" >&2
        exit 1
      }
    fi
    export REDIS_HOST
    export REDIS_PORT
    MAIL_HOST=$(param /masiton/mail/host); export MAIL_HOST
    MAIL_PORT=$(param /masiton/mail/port); export MAIL_PORT
    export MAIL_HEALTH_ENABLED=true
    export DEPENDENCY_HEALTH_COMPONENTS=db,redis,mail
    export AUTH_ALLOWED_ORIGINS=https://masiton.click
    export VERIFICATION_PUBLIC_BASE_URL=https://masiton.click
    export VERIFICATION_TRUSTED_PROXY_ADDRESSES=127.0.0.1
    export VERIFICATION_REVERSE_PROXY_ENABLED=true
    export MEMBER_TRUSTED_PROXY_ADDRESSES=127.0.0.1
    export MEMBER_REVERSE_PROXY_ENABLED=true
    export AUTH_LOGIN_TRUSTED_PROXY_ADDRESSES=127.0.0.1
    export AUTH_LOGIN_REVERSE_PROXY_ENABLED=true
    export RESTAURANT_MAP_TRUSTED_PROXY_ADDRESSES=127.0.0.1
    export RESTAURANT_MAP_REVERSE_PROXY_ENABLED=true
    # 운영 프로파일은 이 값에 기본값을 두지 않는다. PubSubHubbub 허브가 구독을 검증할 때
    # 실제로 도달할 수 있는 주소여야 하고, localhost 기본값이 조용히 쓰이면 구독이
    # 성립한 것처럼 보이면서 알림이 오지 않는다. 비밀이 아니므로 환경 변수로 넘긴다.
    export YOUTUBE_WEBHOOK_CALLBACK_URL=https://masiton.click/api/webhooks/youtube/channel-updates
    AI_WORKER_ENABLED=$(optional_param /masiton/ai/worker/enabled); export AI_WORKER_ENABLED="${AI_WORKER_ENABLED:-false}"
    AI_WORKER_PROVIDER_QUOTA_LIMIT=$(optional_param /masiton/ai/worker/provider-quota-limit); export AI_WORKER_PROVIDER_QUOTA_LIMIT="${AI_WORKER_PROVIDER_QUOTA_LIMIT:-0}"
    AI_WORKER_APPLICATION_QUOTA_LIMIT=$(optional_param /masiton/ai/worker/application-quota-limit); export AI_WORKER_APPLICATION_QUOTA_LIMIT="${AI_WORKER_APPLICATION_QUOTA_LIMIT:-0}"
    AI_WORKER_QUOTA_WINDOW=$(optional_param /masiton/ai/worker/quota-window); export AI_WORKER_QUOTA_WINDOW="${AI_WORKER_QUOTA_WINDOW:-P1D}"
    GEMINI_ENABLED=$(optional_bool_param /masiton/ai/gemini/enabled); export GEMINI_ENABLED
    GEMINI_FREE_TIER_VERIFIED=$(optional_bool_param /masiton/ai/gemini/free-tier-verified); export GEMINI_FREE_TIER_VERIFIED
    GEMINI_PAID_BILLING_ENABLED=$(optional_bool_param /masiton/ai/gemini/paid-billing-enabled); export GEMINI_PAID_BILLING_ENABLED

    [ -d "$SECRETS_DIR" ] || { echo "비밀값 디렉터리가 없다: $SECRETS_DIR" >&2; exit 1; }

    exec /usr/bin/docker run --name masiton-backend \
      --network host \
      --memory 1024m \
      --log-driver json-file --log-opt max-size=10m --log-opt max-file=3 \
      --volume "$SECRETS_DIR":"$SECRETS_DIR":ro \
      -e SPRING_PROFILES_ACTIVE \
      -e SPRING_FLYWAY_TARGET \
      -e DB_URL -e DB_USERNAME \
      -e KAKAO_MOBILITY_ENABLED -e KAKAO_MOBILITY_FREE_TIER_VERIFIED \
      -e REDIS_HOST -e REDIS_PORT \
      -e MAIL_HOST -e MAIL_PORT -e MAIL_HEALTH_ENABLED -e DEPENDENCY_HEALTH_COMPONENTS \
      -e AUTH_ALLOWED_ORIGINS -e VERIFICATION_PUBLIC_BASE_URL \
      -e VERIFICATION_TRUSTED_PROXY_ADDRESSES -e VERIFICATION_REVERSE_PROXY_ENABLED \
      -e MEMBER_TRUSTED_PROXY_ADDRESSES -e MEMBER_REVERSE_PROXY_ENABLED \
      -e AUTH_LOGIN_TRUSTED_PROXY_ADDRESSES -e AUTH_LOGIN_REVERSE_PROXY_ENABLED \
      -e RESTAURANT_MAP_TRUSTED_PROXY_ADDRESSES -e RESTAURANT_MAP_REVERSE_PROXY_ENABLED \
      -e AI_WORKER_ENABLED -e AI_WORKER_PROVIDER_QUOTA_LIMIT \
      -e AI_WORKER_APPLICATION_QUOTA_LIMIT -e AI_WORKER_QUOTA_WINDOW \
      -e GEMINI_ENABLED -e GEMINI_FREE_TIER_VERIFIED -e GEMINI_PAID_BILLING_ENABLED \
      -e YOUTUBE_WEBHOOK_CALLBACK_URL \
      -e SECRETS_DIR \
      "$image"
    ;;
  frontend)
    # 프론트엔드는 같은 인스턴스의 백엔드로 /api를 전달한다. 비밀값이 없다.
    export API_BASE_URL=http://127.0.0.1:8080
    export PORT=3000
    export HOSTNAME=127.0.0.1

    exec /usr/bin/docker run --name masiton-frontend \
      --network host \
      --memory 512m \
      --log-driver json-file --log-opt max-size=10m --log-opt max-file=3 \
      -e API_BASE_URL -e PORT -e HOSTNAME \
      "$image"
    ;;
  *)
    echo "알 수 없는 구성 요소: $component" >&2
    exit 1
    ;;
esac
