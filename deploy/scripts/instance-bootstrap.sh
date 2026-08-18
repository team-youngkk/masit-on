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
"$STAGE/nginx-install.sh" "$STAGE"
"/opt/masiton/bin/runtime-health.sh"
