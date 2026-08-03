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

# M2-11 Basic Auth의 systemd 사전 실행 경계를 제거한다. 이전 설치의 drop-in이
# 남아 있으면 삭제한 렌더러를 계속 호출해 Nginx 재기동이 실패한다.
rm -f /etc/systemd/system/nginx.service.d/10-masiton-basic-auth.conf

# 인증서를 먼저 내려받아야 Nginx가 기동한다. ssl_certificate 파일이 없으면
# 설정 검사부터 실패한다.
AWS_REGION="${AWS_REGION:-ap-northeast-2}" "$OPT_DIR/bin/tls-deploy-cert.sh"

install -m 0644 "$STAGE/00-masiton-upgrade-map.conf" /etc/nginx/conf.d/00-masiton-upgrade-map.conf
rm -f /etc/nginx/conf.d/01-masiton-api-auth-map.conf
install -m 0644 "$STAGE/masiton.click.conf" /etc/nginx/conf.d/masiton.click.conf

# 최상위 설정을 저장소 산출물로 교체한다. 배포판 기본 설정에는
# /usr/share/nginx/html을 서비스하는 server 블록이 있어 도메인 밖 접근에
# 기본 페이지가 응답한다. 첫 교체 때 원본을 한 번만 남긴다.
if [ ! -f /etc/nginx/nginx.conf.masiton-orig ]; then
  cp -p /etc/nginx/nginx.conf /etc/nginx/nginx.conf.masiton-orig
fi
install -m 0644 "$STAGE/nginx.conf" /etc/nginx/nginx.conf

nginx -t
# 이전 Basic Auth drop-in을 제거했으므로 systemd 상태도 다시 읽는다.
systemctl daemon-reload
systemctl enable nginx >/dev/null
systemctl restart nginx

install -m 0644 "$STAGE/masiton-tls-renew.service" /etc/systemd/system/masiton-tls-renew.service
install -m 0644 "$STAGE/masiton-tls-renew.timer" /etc/systemd/system/masiton-tls-renew.timer
systemctl daemon-reload
systemctl enable --now masiton-tls-renew.timer >/dev/null

echo "nginx: enabled=$(systemctl is-enabled nginx) active=$(systemctl is-active nginx)"
echo "timer: enabled=$(systemctl is-enabled masiton-tls-renew.timer) active=$(systemctl is-active masiton-tls-renew.timer)"
