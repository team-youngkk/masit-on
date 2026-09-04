#!/usr/bin/env bash
# Docker Hub private image 인증을 한 번만 수행한 뒤 공통 운영 배포를 호출한다.
#
# 사용: sudo ./dockerhub-app-deploy.sh <Docker Hub username> <허용 namespace> \
#        <backend digest ref> <frontend digest ref> <스테이징 디렉터리>
#
# Docker Hub token은 인자로 받지 않는다. 표준 입력을 docker login --password-stdin에
# 그대로 전달하고, 인증 정보가 남는 Docker config는 임시 디렉터리에만 둔다.
set -euo pipefail

[ "$#" -eq 5 ] || {
  echo '사용: dockerhub-app-deploy.sh <Docker Hub username> <허용 namespace> <backend digest ref> <frontend digest ref> <스테이징 디렉터리>' >&2
  exit 1
}
USERNAME="${1:?Docker Hub username을 지정한다}"
ALLOWED_NAMESPACE="${2:?Docker Hub namespace를 지정한다}"
BACKEND_IMAGE_REF="${3:?backend digest ref를 지정한다}"
FRONTEND_IMAGE_REF="${4:?frontend digest ref를 지정한다}"
STAGE="${5:?스테이징 디렉터리를 지정한다}"

[[ "$USERNAME" =~ ^[a-z0-9][a-z0-9_.-]{0,254}$ ]] || {
  echo 'Docker Hub username 형식이 올바르지 않다' >&2
  exit 1
}
[[ "$ALLOWED_NAMESPACE" =~ ^[a-z0-9][a-z0-9_.-]{0,254}$ ]] || {
  echo 'Docker Hub namespace 형식이 올바르지 않다' >&2
  exit 1
}

validate_image_ref() {
  local component="$1"
  local reference="$2"
  local namespace repository

  [[ "$reference" =~ ^docker[.]io/([a-z0-9][a-z0-9_.-]{0,254})/masiton-(backend|frontend)@sha256:[0-9a-f]{64}$ ]] || {
    echo "$component 이미지 참조가 Docker Hub canonical digest 형식이 아니다" >&2
    return 1
  }
  namespace="${BASH_REMATCH[1]}"
  repository="${BASH_REMATCH[2]}"
  [ "$namespace" = "$ALLOWED_NAMESPACE" ] || {
    echo "$component 이미지 참조가 허용된 Docker Hub namespace를 가리키지 않는다" >&2
    return 1
  }
  [ "$repository" = "$component" ] || {
    echo "$component 이미지 참조가 올바른 Docker Hub 저장소를 가리키지 않는다" >&2
    return 1
  }
}

validate_image_ref backend "$BACKEND_IMAGE_REF"
validate_image_ref frontend "$FRONTEND_IMAGE_REF"

[[ "$STAGE" =~ ^/run/masiton/deploy/masiton-deploy\.[A-Za-z0-9]{6}$ ]] || {
  echo '스테이징 디렉터리 형식이 올바르지 않다' >&2
  exit 1
}
[ "$(id -u)" -eq 0 ] || {
  echo 'Docker Hub 배포는 root로 실행돼야 한다' >&2
  exit 1
}
[ -d "$STAGE" ] && [ ! -L "$STAGE" ] || {
  echo "root 스테이징 디렉터리가 없다: $STAGE" >&2
  exit 1
}
[ "$(stat -c '%u' "$STAGE")" -eq 0 ] || {
  echo "스테이징 디렉터리 소유자가 root가 아니다: $STAGE" >&2
  exit 1
}

require_stage_file() {
  local file="$1"
  [ -f "$STAGE/$file" ] && [ ! -L "$STAGE/$file" ] || {
    echo "필수 배포 산출물이 없다: $STAGE/$file" >&2
    return 1
  }
  [ "$(stat -c '%u' "$STAGE/$file")" -eq 0 ] || {
    echo "배포 산출물 소유자가 root가 아니다: $STAGE/$file" >&2
    return 1
  }
}

for file in \
  app-deploy.sh app-run.sh app-secrets-render.sh runtime-health.sh \
  cloudwatch-install.sh health-metrics.sh nginx-install.sh nginx-smoke.sh \
  tls-deploy-cert.sh masiton-backend.service masiton-frontend.service \
  amazon-cloudwatch-agent.json masiton-health-metrics.service masiton-health-metrics.timer \
  nginx.conf masiton.click.conf 00-masiton-upgrade-map.conf \
  masiton-tls-renew.service masiton-tls-renew.timer; do
  require_stage_file "$file"
done
for file in app-deploy.sh app-run.sh app-secrets-render.sh runtime-health.sh \
  cloudwatch-install.sh health-metrics.sh nginx-install.sh nginx-smoke.sh tls-deploy-cert.sh; do
  [ -x "$STAGE/$file" ] || {
    echo "배포 스크립트가 실행 가능하지 않다: $STAGE/$file" >&2
    exit 1
  }
done

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "원격 배포 필수 명령이 없다: $1" >&2
    return 1
  }
}
# sudo의 secure_path와 무관하게 배포 하위 스크립트가 사용자 writable PATH의
# 동명 명령을 실행하지 않도록 운영 표준 경로로 고정한다.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
for command_name in docker sudo systemctl bash tar base64 curl aws python3 \
  id stat uname install mktemp rm dnf rpm tr tail awk sha256sum cmp openssl \
  getent seq sleep dirname cp mkdir find wc cut chown tee; do
  require_command "$command_name"
done
[ -x /usr/bin/docker ] || {
  echo '원격 배포 Docker 경로가 없다: /usr/bin/docker' >&2
  exit 1
}

host_arch="$(uname -m)"
[ "$host_arch" = x86_64 ] || {
  echo "원격 호스트 아키텍처가 x86_64가 아니다: $host_arch" >&2
  exit 1
}
/usr/bin/docker info >/dev/null 2>&1 || {
  echo 'Docker daemon이 실행 중이 아니거나 접근할 수 없다' >&2
  exit 1
}
docker_arch="$(/usr/bin/docker info --format '{{.Architecture}}' 2>/dev/null)"
[ "$docker_arch" = amd64 ] || {
  echo "Docker daemon 플랫폼이 amd64가 아니다: $docker_arch" >&2
  exit 1
}

install -d -o root -g root -m 0700 /run/masiton/deploy
DOCKER_CONFIG=$(mktemp -d /run/masiton/deploy/dockerhub-config.XXXXXX)
chmod 0700 "$DOCKER_CONFIG"
export DOCKER_CONFIG
LOGIN_DONE=no

cleanup() {
  local exit_code=$?
  set +e
  if [ "$LOGIN_DONE" = yes ]; then
    /usr/bin/docker logout docker.io >/dev/null 2>&1 || true
  fi
  rm -rf "$DOCKER_CONFIG" "$STAGE"
  exit "$exit_code"
}
trap cleanup EXIT

/usr/bin/docker login docker.io --username "$USERNAME" --password-stdin >/dev/null
LOGIN_DONE=yes
"$STAGE/app-deploy.sh" --image-refs "$BACKEND_IMAGE_REF" "$FRONTEND_IMAGE_REF" "$STAGE"
