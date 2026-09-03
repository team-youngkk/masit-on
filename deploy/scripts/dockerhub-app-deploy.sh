#!/usr/bin/env bash
# Docker Hub private image 인증을 한 번만 수행한 뒤 공통 운영 배포를 호출한다.
#
# 사용: sudo ./dockerhub-app-deploy.sh <Docker Hub username> \
#        <backend digest ref> <frontend digest ref> <스테이징 디렉터리>
#
# Docker Hub token은 인자로 받지 않는다. 표준 입력을 docker login --password-stdin에
# 그대로 전달하고, 인증 정보가 남는 Docker config는 임시 디렉터리에만 둔다.
set -euo pipefail

[ "$#" -eq 4 ] || {
  echo '사용: dockerhub-app-deploy.sh <Docker Hub username> <backend digest ref> <frontend digest ref> <스테이징 디렉터리>' >&2
  exit 1
}
USERNAME="${1:?Docker Hub username을 지정한다}"
BACKEND_IMAGE_REF="${2:?backend digest ref를 지정한다}"
FRONTEND_IMAGE_REF="${3:?frontend digest ref를 지정한다}"
STAGE="${4:?스테이징 디렉터리를 지정한다}"

[[ "$USERNAME" =~ ^[a-z0-9][a-z0-9_.-]{0,254}$ ]] || {
  echo 'Docker Hub username 형식이 올바르지 않다' >&2
  exit 1
}
[[ "$STAGE" =~ ^/tmp/masiton-deploy-[0-9]+$ ]] || {
  echo '스테이징 디렉터리 형식이 올바르지 않다' >&2
  exit 1
}
[ "$(id -u)" -eq 0 ] || {
  echo 'Docker Hub 배포는 root로 실행돼야 한다' >&2
  exit 1
}
[ -x "$STAGE/app-deploy.sh" ] || {
  echo "app-deploy.sh가 없다: $STAGE" >&2
  exit 1
}

install -d -m 0750 /run/masiton
DOCKER_CONFIG=$(mktemp -d /run/masiton/dockerhub-config.XXXXXX)
chmod 0700 "$DOCKER_CONFIG"
export DOCKER_CONFIG

cleanup() {
  local exit_code=$?
  set +e
  /usr/bin/docker logout docker.io >/dev/null 2>&1 || true
  rm -rf "$DOCKER_CONFIG" "$STAGE"
  exit "$exit_code"
}
trap cleanup EXIT

/usr/bin/docker login docker.io --username "$USERNAME" --password-stdin >/dev/null
"$STAGE/app-deploy.sh" --image-refs "$BACKEND_IMAGE_REF" "$FRONTEND_IMAGE_REF" "$STAGE"
