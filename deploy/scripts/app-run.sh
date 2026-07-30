#!/usr/bin/env bash
# 운영 애플리케이션 컨테이너를 포그라운드로 실행한다 — M2-09.
# systemd unit의 ExecStart가 이 스크립트를 호출한다.
#
# 사용: app-run.sh backend|frontend
#
# 비밀값은 환경 변수로 넘기지 않는다. `-e VAR` 통과 형식은 명령행 노출은 막지만
# 값이 컨테이너 스펙에 들어가고 Docker가 그것을
# `/var/lib/docker/containers/<id>/config.v2.json`에 평문으로 적는다. `docker inspect`로
# 읽히고 루트 볼륨 스냅샷에도 들어가 ADR-SEC-001 11절의 평문 저장 금지에 걸린다.
#
# 대신 app-secrets-render.sh가 tmpfs에 만든 파일을 읽기 전용으로 마운트하고
# 애플리케이션이 `configtree:`로 읽는다(application-prod.yml). 비밀이 아닌 접속값만
# 환경 변수로 남긴다.
#
# 네트워크는 host를 쓴다. ADR-RUNTIME-001 11절이 운영 설정의 Docker 서비스명을
# 금지하므로 앱은 저장소에 127.0.0.1로 붙어야 하고, Nginx도 127.0.0.1의 8080·3000으로
# 전달한다(M2-08). 브리지 네트워크로는 두 방향 모두 성립하지 않는다.
set -euo pipefail

component="${1:?backend 또는 frontend를 지정한다}"
REGION="${AWS_REGION:-ap-northeast-2}"
IMAGE_REF_FILE="/opt/masiton/etc/${component}.image"
# 컨테이너 안과 밖의 경로를 같게 둔다. application-prod.yml이 이 값을 그대로 읽는다.
SECRETS_DIR="${SECRETS_DIR:-/run/masiton/secrets}"
export SECRETS_DIR

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
    # 비밀이 아닌 값만 환경 변수로 넘긴다. 접속 주소와 사용자명은 비밀이 아니며
    # 기록 문서에도 그대로 적혀 있다.
    export SPRING_PROFILES_ACTIVE=prod
    DB_URL=$(param /masiton/db/url); export DB_URL
    DB_USERNAME=$(param /masiton/db/username); export DB_USERNAME
    export REDIS_HOST=127.0.0.1
    export REDIS_PORT=6379

    [ -d "$SECRETS_DIR" ] || { echo "비밀값 디렉터리가 없다: $SECRETS_DIR" >&2; exit 1; }

    exec /usr/bin/docker run --name masiton-backend \
      --network host \
      --memory 1024m \
      --log-driver json-file --log-opt max-size=10m --log-opt max-file=3 \
      --volume "$SECRETS_DIR":"$SECRETS_DIR":ro \
      -e SPRING_PROFILES_ACTIVE \
      -e DB_URL -e DB_USERNAME \
      -e REDIS_HOST -e REDIS_PORT \
      -e SECRETS_DIR \
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
