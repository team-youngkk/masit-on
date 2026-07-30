#!/usr/bin/env bash
# 운영 애플리케이션 컨테이너를 포그라운드로 실행한다 — M2-09.
# systemd unit의 ExecStart가 이 스크립트를 호출한다.
#
# 사용: app-run.sh backend|frontend
#
# 비밀값은 `docker run -e VAR` 통과 형식으로만 넘긴다. `-e VAR=값` 형태로 쓰면
# 같은 인스턴스의 `ps`와 `docker inspect`에 평문이 남는다. 환경 파일도 만들지
# 않는다(NFR-SECURITY-003, M2-07 완료 조건).
#
# 네트워크는 host를 쓴다. ADR-RUNTIME-001 11절이 운영 설정의 Docker 서비스명을
# 금지하므로 앱은 저장소에 127.0.0.1로 붙어야 하고, Nginx도 127.0.0.1의 8080·3000으로
# 전달한다(M2-08). 브리지 네트워크로는 두 방향 모두 성립하지 않는다.
set -euo pipefail

component="${1:?backend 또는 frontend를 지정한다}"
REGION="${AWS_REGION:-ap-northeast-2}"
IMAGE_REF_FILE="/opt/masiton/etc/${component}.image"

[ -f "$IMAGE_REF_FILE" ] || { echo "배포된 이미지 참조가 없다: $IMAGE_REF_FILE" >&2; exit 1; }
image=$(tr -d ' \r\n' < "$IMAGE_REF_FILE")
[ -n "$image" ] || { echo "이미지 참조가 비어 있다: $IMAGE_REF_FILE" >&2; exit 1; }

# Parameter Store 값을 셸 환경으로만 읽어들인다.
param() {
  aws ssm get-parameter --region "$REGION" --name "$1" --with-decryption \
    --query 'Parameter.Value' --output text
}
optional_param() {
  aws ssm get-parameter --region "$REGION" --name "$1" --with-decryption \
    --query 'Parameter.Value' --output text 2>/dev/null || printf ''
}

case "$component" in
  backend)
    export SPRING_PROFILES_ACTIVE=prod
    DB_URL=$(param /masiton/db/url); export DB_URL
    DB_USERNAME=$(param /masiton/db/username); export DB_USERNAME
    DB_PASSWORD=$(param /masiton/db/password); export DB_PASSWORD
    export REDIS_HOST=127.0.0.1
    export REDIS_PORT=6379
    REDIS_PASSWORD=$(param /masiton/redis/password); export REDIS_PASSWORD
    JWT_KEY_ID=$(param /masiton/jwt/key-id); export JWT_KEY_ID
    JWT_PRIVATE_KEY_PEM=$(param /masiton/jwt/private-key-pem); export JWT_PRIVATE_KEY_PEM
    JWT_PUBLIC_KEY_PEM=$(param /masiton/jwt/public-key-pem); export JWT_PUBLIC_KEY_PEM
    # 두 Key는 없을 수 있다. 없으면 빈 값으로 기동하고 관리자 등록 흐름에서만 실패한다.
    KAKAO_REST_API_KEY=$(optional_param /masiton/integration/kakao/rest-api-key); export KAKAO_REST_API_KEY
    YOUTUBE_API_KEY=$(optional_param /masiton/integration/youtube/api-key); export YOUTUBE_API_KEY

    exec /usr/bin/docker run --name masiton-backend \
      --network host \
      --memory 1024m \
      --log-driver json-file --log-opt max-size=10m --log-opt max-file=3 \
      -e SPRING_PROFILES_ACTIVE \
      -e DB_URL -e DB_USERNAME -e DB_PASSWORD \
      -e REDIS_HOST -e REDIS_PORT -e REDIS_PASSWORD \
      -e JWT_KEY_ID -e JWT_PRIVATE_KEY_PEM -e JWT_PUBLIC_KEY_PEM \
      -e KAKAO_REST_API_KEY -e YOUTUBE_API_KEY \
      "$image"
    ;;
  frontend)
    # 프론트엔드는 같은 인스턴스의 백엔드로 /api를 전달한다. 비밀값이 없다.
    export API_BASE_URL=http://127.0.0.1:8080
    export PORT=3000
    export HOSTNAME=127.0.0.1

    exec /usr/bin/docker run --name masiton-frontend \
      --network host \
      --memory 512m \
      --log-driver json-file --log-opt max-size=10m --log-opt max-file=3 \
      -e API_BASE_URL -e PORT -e HOSTNAME \
      "$image"
    ;;
  *)
    echo "알 수 없는 구성 요소: $component" >&2
    exit 1
    ;;
esac
