#!/usr/bin/env bash
# 상태 확인 결과를 CloudWatch 지표로 올린다 — M2-10.
#
# `/internal/**`은 인터넷에서 차단되므로(ADR-WEB-003) 외부 감시 서비스로는 볼 수
# 없다. 그래서 인스턴스 안에서 1분 주기로 호출해 지표로 올리고, 알람이 그 지표의
# 연속 실패를 본다.
#
# 지표는 정상 1 / 실패 0이다. 알람은 `1 미만이 연속 3회`로 걸어 "상태 확인 연속
# 3회 실패"와 "저장소 연결 실패 연속 3회"를 각각 표현한다.
set -uo pipefail

DEPLOYMENT_ENV_FILE="${DEPLOYMENT_ENV_FILE:-/etc/masiton/deployment.env}"
if [ -f "$DEPLOYMENT_ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$DEPLOYMENT_ENV_FILE"
  set +a
fi

REGION="${AWS_REGION:-ap-northeast-2}"
NAMESPACE="${METRIC_NAMESPACE:-masiton/health}"
# fleet 집계 지표의 범위를 가르는 이름이다. CodeDeploy alarm은 asg만 본다.
# 기존 단일 EC2에 이 스크립트를 설치할 때는 다른 값을 넘겨야 두 환경이 섞이지 않는다.
ENVIRONMENT="${METRIC_ENVIRONMENT:-asg}"
BASE="${HEALTH_BASE:-http://127.0.0.1:8080}"
instance_id=$(curl -sS -m 3 -H "X-aws-ec2-metadata-token: $(curl -sS -m 3 -X PUT \
  -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' http://169.254.169.254/latest/api/token)" \
  http://169.254.169.254/latest/meta-data/instance-id)

# IMDSv2가 강제돼 있어 토큰을 먼저 받아야 한다. 실패하면 지표에 차원을 붙일 수
# 없으므로 중단한다. 값이 없는 채로 올리면 알람이 다른 차원을 보게 된다.
if [ -z "$instance_id" ]; then
  echo "instance-id를 읽지 못했다" >&2
  exit 1
fi

# 정상 1 / 실패 0. jq가 없으므로 python3으로 판정한다.
probe() {
  local path="$1" component="$2"
  local body status
  body=$(curl -sS -m 5 "$BASE/internal/health/$path" 2>/dev/null)
  status=$?
  if [ "$status" -ne 0 ] || [ -z "$body" ]; then
    echo 0
    return
  fi
  python3 -c "
import json, sys
try:
    d = json.loads(sys.argv[1])
except Exception:
    print(0); sys.exit()
component = sys.argv[2]
if component:
    value = d.get('components', {}).get(component, {}).get('status')
else:
    value = d.get('status')
print(1 if value == 'UP' else 0)
" "$body" "$component"
}

live=$(probe live "")
ready=$(probe ready "")
db=$(probe dependencies db)
redis=$(probe dependencies redis)

# Redis는 앱 컨테이너와 같은 host network를 사용하므로, health-metrics도 같은
# endpoint를 직접 조회한다. 비밀값은 명령행이 아니라 app-secrets-render.sh가
# tmpfs에 만든 파일에서 읽는다. 공유 모드가 아니면 SSM의 공유 endpoint를 사용하지
# 않고 기존 단일 EC2 동거 Redis인 127.0.0.1로 고정한다. 공유 모드에서 SSM 조회가
# 실패하면 빈 값으로 남겨 capacity 지표를 결측 처리한다.
if [ "${REQUIRE_SHARED_REDIS:-false}" = true ]; then
  REDIS_HOST="${REDIS_HOST:-$(aws ssm get-parameter --region "$REGION" --name /masiton/redis/host \
    --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || printf '')}"
  REDIS_PORT="${REDIS_PORT:-$(aws ssm get-parameter --region "$REGION" --name /masiton/redis/port \
    --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || printf '')}"
else
  REDIS_HOST=127.0.0.1
  REDIS_PORT="${REDIS_PORT:-6379}"
fi

# BEGIN SHARED REDIS ENDPOINT CONTRACT
# health-metrics는 host network에서 Redis에 직접 연결하므로, 입력 endpoint를
# Redis client가 읽기 전에 엄격히 고정한다. 숫자 IPv4는 표준 dotted-decimal만
# 허용해 octal·shorthand·IPv4-mapped IPv6 해석 차이를 제거하고, DNS는 모든 A
# 결과를 검사한 뒤 선택한 숫자 주소를 사용해 validation 이후 rebinding을 막는다.
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

  # Shared Redis is reachable only through a private IPv4 address. This also
  # rejects unspecified, loopback, link-local, multicast and broadcast ranges.
  if (( first == 10 )); then
    return 0
  fi
  if (( first == 172 && second >= 16 && second <= 31 )); then
    return 0
  fi
  if (( first == 192 && second == 168 )); then
    return 0
  fi
  return 1
}

resolve_shared_redis_host() {
  local host="$1"
  local lookup=""
  local address=""
  local resolved=""
  local -a labels=()

  if is_canonical_ipv4 "$host"; then
    is_safe_shared_ipv4 "$host" || return 1
    printf '%s' "$host"
    return 0
  fi

  # Numeric-looking non-canonical forms must not fall through to DNS, where a
  # resolver or client could interpret them as legacy octal/decimal addresses.
  [[ "$host" =~ ^[0-9.]+$ || "$host" =~ ^[0xX][0-9A-Fa-f]+$ ]] && return 1

  # A colon is never valid in the hostname form. In particular, this rejects
  # IPv6 and IPv4-mapped IPv6 before any resolver or client can reinterpret it.
  [[ "$host" != *:* ]] || return 1
  [[ "$host" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] || return 1
  [ "${#host}" -le 253 ] || return 1
  [[ "$host" != *..* ]] || return 1
  IFS=. read -r -a labels <<< "$host"
  for label in "${labels[@]}"; do
    [ "${#label}" -le 63 ] || return 1
  done

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

validate_local_redis_endpoint() {
  local port="${1-}"
  REDIS_VALIDATED_HOST=127.0.0.1
  REDIS_VALIDATED_PORT=""
  validate_shared_redis_port "$port" || {
    echo "로컬 Redis port가 유효한 숫자 범위가 아니다" >&2
    return 1
  }
  REDIS_VALIDATED_PORT="$port"
}
# END SHARED REDIS ENDPOINT CONTRACT

REDIS_ENDPOINT_HOST=""
REDIS_ENDPOINT_PORT=""
REDIS_ENDPOINT_VALID=false
if [ "${REQUIRE_SHARED_REDIS:-false}" = true ]; then
  validate_shared_redis_endpoint "$REDIS_HOST" "$REDIS_PORT"
else
  validate_local_redis_endpoint "$REDIS_PORT"
fi
if [ "$?" -eq 0 ]; then
  REDIS_ENDPOINT_HOST="$REDIS_VALIDATED_HOST"
  REDIS_ENDPOINT_PORT="$REDIS_VALIDATED_PORT"
  REDIS_ENDPOINT_VALID=true
fi
if [ "$REDIS_ENDPOINT_VALID" != true ]; then
  if [ "${REQUIRE_SHARED_REDIS:-false}" = true ]; then
    echo "Redis shared endpoint가 안전한 private IPv4/port 계약을 만족하지 않는다" >&2
  else
    echo "Redis local endpoint가 127.0.0.1/유효한 port 계약을 만족하지 않는다" >&2
  fi
fi

REDIS_PASSWORD_FILE="${REDIS_PASSWORD_FILE:-/run/masiton/secrets/spring.data.redis.password}"
# Keep the fallback client aligned with deploy/redis/masiton-redis.service. Do not
# allow deployment environment input to select an arbitrary executable image.
REDIS_CLI_IMAGE='redis@sha256:8096655e437712b07503796fb64d81359256cfcff0ab29d95a7da72863786efb'

redis_cli() {
  # Endpoint validation must complete before this function can read the
  # password file or discover/invoke either Redis client path.
  [ "$REDIS_ENDPOINT_VALID" = true ] || return 1
  if command -v redis-cli >/dev/null 2>&1; then
    [ -r "$REDIS_PASSWORD_FILE" ] || return 1
    redis-cli --askpass -h "$REDIS_ENDPOINT_HOST" -p "$REDIS_ENDPOINT_PORT" --raw "$@" < "$REDIS_PASSWORD_FILE"
  elif command -v docker >/dev/null 2>&1; then
    local redis_password_owner=""
    [ -r "$REDIS_PASSWORD_FILE" ] || return 1
    redis_password_owner=$(stat -c '%u:%g' "$REDIS_PASSWORD_FILE" 2>/dev/null) || return 1
    [[ "$redis_password_owner" =~ ^[0-9]+:[0-9]+$ ]] || return 1
    docker run --rm --network host \
      --user "$redis_password_owner" \
      --mount "type=bind,src=$REDIS_PASSWORD_FILE,dst=/run/masiton-redis-password,readonly" \
      "$REDIS_CLI_IMAGE" sh -c \
      'host=$1; port=$2; shift 2; exec redis-cli --askpass -h "$host" -p "$port" --raw "$@" < /run/masiton-redis-password' \
      sh "$REDIS_ENDPOINT_HOST" "$REDIS_ENDPOINT_PORT" "$@"
  else
    return 1
  fi
}

redis_used_memory=""
redis_max_memory=""
redis_memory_percent=""
redis_info=""
if redis_info=$(redis_cli INFO memory 2>/dev/null); then
  redis_used_memory=$(printf '%s\n' "$redis_info" | awk -F: '$1 == "used_memory" {gsub("\\r", "", $2); print $2; exit}')
  redis_max_memory=$(printf '%s\n' "$redis_info" | awk -F: '$1 == "maxmemory" {gsub("\\r", "", $2); print $2; exit}')
fi

redis_memory_data=()
if [[ "$redis_used_memory" =~ ^[0-9]+$ ]] \
  && [[ "$redis_max_memory" =~ ^[0-9]+$ ]] \
  && [ "$redis_max_memory" -gt 0 ]; then
  redis_memory_percent=$((redis_used_memory * 100 / redis_max_memory))
  redis_memory_data=(
    "MetricName=RedisUsedMemoryBytes,Value=$redis_used_memory,Unit=Bytes,Dimensions=[{Name=Environment,Value=$ENVIRONMENT}]"
    "MetricName=RedisMaxMemoryBytes,Value=$redis_max_memory,Unit=Bytes,Dimensions=[{Name=Environment,Value=$ENVIRONMENT}]"
    "MetricName=RedisMemoryUtilizationPercent,Value=$redis_memory_percent,Unit=Percent,Dimensions=[{Name=Environment,Value=$ENVIRONMENT}]"
  )
fi

# Nginx에 **설치된** 인증서의 남은 일수를 올린다. ACM의 DaysToExpiry는 ACM이 가진
# 인증서만 보므로, ACM이 갱신했는데 EC2 재배포가 실패한 경우를 잡지 못한다.
# 계획 4.1절이 감시하려는 위험이 정확히 그 경우다.
CERT="${TLS_CERT:-/etc/nginx/tls/masiton.click.fullchain.pem}"
cert_days=""
if [ -f "$CERT" ]; then
  not_after=$(openssl x509 -in "$CERT" -noout -enddate 2>/dev/null | cut -d= -f2)
  if [ -n "$not_after" ]; then
    cert_days=$(( ( $(date -d "$not_after" +%s) - $(date +%s) ) / 86400 ))
  fi
fi

metric_data=(
  "MetricName=HealthLive,Value=$live,Unit=None,Dimensions=[{Name=InstanceId,Value=$instance_id}]"
  "MetricName=HealthReady,Value=$ready,Unit=None,Dimensions=[{Name=InstanceId,Value=$instance_id}]"
  "MetricName=DependencyPostgres,Value=$db,Unit=None,Dimensions=[{Name=InstanceId,Value=$instance_id}]"
  "MetricName=DependencyRedis,Value=$redis,Unit=None,Dimensions=[{Name=InstanceId,Value=$instance_id}]"
  # 위 지표는 InstanceId 차원을 가져 ASG처럼 인스턴스가 계속 바뀌는 환경에서는
  # 알람 대상으로 고정할 수 없다. 차원 없는 같은 값을 함께 올려 fleet 전체를
  # 하나의 지표로 본다. 차원 집합이 다르면 CloudWatch가 별개 지표로 다루므로
  # 위 InstanceId 지표와 섞이지 않는다. Minimum으로 집계하면 한 대라도 0이면
  # 0이 되어 "어느 인스턴스든 Redis가 끊겼다"를 표현한다.
  #
  # Redis만 올린다. Postgres는 ready 그룹에 있어 이미 ALB가 target을 드레인하지만
  # Redis는 ready에 없어 어느 경로로도 감지되지 않는다(ADR-DEPLOY-005 5절).
  #
  # 차원을 완전히 비우면 이 계정·리전의 어떤 인스턴스가 올린 값이든 같은 지표에
  # 섞인다. 기존 단일 EC2가 이 스크립트를 받게 되면 그 인스턴스의 동거 Redis를
  # 종료하는 순간 ASG의 Redis는 멀쩡한데도 배포가 차단된다. 인스턴스가 바뀌어도
  # 변하지 않는 환경 이름으로 범위를 좁힌다.
  "MetricName=FleetDependencyRedis,Value=$redis,Unit=None,Dimensions=[{Name=Environment,Value=$ENVIRONMENT}]"
)
# Capacity data is intentionally environment-scoped: every ASG instance observes
# the same dedicated Redis. If INFO memory cannot be read or maxmemory is unset,
# omit all three metrics so the capacity alarm treats missing data as a detection
# path failure.
metric_data+=("${redis_memory_data[@]}")
# 인증서를 읽지 못했으면 지표를 올리지 않는다. 0을 올리면 만료 임박으로 오탐하고,
# 임의값을 올리면 실제 만료를 가린다. 지표가 끊기면 알람이 breaching으로 잡는다.
if [ -n "$cert_days" ]; then
  metric_data+=("MetricName=InstalledCertificateDaysToExpiry,Value=$cert_days,Unit=Count,Dimensions=[{Name=InstanceId,Value=$instance_id}]")
fi

# 전송 실패를 삼키지 않는다. FleetDependencyRedis가 올라가지 않으면 CodeDeploy
# alarm은 결측을 breaching으로 다뤄 배포를 차단한다. 권한 누락이나 네트워크
# 문제는 systemd 단위 실패와 배포 게이트 차단으로 즉시 드러나야 한다.
put_status=0
aws cloudwatch put-metric-data --region "$REGION" --namespace "$NAMESPACE" \
  --metric-data "${metric_data[@]}" || put_status=$?

echo "live=$live ready=$ready postgres=$db redis=$redis redis_memory_percent=${redis_memory_percent:-미확인} cert_days=${cert_days:-미확인}"

if [ "$put_status" -ne 0 ]; then
  echo "CloudWatch 지표 전송에 실패했다 (exit $put_status). 감지 경로가 동작하지 않는다." >&2
  exit "$put_status"
fi
