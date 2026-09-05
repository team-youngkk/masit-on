#!/usr/bin/env bash
# 운영 EC2에 백엔드·프론트엔드 이미지를 배포한다 — M2-09.
#
# 사용: sudo ./app-deploy.sh <커밋 SHA> [스테이징 디렉터리]
#       sudo ./app-deploy.sh --image-refs <backend digest ref> <frontend digest ref> [스테이징 디렉터리]
#
# 기본 모드는 ECR에서 커밋 태그를 digest로 굳힌다. `--image-refs` 모드는 이미 검증된
# Docker Hub digest 참조를 직접 받아 ECR 조회를 하지 않는다. 두 모드 모두 실행 참조를
# digest로 두어 배포된 것이 정확히 무엇인지 기록·대조한다(ADR-RUNTIME-001 11·13절,
# NFR-DEPLOYMENT-003).
#
# 롤백은 이전 커밋 SHA로 이 스크립트를 다시 실행하는 것이다.
set -euo pipefail

# BEGIN SHARED REDIS ENDPOINT CONTRACT
redis_ipv4_to_words() {
  local host="$1"
  local -a parts=()
  local part value high low high_part middle_part low_part
  [[ "$host" =~ ^(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})$ ]] || return 1
  IFS=. read -r -a parts <<< "$host"
  for part in "${parts[@]}"; do
    value=$((10#$part))
    (( value <= 255 )) || return 1
  done
  high_part=$((10#${parts[0]})); middle_part=$((10#${parts[1]})); low_part=$((10#${parts[2]})); value=$((10#${parts[3]}))
  high=$((high_part * 256 + middle_part)); low=$((low_part * 256 + value))
  REDIS_IPV4_HIGH="$high"
  REDIS_IPV4_LOW="$low"
}

redis_ipv6_append_groups() {
  local group_list="$1" group value
  local -a groups=()
  [ -n "$group_list" ] || return 0
  IFS=: read -r -a groups <<< "$group_list"
  for group in "${groups[@]}"; do
    [[ "$group" =~ ^[0-9a-f]{1,4}$ ]] || return 1
    value=$((16#$group))
    redis_ipv6_words+=("$value")
  done
}

redis_ipv6_to_words() {
  local host="$1" left right dotted high_hex low_hex
  local left_count right_count zero_count i group
  local -a right_groups=()
  redis_ipv6_words=()
  [[ "$host" == *:* ]] || return 1
  if [[ "$host" == *.* ]]; then
    dotted="${host##*:}"
    [[ "$dotted" != "$host" ]] || return 1
    redis_ipv4_to_words "$dotted" || return 1
    printf -v high_hex '%x' "$REDIS_IPV4_HIGH"
    printf -v low_hex '%x' "$REDIS_IPV4_LOW"
    host="${host%:*}:$high_hex:$low_hex"
  fi
  case "$host" in
    *::*)
      left="${host%%::*}"; right="${host#*::}"
      [[ "$left" != *::* && "$right" != *::* ]] || return 1
      [[ -z "$left" || ( "$left" != :* && "$left" != *: ) ]] || return 1
      [[ -z "$right" || ( "$right" != :* && "$right" != *: ) ]] || return 1
      redis_ipv6_append_groups "$left" || return 1
      left_count="${#redis_ipv6_words[@]}"
      if [ -n "$right" ]; then
        IFS=: read -r -a right_groups <<< "$right"
        for group in "${right_groups[@]}"; do
          [[ "$group" =~ ^[0-9a-f]{1,4}$ ]] || return 1
        done
        right_count="${#right_groups[@]}"
      else
        right_count=0
      fi
      (( left_count + right_count < 8 )) || return 1
      zero_count=$((8 - left_count - right_count))
      for ((i = 0; i < zero_count; i++)); do redis_ipv6_words+=(0); done
      redis_ipv6_append_groups "$right" || return 1
      ;;
    *)
      [[ "$host" != :* && "$host" != *: ]] || return 1
      redis_ipv6_append_groups "$host" || return 1
      ;;
  esac
  (( "${#redis_ipv6_words[@]}" == 8 ))
}

redis_ip_is_approved() {
  local address="$1" first_octet second_octet
  local -a words=()
  if redis_ipv4_to_words "$address"; then
    first_octet=$((REDIS_IPV4_HIGH / 256)); second_octet=$((REDIS_IPV4_HIGH % 256))
    (( first_octet == 10 )) && return 0
    (( first_octet == 172 && second_octet >= 16 && second_octet <= 31 )) && return 0
    (( first_octet == 192 && second_octet == 168 )) && return 0
    return 1
  fi
  redis_ipv6_to_words "$address" || return 1
  words=("${redis_ipv6_words[@]}")
  (( words[0] >= 0xfc00 && words[0] <= 0xfdff ))
}

redis_host_is_noncanonical_numeric_ipv4() {
  local host="${1%.}"
  [[ "$host" =~ ^[0-9]+(\.[0-9]+){0,3}$ ]] || return 1
  ! redis_ipv4_to_words "$host"
}

redis_resolve_host() {
  local host="$1"
  if getent ahosts --no-addrconfig "$host" 2>/dev/null; then
    return 0
  fi
  getent ahosts "$host" 2>/dev/null
}

validate_redis_host() {
  local host="${1-}" normalized_host address selected_address="" resolved_any=no scope
  [ -n "$host" ] || return 1
  [[ "$host" != *[[:space:]]* ]] || return 1
  [[ "$host" != */* ]] || return 1
  normalized_host="${host,,}"
  normalized_host="${normalized_host%.}"
  if [[ "$normalized_host" == *%* ]]; then
    [[ "$normalized_host" == *:* ]] || return 1
    scope="${normalized_host#*%}"
    normalized_host="${normalized_host%%\%*}"
    [ -n "$scope" ] || return 1
    [[ "$scope" != *%* ]] || return 1
  fi
  case "$normalized_host" in
    localhost|localhost.localdomain|127) return 1 ;;
  esac
  redis_host_is_noncanonical_numeric_ipv4 "$normalized_host" && return 1
  if redis_ipv4_to_words "$normalized_host" || redis_ipv6_to_words "$normalized_host"; then
    redis_ip_is_approved "$normalized_host" || return 1
    REDIS_VALIDATED_HOST="$normalized_host"
    return 0
  fi
  while read -r address _; do
    [ -n "$address" ] || continue
    if ! redis_ipv4_to_words "$address" && ! redis_ipv6_to_words "$address"; then
      return 1
    fi
    resolved_any=yes
    redis_ip_is_approved "$address" || return 1
    [ -n "$selected_address" ] || selected_address="$address"
  done < <(redis_resolve_host "$normalized_host")
  [ "$resolved_any" = yes ] || return 1
  REDIS_VALIDATED_HOST="$selected_address"
  return 0
}

validate_redis_port() {
  local port="${1-}"
  [[ "$port" =~ ^[0-9]{1,5}$ ]] || return 1
  local numeric=$((10#$port))
  (( numeric >= 1 && numeric <= 65535 ))
}

validate_shared_redis_endpoint() {
  local host="${1-}"
  local port="${2-}"
  if ! validate_redis_host "$host"; then
    echo "공유 Redis host가 비어 있거나 승인된 사설 주소가 아니다" >&2
    return 1
  fi
  if ! validate_redis_port "$port"; then
    echo "공유 Redis port가 유효하지 않다" >&2
    return 1
  fi
}
# END SHARED REDIS ENDPOINT CONTRACT

DEPLOYMENT_ENV_FILE="${DEPLOYMENT_ENV_FILE:-/etc/masiton/deployment.env}"
if [ -f "$DEPLOYMENT_ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$DEPLOYMENT_ENV_FILE"
  set +a
fi

DEPLOYMENT_MODE=ecr
BACKEND_IMAGE_REF=""
FRONTEND_IMAGE_REF=""
if [ "${1:-}" = --image-refs ]; then
  [ "$#" -ge 3 ] && [ "$#" -le 4 ] || {
    echo '사용: app-deploy.sh --image-refs <backend digest ref> <frontend digest ref> [스테이징 디렉터리]' >&2
    exit 1
  }
  DEPLOYMENT_MODE=digest
  BACKEND_IMAGE_REF="$2"
  FRONTEND_IMAGE_REF="$3"
  STAGE="${4:-/tmp/masiton-deploy}"
else
  TAG="${1:?배포할 커밋 SHA를 지정한다}"
  STAGE="${2:-/tmp/masiton-deploy}"
fi
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

validate_digest_image_ref() {
  local component="$1"
  local reference="$2"
  case "$component" in
    backend)
      [[ "$reference" =~ ^docker\.io/[a-z0-9][a-z0-9._-]*/masiton-backend@sha256:[0-9a-f]{64}$ ]] ;;
    frontend)
      [[ "$reference" =~ ^docker\.io/[a-z0-9][a-z0-9._-]*/masiton-frontend@sha256:[0-9a-f]{64}$ ]] ;;
    *)
      return 1 ;;
  esac || {
    echo "$component 이미지 참조가 Docker Hub digest 형식이 아니다" >&2
    return 1
  }
}

validate_ecr_digest_image_ref() {
  local component="$1"
  local reference="$2"
  case "$component" in
    backend)
      [[ "$reference" =~ ^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/masiton-backend@sha256:[0-9a-f]{64}$ ]] ;;
    frontend)
      [[ "$reference" =~ ^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/masiton-frontend@sha256:[0-9a-f]{64}$ ]] ;;
    *)
      return 1 ;;
  esac || {
    echo "기존 ${component} rollback 이미지 참조가 허용된 Docker Hub 또는 ECR digest 형식이 아니다" >&2
    return 1
  }
}

prepare_ecr_rollback_image() {
  local component="$1"
  local reference="$2"
  local registry ecr_region ecr_docker_config

  if docker image inspect "$reference" >/dev/null 2>&1; then
    return 0
  fi

  registry="${reference%%/*}"
  if [[ "$registry" =~ ^[0-9]{12}\.dkr\.ecr\.([a-z0-9-]+)\.amazonaws\.com$ ]]; then
    ecr_region="${BASH_REMATCH[1]}"
  else
    echo "기존 ${component} rollback ECR registry 형식을 확인할 수 없다" >&2
    return 1
  fi

  # ECR 자격 증명이 운영자의 기존 Docker config에 남지 않도록 fallback pull에만
  # 사용하는 임시 config를 둔다. 비밀번호는 AWS CLI stdout에서 stdin으로만 흐른다.
  ecr_docker_config=$(mktemp -d "$staged/ecr-docker-config.XXXXXX")
  chmod 0700 "$ecr_docker_config"
  if ! (
    export DOCKER_CONFIG="$ecr_docker_config"
    aws ecr get-login-password --region "$ecr_region" \
      | docker login --username AWS --password-stdin "$registry" >/dev/null
    docker pull "$reference" >/dev/null
  ); then
    rm -rf "$ecr_docker_config"
    echo "기존 ${component} rollback ECR 이미지 login/pull 실패" >&2
    return 1
  fi
  rm -rf "$ecr_docker_config"
}

if [ "$DEPLOYMENT_MODE" = ecr ]; then
  aws ecr get-login-password --region "$REGION" \
    | docker login --username AWS --password-stdin "$REGISTRY" >/dev/null
fi

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
  if [ "${#smoke_redis_keys[@]}" -gt 0 ] && declare -F redis_cli >/dev/null 2>&1; then
    redis_cli DEL "${smoke_redis_keys[@]}" >/dev/null 2>&1 || true
  fi
  rm -rf "$staged"
}
trap cleanup EXIT

rollback() {
  local original_exit_code="${1:-$?}"
  set +e
  trap - ERR
  trap '' INT TERM HUP
  [ "$rollback_enabled" = yes ] || return "$original_exit_code"
  echo '배포 후 health 실패: 이전 이미지·실행 산출물로 rollback을 시도한다' >&2
  local rollback_failed=no

  restore_asset() {
    local source="$1"
    local backup="$2"
    local temporary
    if [ -f "$backup" ]; then
      install -d "$(dirname "$source")" || rollback_failed=yes
      temporary="${source}.rollback.$$"
      rm -f "$temporary" || rollback_failed=yes
      if cp -a "$backup" "$temporary" && mv -f "$temporary" "$source"; then
        :
      else
        rm -f "$temporary" || true
        rollback_failed=yes
      fi
    elif [ -f "${backup}.missing" ]; then
      rm -f "$source" || rollback_failed=yes
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
  systemctl daemon-reload || rollback_failed=yes
  for service in masiton-backend.service masiton-frontend.service; do
    if [ -f "/etc/systemd/system/$service" ]; then
      systemctl restart "$service" || rollback_failed=yes
    else
      systemctl disable --now "$service" >/dev/null 2>&1 || true
    fi
  done
  rollback_backend_health=no
  rollback_frontend_health=no
  for attempt in $(seq 1 12); do
    if [ "$rollback_backend_health" != yes ] &&
       curl -fsS -m 3 http://127.0.0.1:8080/internal/health/ready >/dev/null 2>&1; then
      rollback_backend_health=yes
    fi
    if [ "$rollback_frontend_health" != yes ] &&
       curl -fsS -m 3 http://127.0.0.1:3000/ >/dev/null 2>&1; then
      rollback_frontend_health=yes
    fi
    if [ "$rollback_backend_health" = yes ] && [ "$rollback_frontend_health" = yes ]; then
      break
    fi
    [ "$attempt" -lt 12 ] && sleep 5
  done
  rollback_dependencies_body="$staged/rollback-dependencies.json"
  rollback_dependencies_status=$(curl -sS -m 5 -o "$rollback_dependencies_body" -w '%{http_code}' \
    http://127.0.0.1:8080/internal/health/dependencies 2>/dev/null || printf '000')
  if [ "$rollback_dependencies_status" = 200 ]; then
    if ! python3 - "$rollback_dependencies_body" <<'PY'
import json
import sys

try:
    with open(sys.argv[1], encoding="utf-8") as response:
        body = json.load(response)
except (OSError, UnicodeDecodeError, json.JSONDecodeError):
    raise SystemExit(1)

components = body.get("components") if isinstance(body, dict) else None
if not isinstance(components, dict) or any(
    not isinstance(component, dict) or component.get("status") != "UP"
    for component in components.values()
):
    raise SystemExit(1)
PY
    then
      echo 'rollback 후 dependency health 확인 실패' >&2
      rollback_failed=yes
    fi
  else
    echo "rollback 후 dependency health HTTP 실패: $rollback_dependencies_status" >&2
    rollback_failed=yes
  fi
  if [ -x "$OPT_DIR/bin/runtime-health.sh" ] && ! "$OPT_DIR/bin/runtime-health.sh"; then
    echo 'rollback 후 runtime health 확인 실패' >&2
    rollback_failed=yes
  fi
  if command -v nginx >/dev/null 2>&1 && [ -f /etc/nginx/conf.d/masiton.click.conf ]; then
    nginx -t >/dev/null 2>&1 || rollback_failed=yes
    systemctl is-active --quiet nginx || rollback_failed=yes
  fi
  if [ "$rollback_backend_health" != yes ] || [ "$rollback_frontend_health" != yes ]; then
    echo 'rollback 후 backend/frontend 최소 health 확인 실패' >&2
    rollback_failed=yes
  fi

  if [ "$rollback_failed" = yes ]; then
    echo 'rollback 자체가 실패했다. 수동 복구가 필요하다.' >&2
    return 1
  fi
  return "$original_exit_code"
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
  if [ "$DEPLOYMENT_MODE" = digest ]; then
    case "$component" in
      backend) reference="$BACKEND_IMAGE_REF" ;;
      frontend) reference="$FRONTEND_IMAGE_REF" ;;
    esac
    validate_digest_image_ref "$component" "$reference"
    digest="${reference##*@}"
  else
    repository="masiton-${component}"
    digest=$(aws ecr describe-images --region "$REGION" \
      --repository-name "$repository" --image-ids "imageTag=${TAG}" \
      --query 'imageDetails[0].imageDigest' --output text)
    if [ -z "$digest" ] || [ "$digest" = "None" ]; then
      echo "${repository}:${TAG} 이미지가 ECR에 없다. CI가 push했는지 확인한다." >&2
      exit 1
    fi
    reference="${REGISTRY}/${repository}@${digest}"
  fi
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

if [ "$DEPLOYMENT_MODE" = digest ]; then
  for component in backend frontend; do
    current_ref_file="$OPT_DIR/etc/${component}.image"
    if [ -f "$current_ref_file" ]; then
      previous_reference=$(tr -d ' \r\n' < "$current_ref_file")
      [ -n "$previous_reference" ] || {
        echo "기존 ${component} 이미지 참조가 비어 있어 rollback 이미지를 준비할 수 없다" >&2
        exit 1
      }
      if [[ "$previous_reference" == docker.io/* ]]; then
        validate_digest_image_ref "$component" "$previous_reference"
        docker pull "$previous_reference" >/dev/null
      else
        validate_ecr_digest_image_ref "$component" "$previous_reference"
        prepare_ecr_rollback_image "$component" "$previous_reference"
      fi
    fi
  done
fi

# 여기까지 왔으면 두 이미지와 이전 실행 산출물의 백업이 모두 준비됐다. 이후 첫
# install부터 rollback 보호를 켜서 활성 경로 변경 중 실패도 복구 대상으로 포함한다.
rollback_enabled=yes
trap rollback ERR

handle_signal() {
  local signal_exit_code=143
  trap - INT TERM HUP
  rollback "$signal_exit_code"
  exit $?
}
trap handle_signal INT TERM HUP

# 이제 활성 경로를 교체한다.
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
dependencies_failures="$staged/dependency-failures.txt"
check_dependency_health() {
  local dependencies_body="$1"
  local dependencies_failures="$2"
  local dependencies_status=""
  local dependency_request_status
  local dependency_parse_status

  if dependencies_status=$(curl -sS -m 5 -o "$dependencies_body" -w '%{http_code}' \
    http://127.0.0.1:8080/internal/health/dependencies); then
    :
  else
    dependency_request_status=$?
    echo "백엔드 dependency health HTTP 요청 실패: HTTP ${dependencies_status:-000}" >&2
    return "$dependency_request_status"
  fi
  if [ "$dependencies_status" != "200" ]; then
    echo "백엔드 dependency health HTTP 실패: HTTP $dependencies_status" >&2
    return 1
  fi

  if python3 - "$dependencies_body" "$dependencies_failures" <<'PY'
import json
import sys

try:
    with open(sys.argv[1], encoding="utf-8") as response:
        body = json.load(response)
except (OSError, UnicodeDecodeError, json.JSONDecodeError):
    raise SystemExit(2)

components = body.get("components") if isinstance(body, dict) else None
if not isinstance(components, dict):
    raise SystemExit(2)

expected_components = ("db", "mail", "redis")
failed_components = []
for name in expected_components:
    component = components.get(name)
    if not isinstance(component, dict) or component.get("status") != "UP":
        failed_components.append(name)

for name in sorted(components):
    if name in expected_components:
        continue
    component = components[name]
    if not isinstance(component, dict) or component.get("status") != "UP":
        failed_components.append(name)

if failed_components:
    with open(sys.argv[2], "w", encoding="utf-8") as failures:
        failures.write(" ".join(failed_components))
    raise SystemExit(1)
PY
  then
    :
  else
    dependency_parse_status=$?
    if [ "$dependency_parse_status" = "1" ]; then
      dependency_components=$(<"$dependencies_failures")
      echo "백엔드 dependency health 구성요소 실패: $dependency_components (HTTP $dependencies_status)" >&2
    else
      echo "백엔드 dependency health JSON 파싱 실패: HTTP $dependencies_status" >&2
    fi
    return 1
  fi
}

# 함수 호출 자체를 조건문으로 감싸지 않아, 실패 시 기존 ERR trap이 롤백을 실행한다.
check_dependency_health "$dependencies_body" "$dependencies_failures"

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

# 통합 로그인 rate-limit은 신뢰된 Nginx peer에서만 전달 헤더를 해석해야 한다.
# 환경변수의 값은 출력하지 않고, 배포된 컨테이너의 실제 주입 결과만 비교한다.
backend_env=$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' masiton-backend)
auth_proxy_enabled=$(printf '%s\n' "$backend_env" | awk -F= '$1 == "AUTH_LOGIN_REVERSE_PROXY_ENABLED" { print $2; exit }')
auth_trusted_proxies=$(printf '%s\n' "$backend_env" | awk -F= '$1 == "AUTH_LOGIN_TRUSTED_PROXY_ADDRESSES" { print $2; exit }')
[ "$auth_proxy_enabled" = "true" ] || { echo "통합 로그인 reverse-proxy 환경변수 주입 실패" >&2; exit 1; }
[ "$auth_trusted_proxies" = "127.0.0.1" ] || { echo "통합 로그인 trusted-proxy 환경변수 주입 실패" >&2; exit 1; }

# Nginx가 이미 설치·활성화된 호스트에서는 실제 설정을 검사하고 public API 경계를
# 확인한다. 로그인 endpoint는
# 공개 경로이므로 형식이 유효한 가짜 자격 증명을 보내 애플리케이션의 401을 확인한다.
# 빈 JSON({})은 인증 경계가 아니라 입력 검증 오류(400)를 확인하게 된다.
nginx_site_conf=/etc/nginx/conf.d/masiton.click.conf
if command -v nginx >/dev/null 2>&1 && [ -f "$nginx_site_conf" ]; then
  nginx -t
else
  echo "Nginx 설정 smoke 스킵: 설치된 masit-on site 설정이 없다(nginx-install 단계에서 검증한다)."
fi
if [ -f "$nginx_site_conf" ] && systemctl is-active --quiet nginx; then
  public_login_body='{"email":"deploy-smoke-invalid@example.com","password":"invalid-password-123"}'
  public_login_status=$(curl -k -sS -m 5 -o /dev/null -w '%{http_code}' \
    --resolve masiton.click:443:127.0.0.1 \
    -H 'X-Forwarded-For: 198.51.100.99' \
    -H 'Content-Type: application/json' -X POST --data "$public_login_body" \
    https://masiton.click/api/auth/tokens)
  [ "$public_login_status" = "401" ] || {
    echo "public Nginx 통합 로그인 경계 확인 실패: HTTP $public_login_status" >&2
    exit 1
  }
else
  echo "public Nginx 경로 smoke 스킵: 설치된 masit-on site 설정이 없거나 Nginx가 비활성 상태다(이후 nginx-install에서 검증한다)."
fi

# 운영 Origin이 주입됐는지 확인한다. 유효한 Origin과 Token 없는 요청은 인증 실패(401)여야
# 하며, Origin 설정이 localhost 기본값으로 남으면 이 지점에서 403이 된다.
refresh_status=$(curl -sS -m 5 -o /dev/null -w '%{http_code}' \
  -X POST -H 'Origin: https://masiton.click' http://127.0.0.1:8080/api/auth/tokens/refresh)
[ "$refresh_status" = "401" ] || { echo "회원 refresh Origin 검증 실패: HTTP $refresh_status" >&2; exit 1; }

# Nginx peer(127.0.0.1)를 신뢰해 서로 다른 X-Forwarded-For가 실제로 별도
# login-source 버킷을 만드는지 Redis 키로 확인한다.
if [ "${REQUIRE_SHARED_REDIS:-false}" = true ]; then
  REDIS_HOST="${REDIS_HOST:-$(aws ssm get-parameter --region "$REGION" --name /masiton/redis/host \
    --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || printf '')}"
  REDIS_PORT="${REDIS_PORT:-$(aws ssm get-parameter --region "$REGION" --name /masiton/redis/port \
    --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || printf '')}"
  validate_shared_redis_endpoint "$REDIS_HOST" "$REDIS_PORT" || exit 1
  REDIS_HOST="$REDIS_VALIDATED_HOST"
else
  REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
  REDIS_PORT="${REDIS_PORT:-6379}"
  validate_redis_port "$REDIS_PORT" || {
    echo "Redis port가 유효하지 않다" >&2
    exit 1
  }
fi
REDIS_PASSWORD_FILE="${REDIS_PASSWORD_FILE:-/run/masiton/secrets/spring.data.redis.password}"
[ -r "$REDIS_PASSWORD_FILE" ] || {
  echo "Redis smoke 비밀값 파일을 읽을 수 없다: $REDIS_PASSWORD_FILE" >&2
  exit 1
}
read -r REDIS_PASSWORD_UID REDIS_PASSWORD_GID < <(stat -c '%u %g' "$REDIS_PASSWORD_FILE")
[[ "$REDIS_PASSWORD_UID" =~ ^[0-9]+$ && "$REDIS_PASSWORD_GID" =~ ^[0-9]+$ ]] || {
  echo "Redis smoke 비밀값 파일 소유자 UID:GID를 확인할 수 없다: $REDIS_PASSWORD_FILE" >&2
  exit 1
}
readonly REDIS_CLI_IMAGE='redis@sha256:8096655e437712b07503796fb64d81359256cfcff0ab29d95a7da72863786efb'
redis_cli() {
  docker run --rm --network host \
    --mount "type=bind,source=$REDIS_PASSWORD_FILE,target=/run/secrets/redis-password,readonly" \
    --user "$REDIS_PASSWORD_UID:$REDIS_PASSWORD_GID" \
    "$REDIS_CLI_IMAGE" \
    sh -c 'host=$1; port=$2; shift 2; exec redis-cli --askpass -h "$host" -p "$port" --raw "$@" < /run/secrets/redis-password' \
    redis-smoke "$REDIS_HOST" "$REDIS_PORT" "$@"
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

# Nginx 전환도 app-deploy의 롤백 보호 안에서 수행한다. 새 백엔드와 구버전
# validation-gate Nginx가 섞이지 않도록, Nginx smoke/TLS 설치 실패 시 이전
# Nginx 복구와 이전 앱 산출물 복구가 같은 실패 경로에서 실행되어야 한다.
"$STAGE/nginx-install.sh" "$STAGE"

trap - ERR INT TERM HUP
rollback_enabled=no

echo "배포 완료: mode=${DEPLOYMENT_MODE}"
