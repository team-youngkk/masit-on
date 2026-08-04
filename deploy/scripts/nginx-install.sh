#!/usr/bin/env bash
# 운영 EC2에 M2-08 Nginx와 TLS 구성을 설치한다. 재실행해도 결과가 같다.
#
# 저장소 파일을 인스턴스로 옮긴 스테이징 디렉터리를 인자로 받는다.
# 스테이징에는 다음 파일이 있어야 한다.
#   nginx.conf  masiton.click.conf  00-masiton-upgrade-map.conf
#   masiton-tls-renew.service  masiton-tls-renew.timer  tls-deploy-cert.sh
#
# 사용: sudo ./nginx-install.sh [스테이징 디렉터리]
set -euo pipefail

STAGE="${1:-/tmp/masiton-deploy}"
OPT_DIR=/opt/masiton

for f in nginx.conf masiton.click.conf 00-masiton-upgrade-map.conf \
         masiton-tls-renew.service masiton-tls-renew.timer tls-deploy-cert.sh; do
  [ -f "$STAGE/$f" ] || { echo "스테이징에 $f 가 없다: $STAGE" >&2; exit 1; }
done

if ! command -v nginx >/dev/null 2>&1; then
  dnf install -y nginx >/dev/null
fi
echo "nginx: $(nginx -v 2>&1)"

install -d -m 0755 "$OPT_DIR/bin"
install -m 0750 "$STAGE/tls-deploy-cert.sh" "$OPT_DIR/bin/tls-deploy-cert.sh"

# E1-T13 배포 전환: 새 검증 세션 경계를 먼저 배포·검증한 뒤에만 Basic Auth를
# 제거한다(expansion-1-task-breakdown.md). 백엔드가 아직 이전 버전이거나
# 기동하지 않았으면 여기서 중단하고 기존 Basic Auth 구성을 그대로 둔다 —
# 세션 쿠키 없는 요청은 계약상 401이어야 하며, 다른 응답은 새 경계가 아직
# 준비되지 않았다는 뜻이다.
verification_status=$(curl -sS -m 5 -o /dev/null -w '%{http_code}' \
  http://127.0.0.1:8080/internal/verification/session || echo "000")
if [ "$verification_status" != "401" ]; then
  echo "새 검증 세션 경계가 준비되지 않았다(HTTP $verification_status). app-deploy.sh로 백엔드를 먼저 배포·검증한 뒤 다시 실행한다. 기존 Basic Auth 구성은 그대로 둔다." >&2
  exit 1
fi

# 이제부터 Basic Auth 구성을 제거·교체하는 컷오버 구간이다. nginx -t나
# 재시작뿐 아니라 이 구간의 어떤 명령이라도 실패하면 직전 Basic 구성으로
# 복구해야 하므로(expansion-1-task-breakdown.md 178행), 지우거나 덮어쓰는
# 모든 파일을 먼저 백업하고 ERR trap으로 구간 전체를 감싼다.
ROLLBACK_DIR=$(mktemp -d)
BASIC_AUTH_DROPIN=/etc/systemd/system/nginx.service.d/10-masiton-basic-auth.conf
OLD_AUTH_MAP=/etc/nginx/conf.d/01-masiton-api-auth-map.conf
SITE_CONF=/etc/nginx/conf.d/masiton.click.conf
UPGRADE_MAP_CONF=/etc/nginx/conf.d/00-masiton-upgrade-map.conf
NGINX_CONF=/etc/nginx/nginx.conf

if [ -f "$BASIC_AUTH_DROPIN" ]; then cp -p "$BASIC_AUTH_DROPIN" "$ROLLBACK_DIR/basic-auth-dropin.conf"; fi
if [ -f "$OLD_AUTH_MAP" ]; then cp -p "$OLD_AUTH_MAP" "$ROLLBACK_DIR/auth-map.conf"; fi
if [ -f "$SITE_CONF" ]; then cp -p "$SITE_CONF" "$ROLLBACK_DIR/masiton.click.conf"; fi
if [ -f "$UPGRADE_MAP_CONF" ]; then cp -p "$UPGRADE_MAP_CONF" "$ROLLBACK_DIR/00-masiton-upgrade-map.conf"; fi
if [ -f "$NGINX_CONF" ]; then cp -p "$NGINX_CONF" "$ROLLBACK_DIR/nginx.conf"; fi

restore_or_remove() {
  if [ -f "$ROLLBACK_DIR/$1" ]; then install -m 0644 "$ROLLBACK_DIR/$1" "$2"; else rm -f "$2"; fi
}

rollback_basic_auth() {
  trap - ERR
  echo "컷오버 구간에서 실패했다. 백업한 직전 Basic Auth 구성으로 복구한다." >&2
  restore_or_remove basic-auth-dropin.conf "$BASIC_AUTH_DROPIN"
  restore_or_remove auth-map.conf "$OLD_AUTH_MAP"
  restore_or_remove masiton.click.conf "$SITE_CONF"
  restore_or_remove 00-masiton-upgrade-map.conf "$UPGRADE_MAP_CONF"
  if [ -f "$ROLLBACK_DIR/nginx.conf" ]; then install -m 0644 "$ROLLBACK_DIR/nginx.conf" "$NGINX_CONF"; fi
  systemctl daemon-reload
  systemctl restart nginx || echo "복구 후 재시작도 실패했다. 운영자가 직접 확인해야 한다." >&2
}

on_cutover_failure() {
  local status=$?
  rollback_basic_auth
  rm -rf "$ROLLBACK_DIR"
  exit "$status"
}
trap on_cutover_failure ERR

# M2-11 Basic Auth의 systemd 사전 실행 경계를 제거한다. 이전 설치의 drop-in이
# 남아 있으면 삭제한 렌더러를 계속 호출해 Nginx 재기동이 실패한다.
rm -f "$BASIC_AUTH_DROPIN"

# 인증서를 먼저 내려받아야 Nginx가 기동한다. ssl_certificate 파일이 없으면
# 설정 검사부터 실패한다.
AWS_REGION="${AWS_REGION:-ap-northeast-2}" "$OPT_DIR/bin/tls-deploy-cert.sh"

install -m 0644 "$STAGE/00-masiton-upgrade-map.conf" "$UPGRADE_MAP_CONF"
rm -f "$OLD_AUTH_MAP"
install -m 0644 "$STAGE/masiton.click.conf" "$SITE_CONF"

# 최상위 설정을 저장소 산출물로 교체한다. 배포판 기본 설정에는
# /usr/share/nginx/html을 서비스하는 server 블록이 있어 도메인 밖 접근에
# 기본 페이지가 응답한다. 첫 교체 때 원본을 한 번만 남긴다.
if [ ! -f /etc/nginx/nginx.conf.masiton-orig ]; then
  cp -p /etc/nginx/nginx.conf /etc/nginx/nginx.conf.masiton-orig
fi
install -m 0644 "$STAGE/nginx.conf" "$NGINX_CONF"

nginx -t
# 이전 Basic Auth drop-in을 제거했으므로 systemd 상태도 다시 읽는다.
systemctl daemon-reload
systemctl enable nginx >/dev/null
systemctl restart nginx
if [ "$(systemctl is-active nginx)" != "active" ]; then
  echo "Nginx 재시작이 active 상태로 확인되지 않았다." >&2
  rollback_basic_auth
  rm -rf "$ROLLBACK_DIR"
  exit 1
fi

# 컷오버가 성공했으므로 이 시점부터는 실패해도 Basic Auth를 되돌리지 않는다.
trap - ERR
rm -rf "$ROLLBACK_DIR"

install -m 0644 "$STAGE/masiton-tls-renew.service" /etc/systemd/system/masiton-tls-renew.service
install -m 0644 "$STAGE/masiton-tls-renew.timer" /etc/systemd/system/masiton-tls-renew.timer
systemctl daemon-reload
systemctl enable --now masiton-tls-renew.timer >/dev/null

echo "nginx: enabled=$(systemctl is-enabled nginx) active=$(systemctl is-active nginx)"
echo "timer: enabled=$(systemctl is-enabled masiton-tls-renew.timer) active=$(systemctl is-active masiton-tls-renew.timer)"
