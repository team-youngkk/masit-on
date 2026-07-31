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
cleanup() {
  if [ "${#smoke_redis_keys[@]}" -gt 0 ] && [ -n "${redis_password:-}" ]; then
    docker exec -e REDISCLI_AUTH="$redis_password" masiton-redis redis-cli \
      DEL "${smoke_redis_keys[@]}" >/dev/null 2>&1 || true
  fi
  rm -rf "$staged"
}
trap cleanup EXIT

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

# 여기까지 왔으면 두 이미지가 모두 로컬에 있다. 이제 활성 경로를 교체한다.
install -d -m 0755 "$OPT_DIR/bin" "$OPT_DIR/etc"
install -m 0750 "$STAGE/app-run.sh" "$OPT_DIR/bin/app-run.sh"
install -m 0750 "$STAGE/app-secrets-render.sh" "$OPT_DIR/bin/app-secrets-render.sh"
install -m 0644 "$STAGE/masiton-backend.service" /etc/systemd/system/masiton-backend.service
install -m 0644 "$STAGE/masiton-frontend.service" /etc/systemd/system/masiton-frontend.service

for component in backend frontend; do
  install -m 0644 "$staged/${component}.image" "$OPT_DIR/etc/${component}.image"
done

systemctl daemon-reload
systemctl enable masiton-backend.service masiton-frontend.service >/dev/null
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

# 운영 Origin이 주입됐는지 확인한다. 유효한 Origin과 Token 없는 요청은 인증 실패(401)여야
# 하며, Origin 설정이 localhost 기본값으로 남으면 이 지점에서 403이 된다.
refresh_status=$(curl -sS -m 5 -o /dev/null -w '%{http_code}' \
  -X POST -H 'Origin: https://masiton.click' http://127.0.0.1:8080/api/auth/tokens/refresh)
[ "$refresh_status" = "401" ] || { echo "회원 refresh Origin 검증 실패: HTTP $refresh_status" >&2; exit 1; }

# Nginx peer(127.0.0.1)를 신뢰해 서로 다른 X-Forwarded-For가 실제로 별도
# login-source 버킷을 만드는지 Redis 키로 확인한다.
redis_password=$(< /run/masiton/secrets/spring.data.redis.password)
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
docker exec -e REDISCLI_AUTH="$redis_password" masiton-redis redis-cli \
  DEL "${smoke_redis_keys[@]}" >/dev/null

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
  source_count=$(docker exec -e REDISCLI_AUTH="$redis_password" masiton-redis redis-cli GET "$source_key")
  [ "$source_count" = "1" ] || { echo "회원 rate-limit source 키 검증 실패: $source_key" >&2; exit 1; }
done
docker exec -e REDISCLI_AUTH="$redis_password" masiton-redis redis-cli \
  DEL "${smoke_redis_keys[@]}" >/dev/null
smoke_redis_keys=()

echo "배포 완료: tag=${TAG}"
