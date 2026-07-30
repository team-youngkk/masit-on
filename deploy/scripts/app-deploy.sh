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

for f in app-run.sh masiton-backend.service masiton-frontend.service; do
  [ -f "$STAGE/$f" ] || { echo "스테이징에 $f 가 없다: $STAGE" >&2; exit 1; }
done

install -d -m 0755 "$OPT_DIR/bin" "$OPT_DIR/etc"
install -m 0750 "$STAGE/app-run.sh" "$OPT_DIR/bin/app-run.sh"
install -m 0644 "$STAGE/masiton-backend.service" /etc/systemd/system/masiton-backend.service
install -m 0644 "$STAGE/masiton-frontend.service" /etc/systemd/system/masiton-frontend.service

aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY" >/dev/null

# 두 이미지를 모두 준비한 뒤에 활성 참조를 함께 교체한다. 백엔드 참조를 먼저
# 기록하면 프론트엔드 조회나 pull이 실패했을 때 실행 중 컨테이너는 그대로여도
# 다음 재기동부터 백엔드만 새 버전으로 떠 혼합 버전이 남는다.
staged=$(mktemp -d)
trap 'rm -rf "$staged"' EXIT

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

# 여기까지 왔으면 두 이미지가 모두 로컬에 있다. 이제 활성 참조를 교체한다.
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

front=""
for _ in $(seq 1 36); do
  if curl -fsS -m 3 -o /dev/null http://127.0.0.1:3000/ 2>/dev/null; then
    front=yes
    break
  fi
  sleep 5
done
[ -n "$front" ] || { echo "프론트엔드 응답 확인 실패" >&2; systemctl status masiton-frontend.service --no-pager -l | tail -20; exit 1; }

echo "배포 완료: tag=${TAG}"
