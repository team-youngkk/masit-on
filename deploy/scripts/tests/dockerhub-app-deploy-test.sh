#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
DOCKERHUB_SCRIPT="$REPOSITORY_ROOT/deploy/scripts/dockerhub-app-deploy.sh"
APP_DEPLOY_SCRIPT="$REPOSITORY_ROOT/deploy/scripts/app-deploy.sh"

assert_contains() {
  local needle="$1"
  local file="$2"
  grep -Fq -- "$needle" "$file" || {
    echo "기대 문자열이 없다: $needle ($(basename "$file"))" >&2
    exit 1
  }
}

assert_not_contains() {
  local needle="$1"
  local file="$2"
  if grep -Fq -- "$needle" "$file"; then
    echo "금지 문자열이 있다: $needle ($(basename "$file"))" >&2
    exit 1
  fi
}

bash -n "$DOCKERHUB_SCRIPT"
bash -n "$APP_DEPLOY_SCRIPT"

# Stage root를 먼저 검증하고, Docker Hub token은 argv가 아닌 stdin으로만 받는다.
assert_contains '[[ "$STAGE" =~ ^/run/masiton/deploy/masiton-deploy\.[A-Za-z0-9]{6}$ ]]' "$DOCKERHUB_SCRIPT"
assert_contains 'ALLOWED_NAMESPACE="${2:?Docker Hub namespace를 지정한다}"' "$DOCKERHUB_SCRIPT"
assert_contains 'validate_image_ref backend "$BACKEND_IMAGE_REF"' "$DOCKERHUB_SCRIPT"
assert_contains 'require_stage_file "$file"' "$DOCKERHUB_SCRIPT"
assert_contains 'observability-cleanup.sh nginx-install.sh nginx-smoke.sh' "$DOCKERHUB_SCRIPT"
assert_contains '"$STAGE/observability-cleanup.sh"' "$DOCKERHUB_SCRIPT"
assert_not_contains 'cloudwatch-install.sh' "$DOCKERHUB_SCRIPT"
assert_not_contains 'health-metrics.sh' "$DOCKERHUB_SCRIPT"
assert_contains '/usr/bin/docker login docker.io --username "$USERNAME" --password-stdin' "$DOCKERHUB_SCRIPT"
assert_not_contains 'DOCKERHUB_PULL_TOKEN' "$DOCKERHUB_SCRIPT"
if grep -Eq -- 'docker login[^[:space:]]*[[:space:]].*(--password|-p)[[:space:]=]' "$DOCKERHUB_SCRIPT"; then
  echo 'Docker Hub login이 password argv를 사용한다' >&2
  exit 1
fi

# Docker Hub digest 배포의 rollback 준비는 기존 ECR digest도 안전하게 pull할 수 있어야 한다.
assert_contains 'validate_ecr_digest_image_ref' "$APP_DEPLOY_SCRIPT"
assert_contains 'prepare_ecr_rollback_image' "$APP_DEPLOY_SCRIPT"
assert_contains 'aws ecr get-login-password --region "$ecr_region"' "$APP_DEPLOY_SCRIPT"
assert_contains 'docker login --username AWS --password-stdin "$registry"' "$APP_DEPLOY_SCRIPT"
assert_contains 'export DOCKER_CONFIG="$ecr_docker_config"' "$APP_DEPLOY_SCRIPT"
assert_contains 'docker image inspect "$reference"' "$APP_DEPLOY_SCRIPT"
assert_contains 'docker pull "$reference"' "$APP_DEPLOY_SCRIPT"
assert_contains "trap '' INT TERM HUP" "$APP_DEPLOY_SCRIPT"
assert_contains 'mv -f "$temporary" "$source"' "$APP_DEPLOY_SCRIPT"
assert_contains 'http://127.0.0.1:8080/internal/health/ready' "$APP_DEPLOY_SCRIPT"
assert_contains 'http://127.0.0.1:3000/' "$APP_DEPLOY_SCRIPT"

# 기존 ECR tag 모드는 계속 별도 경로로 유지한다.
assert_contains 'if [ "$DEPLOYMENT_MODE" = ecr ]; then' "$APP_DEPLOY_SCRIPT"
assert_contains 'aws ecr describe-images --region "$REGION"' "$APP_DEPLOY_SCRIPT"
assert_contains '--image-refs <backend digest ref> <frontend digest ref>' "$APP_DEPLOY_SCRIPT"
if grep -Eq -- 'docker login[^[:space:]]*[[:space:]].*(--password|-p)[[:space:]=]' "$APP_DEPLOY_SCRIPT"; then
  echo 'app-deploy login이 password argv를 사용한다' >&2
  exit 1
fi

echo 'Docker Hub app deploy contract: PASS'
