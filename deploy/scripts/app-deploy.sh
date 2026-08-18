#!/usr/bin/env bash
# ECR 이미지를 받아 운영 EC2에 배포한다 — M2-09.
#
# 사용: sudo ./app-deploy.sh <커밋 SHA> [스테이징 디렉터리]
#
# 태그로 받아 **digest로 굳혀** 실행한다. 리포지토리가 IMMUTABLE이라 같은 태그가
# 다른 이미지를 가리킬 수는 없지만, 실행 참조를 digest로 두면 배포된 것이 정확히
# 무엇인지 기록·대조할 수 있다(ADR-RUNTIME-001 11·13절, NFR-DEPLOYMENT-003).
#
# 롤백은 이전 커밋 SHA로 이 스크립트를 다시 실행하는 것이다.
set -euo pipefail

DEPLOYMENT_ENV_FILE="${DEPLOYMENT_ENV_FILE:-/etc/masiton/deployment.env}"
if [ -f "$DEPLOYMENT_ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$DEPLOYMENT_ENV_FILE"
  set +a
fi

TAG="${1:?배포할 커밋 SHA를 지정한다}"
STAGE="${2:-/tmp/masiton-deploy}"
REGION="${AWS_REGION:-ap-northeast-2}"
REGISTRY="711457211155.dkr.ecr.${REGION}.amazonaws.com"
OPT_DIR=/opt/masiton

# app-secrets-render.sh도 배포 산출물이다. backend unit의 ExecStartPre가
# /opt/masiton/bin/app-secrets-render.sh를 실행하므로 설치하지 않으면 새 인스턴스는
# 파일 없음으로 기동에 실패하고, 기존 인스턴스는 렌더러 변경이 배포에 반영되지 않는다.
for f in app-run.sh app-secrets-render.sh masiton-backend.service masiton-frontend.service; do
  [ -f "$STAGE/$f" ] || { echo "스테이징에 $f 가 없다: $STAGE" >&2; exit 1; }
done
[ -f "$STAGE/runtime-health.sh" ] || { echo "스테이징에 runtime-health.sh가 없다" >&2; exit 1; }

aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY" >/dev/null

# 두 이미지를 모두 준비한 뒤에 활성 참조와 실행 산출물을 함께 교체한다. 백엔드
# 참조를 먼저 기록하면 프론트엔드 조회나 pull이 실패했을 때 실행 중 컨테이너는
# 그대로여도 다음 재기동부터 백엔드만 새 버전으로 떠 혼합 버전이 남는다.
#
# 실행 스크립트와 unit도 같은 이유로 여기서 미룬다. 이미지 준비 전에 활성 경로에
# 덮어쓰면 이후 단계가 실패했을 때 이미지 참조와 실행 중 컨테이너는 이전 버전인데
# 다음 재기동부터 새 app-run.sh·unit이 적용된다. 설정 형식이나 사전 실행 조건이
# 함께 바뀐 배포에서는 실패한 배포가 재부팅 후 장애를 만든다.
staged=$(mktemp -d)
smoke_redis_keys=()
previous="$staged/previous"
rollback_enabled=no
cleanup() {
  if [ "${#smoke_redis_keys[@]}" -gt 0 ] && [ -n "${redis_password:-}" ]; then
    if declare -F redis_cli >/dev/null 2>&1; then
      redis_cli DEL "${smoke_redis_keys[@]}" >/dev/null 2>&1 || true
    else
      docker exec -e REDISCLI_AUTH="$redis_password" masiton-redis redis-cli \
        DEL "${smoke_redis_keys[@]}" >/dev/null 2>&1 || true
    fi
  fi
  rm -rf "$staged"
}
trap cleanup EXIT

rollback() {
  set +e
  trap - ERR
  [ "$rollback_enabled" = yes ] || return 0
  echo '배포 후 health 실패: 이전 이미지·실행 산출물로 rollback을 시도한다' >&2

  restore_asset() {
    local source="$1"
    local backup="$2"
    if [ -f "$backup" ]; then
      install -d "$(dirname "$source")"
      rm -f "$source"
      cp -a "$backup" "$source"
    elif [ -f "${backup}.missing" ]; then
      rm -f "$source"
    fi
  }

  for component in backend frontend; do
    restore_asset "$OPT_DIR/etc/${component}.image" "$previous/${component}.image"
  done
  restore_asset "$OPT_DIR/bin/app-run.sh" "$previous/opt/masiton/bin/app-run.sh"
  restore_asset "$OPT_DIR/bin/app-secrets-render.sh" "$previous/opt/masiton/bin/app-secrets-render.sh"
  restore_asset "$OPT_DIR/bin/runtime-health.sh" "$previous/opt/masiton/bin/runtime-health.sh"
  restore_asset "/etc/systemd/system/masiton-backend.service" "$previous/etc/systemd/system/masiton-backend.service"
  restore_asset "/etc/systemd/system/masiton-frontend.service" "$previous/etc/systemd/system/masiton-frontend.service"
  systemctl daemon-reload
  for service in masiton-backend.service masiton-frontend.service; do
    if [ -f "/etc/systemd/system/$service" ]; then
      systemctl restart "$service"
    else
      systemctl disable --now "$service" >/dev/null 2>&1 || true
    fi
  done
}

backup_asset() {
  local source="$1"
  local backup="$2"
  if [ -e "$source" ]; then
    install -d "$(dirname "$backup")"
    cp -a "$source" "$backup"
  else
    install -d "$(dirname "$backup")"
    : > "${backup}.missing"
  fi
}

for component in backend frontend; do
  repository="masiton-${component}"
  digest=$(aws ecr describe-images --region "$REGION" \
    --repository-name "$repository" --image-ids "imageTag=${TAG}" \
    --query 'imageDetails[0].imageDigest' --output text)
  if [ -z "$digest" ] || [ "$digest" = "None" ]; then
    echo "${repository}:${TAG} 이미지가 ECR에 없다. CI가 push했는지 확인한다." >&2
    exit 1
  fi
  reference="${REGISTRY}/${repository}@${digest}"
  docker pull "$reference" >/dev/null
  printf '%s\n' "$reference" > "$staged/${component}.image"
  echo "${component}: ${digest}"
done

install -d -m 0750 "$previous"
for component in backend frontend; do
  backup_asset "$OPT_DIR/etc/${component}.image" "$previous/${component}.image"
done
backup_asset "$OPT_DIR/bin/app-run.sh" "$previous/opt/masiton/bin/app-run.sh"
backup_asset "$OPT_DIR/bin/app-secrets-render.sh" "$previous/opt/masiton/bin/app-secrets-render.sh"
backup_asset "$OPT_DIR/bin/runtime-health.sh" "$previous/opt/masiton/bin/runtime-health.sh"
backup_asset "/etc/systemd/system/masiton-backend.service" "$previous/etc/systemd/system/masiton-backend.service"
backup_asset "/etc/systemd/system/masiton-frontend.service" "$previous/etc/systemd/system/masiton-frontend.service"

# 여기까지 왔으면 두 이미지가 모두 로컬에 있다. 이제 활성 경로를 교체한다.
install -d -m 0755 "$OPT_DIR/bin" "$OPT_DIR/etc"
install -m 0750 "$STAGE/app-run.sh" "$OPT_DIR/bin/app-run.sh"
install -m 0750 "$STAGE/app-secrets-render.sh" "$OPT_DIR/bin/app-secrets-render.sh"
install -m 0644 "$STAGE/masiton-backend.service" /etc/systemd/system/masiton-backend.service
install -m 0644 "$STAGE/masiton-frontend.service" /etc/systemd/system/masiton-frontend.service
install -m 0750 "$STAGE/runtime-health.sh" "$OPT_DIR/bin/runtime-health.sh"

for component in backend frontend; do
  install -m 0644 "$staged/${component}.image" "$OPT_DIR/etc/${component}.image"
done

systemctl daemon-reload
systemctl enable masiton-backend.service masiton-frontend.service >/dev/null
rollback_enabled=yes
trap rollback ERR
systemctl restart masiton-backend.service
systemctl restart masiton-frontend.service

# 기동을 임의 대기 없이 상태로 확인한다. 백엔드는 Flyway 마이그레이션과
# 커넥션 풀 초기화를 마쳐야 ready가 된다.
ready=""
for _ in $(seq 1 60); do
  if curl -fsS -m 3 http://127.0.0.1:8080/internal/health/ready >/dev/null 2>&1; then
    ready=yes
    break
  fi
  sleep 5
done
[ -n "$ready" ] || { echo "백엔드 ready 확인 실패" >&2; systemctl status masiton-backend.service --no-pager -l | tail -20; exit 1; }

dependencies_body="$staged/dependencies.json"
dependencies_status=$(curl -sS -m 5 -o "$dependencies_body" -w '%{http_code}' \
  http://127.0.0.1:8080/internal/health/dependencies)
if [ "$dependencies_status" != "200" ] || ! python3 - "$dependencies_body" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as response:
    body = json.load(response)
if body.get("components", {}).get("mail", {}).get("status") != "UP":
    raise SystemExit(1)
PY
then
  echo "백엔드 mail dependency 확인 실패: HTTP $dependencies_status" >&2
  exit 1
fi

front=""
for _ in $(seq 1 36); do
  if curl -fsS -m 3 -o /dev/null http://127.0.0.1:3000/ 2>/dev/null; then
    front=yes
    break
  fi
  sleep 5
done
[ -n "$front" ] || { echo "프론트엔드 응답 확인 실패" >&2; systemctl status masiton-frontend.service --no-pager -l | tail -20; exit 1; }
"$OPT_DIR/bin/runtime-health.sh"

# 관리자 로그인 rate-limit은 신뢰된 Nginx peer에서만 전달 헤더를 해석해야 한다.
# 환경변수의 값은 출력하지 않고, 배포된 컨테이너의 실제 주입 결과만 비교한다.
backend_env=$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' masiton-backend)
admin_proxy_enabled=$(printf '%s\n' "$backend_env" | awk -F= '$1 == "ADMIN_LOGIN_REVERSE_PROXY_ENABLED" { print $2; exit }')
admin_trusted_proxies=$(printf '%s\n' "$backend_env" | awk -F= '$1 == "ADMIN_LOGIN_TRUSTED_PROXY_ADDRESSES" { print $2; exit }')
[ "$admin_proxy_enabled" = "true" ] || { echo "관리자 reverse-proxy 환경변수 주입 실패" >&2; exit 1; }
[ "$admin_trusted_proxies" = "127.0.0.1" ] || { echo "관리자 trusted-proxy 환경변수 주입 실패" >&2; exit 1; }

# app-deploy는 nginx-install보다 먼저 실행될 수 있다. Nginx가 이미 설치·활성화된
# 호스트에서는 실제 설정을 검사하고 public API 경계를 확인한다. 무세션 요청은
# verification gate에 걸리는 것이 정상(401)이므로 로그인 성공을 요구하지 않는다.
nginx_site_conf=/etc/nginx/conf.d/masiton.click.conf
if command -v nginx >/dev/null 2>&1 && [ -f "$nginx_site_conf" ]; then
  nginx -t
else
  echo "Nginx 설정 smoke 스킵: 설치된 masit-on site 설정이 없다(nginx-install 단계에서 검증한다)."
fi
if [ -f "$nginx_site_conf" ] && systemctl is-active --quiet nginx; then
  public_admin_status=$(curl -k -sS -m 5 -o /dev/null -w '%{http_code}' \
    --resolve masiton.click:443:127.0.0.1 \
    -H 'X-Forwarded-For: 198.51.100.99' \
    -H 'Content-Type: application/json' -X POST --data '{}' \
    https://masiton.click/api/admin/auth/tokens)
  [ "$public_admin_status" = "401" ] || {
    echo "public Nginx 관리자 로그인 경계 확인 실패: HTTP $public_admin_status" >&2
    exit 1
  }
else
  echo "public Nginx 경로 smoke 스킵: 설치된 masit-on site 설정이 없거나 Nginx가 비활성 상태다(nginx-install 단계에서 검증한다)."
fi

# 운영 Origin이 주입됐는지 확인한다. 유효한 Origin과 Token 없는 요청은 인증 실패(401)여야
# 하며, Origin 설정이 localhost 기본값으로 남으면 이 지점에서 403이 된다.
refresh_status=$(curl -sS -m 5 -o /dev/null -w '%{http_code}' \
  -X POST -H 'Origin: https://masiton.click' http://127.0.0.1:8080/api/auth/tokens/refresh)
[ "$refresh_status" = "401" ] || { echo "회원 refresh Origin 검증 실패: HTTP $refresh_status" >&2; exit 1; }

# Nginx peer(127.0.0.1)를 신뢰해 서로 다른 X-Forwarded-For가 실제로 별도
# login-source 버킷을 만드는지 Redis 키로 확인한다.
redis_password=$(< /run/masiton/secrets/spring.data.redis.password)
REDIS_HOST="${REDIS_HOST:-$(aws ssm get-parameter --region "$REGION" --name /masiton/redis/host \
  --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || printf '')}"
if [ "${REQUIRE_SHARED_REDIS:-false}" = true ] && [ -z "$REDIS_HOST" ]; then
  echo "ASG 배포 smoke에서도 공유 Redis endpoint가 필요하다: REDIS_HOST 또는 /masiton/redis/host" >&2
  exit 1
fi
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-$(aws ssm get-parameter --region "$REGION" --name /masiton/redis/port \
  --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || printf '')}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_CLI_IMAGE="${REDIS_CLI_IMAGE:-redis:8.8-alpine}"
redis_cli() {
  docker run --rm --network host -e REDISCLI_AUTH="$redis_password" "$REDIS_CLI_IMAGE" \
    redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw "$@"
}
rate_limit_keys() {
  python3 - "$1" "$2" <<'PY'
import hashlib
import hmac
import sys

with open("/run/masiton/secrets/masiton.member.rate-limit.secret", "rb") as secret_file:
    secret = secret_file.read().rstrip(b"\r\n")
source = sys.argv[1]
email = sys.argv[2]
digest = lambda value: hmac.new(secret, value, hashlib.sha256).hexdigest()
prefix = "auth:member:rate-limit:"
print(prefix + "login-source:" + digest(source.encode()))
print(prefix + "login-email:" + digest(email.encode()))
print(prefix + "login-email-source:" + digest((email + "\0" + source).encode()))
PY
}

mapfile -t first_client_keys < <(rate_limit_keys '198.51.100.10' 'deploy-smoke-1@invalid.example')
mapfile -t second_client_keys < <(rate_limit_keys '198.51.100.11' 'deploy-smoke-2@invalid.example')
smoke_redis_keys=("${first_client_keys[@]}" "${second_client_keys[@]}")
redis_cli DEL "${smoke_redis_keys[@]}" >/dev/null

for client in \
  '198.51.100.10|deploy-smoke-1@invalid.example' \
  '198.51.100.11|deploy-smoke-2@invalid.example'; do
  source=${client%%|*}
  email=${client#*|}
  login_body=$(printf '{"email":"%s","password":"invalid-password-123"}' "$email")
  curl -sS -m 5 -o /dev/null -X POST \
    -H 'Content-Type: application/json' -H "X-Forwarded-For: $source" \
    --data "$login_body" http://127.0.0.1:8080/api/auth/tokens || true
done

for source_key in "${first_client_keys[0]}" "${second_client_keys[0]}"; do
  source_count=$(redis_cli GET "$source_key")
  [ "$source_count" = "1" ] || { echo "회원 rate-limit source 키 검증 실패: $source_key" >&2; exit 1; }
done
redis_cli DEL "${smoke_redis_keys[@]}" >/dev/null
smoke_redis_keys=()

trap - ERR
rollback_enabled=no

echo "배포 완료: tag=${TAG}"
