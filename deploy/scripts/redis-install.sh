#!/usr/bin/env bash
# 운영 EC2에 M2-05 Redis 구성을 설치하고 기동한다. 재실행해도 결과가 같다.
#
# 저장소 파일을 인스턴스로 옮긴 스테이징 디렉터리를 인자로 받는다.
# 스테이징에는 다음 세 파일이 있어야 한다.
#   redis.conf  masiton-redis.service  redis-render-conf.sh
#
# 사용: sudo ./redis-install.sh [스테이징 디렉터리]
set -euo pipefail

STAGE="${1:-/tmp/masiton-deploy}"
OPT_DIR=/opt/masiton
UNIT=/etc/systemd/system/masiton-redis.service

for f in redis.conf masiton-redis.service redis-render-conf.sh; do
  [ -f "$STAGE/$f" ] || { echo "스테이징에 $f 가 없다: $STAGE" >&2; exit 1; }
done

install -d -m 0755 "$OPT_DIR" "$OPT_DIR/redis" "$OPT_DIR/bin"
# 데이터 디렉터리는 컨테이너의 redis 사용자(uid 999)가 쓴다.
install -d -m 0700 -o 999 -g 999 "$OPT_DIR/redis/data"

install -m 0644 "$STAGE/redis.conf" "$OPT_DIR/redis/redis.conf"
install -m 0750 "$STAGE/redis-render-conf.sh" "$OPT_DIR/bin/redis-render-conf.sh"
install -m 0644 "$STAGE/masiton-redis.service" "$UNIT"

systemctl daemon-reload
systemctl enable masiton-redis.service >/dev/null
systemctl restart masiton-redis.service

# 기동을 기다린다. 임의 대기 대신 상태를 확인한다.
for _ in $(seq 1 30); do
  if [ "$(docker inspect -f '{{.State.Running}}' masiton-redis 2>/dev/null)" = "true" ]; then
    break
  fi
  sleep 1
done

systemctl is-enabled masiton-redis.service
systemctl is-active masiton-redis.service
docker inspect -f 'image={{.Image}} running={{.State.Running}}' masiton-redis
