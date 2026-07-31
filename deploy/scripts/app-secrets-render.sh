#!/usr/bin/env bash
# 애플리케이션 비밀값을 tmpfs에 파일로 렌더링한다 — M2-09.
#
# 컨테이너 환경 변수로 비밀값을 넘기지 않는 이유는 Docker가 그것을
# `/var/lib/docker/containers/<id>/config.v2.json`에 평문으로 적기 때문이다.
# `docker inspect`로 읽히고 루트 볼륨 스냅샷에도 들어가 ADR-SEC-001 11절의 평문
# 저장 금지에 걸린다.
#
# 여기서 만드는 파일들은 tmpfs(`/run`)에 있어 디스크에 쓰이지 않고 재기동하면
# 사라진다. 그래서 기동마다 다시 만들어야 하고 systemd unit의 ExecStartPre가
# 그것을 보장한다.
#
# 파일 이름이 곧 속성 이름이다(Spring `configtree:`). application-prod.yml이 이
# 이름들을 실제 속성에 매핑한다. 이름을 바꾸면 그 파일도 함께 바꿔야 한다.
set -euo pipefail

REGION="${AWS_REGION:-ap-northeast-2}"
SECRETS_DIR="${SECRETS_DIR:-/run/masiton/secrets}"
# 컨테이너의 애플리케이션 사용자 uid·gid다. 두 이미지 모두 1001을 쓴다.
APP_UID="${APP_UID:-1001}"
APP_GID="${APP_GID:-1001}"

# /run/masiton은 0711로 통일한다. 권한이 엇갈리면 마지막에 만든 쪽이 이겨
# 다른 소비자가 접근하지 못한다(Nginx htpasswd에서 실제로 겪었다).
install -d -m 0711 "$(dirname "$SECRETS_DIR")"
install -d -m 0500 -o "$APP_UID" -g "$APP_GID" "$SECRETS_DIR"

# 이전 렌더링 잔여물을 지운다. 파라미터를 없앤 뒤에도 옛 값이 남으면
# 지웠다고 믿은 비밀값이 계속 주입된다.
find "$SECRETS_DIR" -mindepth 1 -maxdepth 1 -type f -delete

# 필수 값. 하나라도 없으면 기동을 시도하지 않는다.
render_required() {
  local property="$1" parameter="$2"
  local value
  value=$(aws ssm get-parameter --region "$REGION" --name "$parameter" --with-decryption \
    --query 'Parameter.Value' --output text)
  if [ -z "$value" ] || [ "$value" = "None" ]; then
    echo "필수 비밀값을 읽지 못했다: $parameter" >&2
    exit 1
  fi
  write_secret "$property" "$value"
}

# 선택 값. 없으면 파일을 만들지 않고 애플리케이션의 빈 기본값을 쓴다.
render_optional() {
  local property="$1" parameter="$2"
  local value
  value=$(aws ssm get-parameter --region "$REGION" --name "$parameter" --with-decryption \
    --query 'Parameter.Value' --output text 2>/dev/null || printf '')
  if [ -z "$value" ] || [ "$value" = "None" ]; then
    echo "  $property: 없음 (선택 값)"
    return
  fi
  write_secret "$property" "$value"
}

write_secret() {
  local property="$1" value="$2"
  local path="$SECRETS_DIR/$property"
  umask 077
  # 값에 섞인 CR을 걷어낸다. Windows 셸에서 만든 값이 `\r`을 물면 인증이 어긋난다
  # (Redis requirepass에서 실제로 겪었다).
  printf '%s' "$(printf %s "$value" | tr -d '\r')" > "$path"
  chown "$APP_UID:$APP_GID" "$path"
  chmod 0400 "$path"
  echo "  $property: ${#value}자"
}

# 파일 이름이 곧 최종 속성 이름이다. 짧은 별칭을 쓰고 프로파일에서 매핑하면
# 프로파일 문서가 JWT 키 속성을 선언하게 되어 ConfigurationLayeringTest에 걸린다.
# 이름을 바꾸면 application-prod.yml 주석의 목록도 함께 바꾼다.
render_required "spring.datasource.password"            /masiton/db/password
render_required "spring.data.redis.password"            /masiton/redis/password
render_required "masiton.security.jwt.key-id"           /masiton/jwt/key-id
render_required "masiton.security.jwt.private-key-pem"  /masiton/jwt/private-key-pem
render_required "masiton.security.jwt.public-key-pem"   /masiton/jwt/public-key-pem
render_optional "masiton.integration.kakao.rest-api-key" /masiton/integration/kakao/rest-api-key
render_optional "masiton.integration.youtube.api-key"    /masiton/integration/youtube/api-key

echo "렌더링 완료: $SECRETS_DIR ($(find "$SECRETS_DIR" -type f | wc -l)개 파일)"
