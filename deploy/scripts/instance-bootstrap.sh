#!/usr/bin/env bash
# ASG/Blue-Green 새 인스턴스의 멱등 bootstrap 진입점.
set -euo pipefail

TAG="${1:?배포할 커밋 SHA를 지정한다}"
STAGE="${2:-/tmp/masiton-deploy}"

for script in app-deploy.sh nginx-install.sh runtime-health.sh; do
  [ -x "$STAGE/$script" ] || { echo "스테이징에 실행 가능한 $script 가 없다" >&2; exit 1; }
done

install -d -m 0750 /opt/masiton/bin /opt/masiton/etc
install -m 0750 "$STAGE/runtime-health.sh" /opt/masiton/bin/runtime-health.sh
# 앱 ASG는 공유 Redis에 연결한다. 로컬 Redis는 단일 EC2 호환을 명시적으로
# 요청한 경우에만 설치해 색상 전환마다 세션 저장소가 새로 생기는 것을 막는다.
if [ "${INSTALL_LOCAL_REDIS:-false}" = true ]; then
  [ -x "$STAGE/redis-install.sh" ] || { echo "로컬 Redis 설치가 요청됐지만 redis-install.sh가 없다" >&2; exit 1; }
  "$STAGE/redis-install.sh" "$STAGE"
fi
"$STAGE/app-deploy.sh" "$TAG" "$STAGE"

# 상태 지표 수집을 설치한다. Redis 장애는 ready 그룹에 없어 ALB target health로
# 드러나지 않으므로, 이 지표가 CodeDeploy alarm의 유일한 감지 경로다
# (ADR-DEPLOY-005 5절). CodeDeploy 경로에서는 after-install.sh의 chmod와 revision
# 패키징이 이 파일들을 먼저 강제하므로 아래 스킵은 기존 단일 EC2 등 레거시
# 호출자를 위한 방어선이다. 기존 단일 EC2의 SSM 배포 경로는 이 파일들을 싣지 않고
# 프로비저닝 시점에 이미 설치돼 있다.
if [ -x "$STAGE/cloudwatch-install.sh" ]; then
  "$STAGE/cloudwatch-install.sh" "$STAGE"
else
  echo 'cloudwatch-install.sh가 스테이징에 없어 상태 지표 설치를 건너뛴다' >&2
fi

"/opt/masiton/bin/runtime-health.sh"

