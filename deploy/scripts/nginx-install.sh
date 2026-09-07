#!/usr/bin/env bash
# 운영 EC2에 M2-08 Nginx와 TLS 구성을 설치한다. 재실행해도 결과가 같다.
#
# 저장소 파일을 인스턴스로 옮긴 스테이징 디렉터리를 인자로 받는다.
# 스테이징에는 다음 파일이 있어야 한다.
#   nginx.conf  masiton.click.conf  00-masiton-upgrade-map.conf
#   masiton-tls-renew.service  masiton-tls-renew.timer  tls-deploy-cert.sh  nginx-smoke.sh
#
# 사용: sudo ./nginx-install.sh [--check-config] [스테이징 디렉터리]
# TLS_SOURCE=files(기본)|acm. files는 TLS_CERT_FILE/TLS_KEY_FILE 또는 기존
# site의 단일 인증서 쌍을 사용한다. --check-config는 입력만 검사하며 설치하지 않는다.
set -euo pipefail

CHECK_CONFIG=no
if [ "${1:-}" = --check-config ]; then
  CHECK_CONFIG=yes
  shift
fi
[ "$#" -le 1 ] || { echo '사용: nginx-install.sh [--check-config] [STAGE]' >&2; exit 1; }
STAGE="${1:-/tmp/masiton-deploy}"
OPT_DIR=/opt/masiton
DEPLOYMENT_ENV_FILE="${DEPLOYMENT_ENV_FILE:-/etc/masiton/deployment.env}"
if [ -f "$DEPLOYMENT_ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$DEPLOYMENT_ENV_FILE"
  set +a
fi

fail() { echo "$*" >&2; exit 1; }
TLS_SOURCE="${TLS_SOURCE:-files}"
case "$TLS_SOURCE" in files|acm) ;; *) fail 'TLS_SOURCE는 files 또는 acm이어야 한다.' ;; esac
required_files=(nginx.conf masiton.click.conf 00-masiton-upgrade-map.conf nginx-smoke.sh)
if [ "$TLS_SOURCE" = acm ]; then
  required_files+=(masiton-tls-renew.service masiton-tls-renew.timer tls-deploy-cert.sh)
fi
for f in "${required_files[@]}"; do
  [ -f "$STAGE/$f" ] || { echo "스테이징에 $f 가 없다: $STAGE" >&2; exit 1; }
done

# 배포 전 검사와 실제 설치가 같은 입력 검증을 거친다. 파일 권한은 고치지
# 않고 거부한다. certbot 등의 symlink 경로를 유지해야 다음 갱신도 반영된다.
validate_tls_path() {
  local path="$1" kind="$2" resolved mode owner parent
  [[ "$path" =~ ^/[a-zA-Z0-9_./-]+$ ]] || fail 'TLS 파일은 안전한 절대 경로여야 한다.'
  [ -f "$path" ] && [ -r "$path" ] || fail "TLS 파일을 읽을 수 없다: $path"
  resolved=$(readlink -f -- "$path") || fail "TLS 경로를 해석할 수 없다: $path"
  owner=$(stat -Lc %u -- "$path")
  mode=$(stat -Lc %a -- "$path")
  [ "$owner" = 0 ] || fail "TLS 파일은 root 소유여야 한다: $path"
  if [ "$kind" = key ]; then
    (( (8#$mode & 0177) == 0 )) || fail "TLS 개인키는 0400/0600 권한이어야 한다: $path"
  else
    (( (8#$mode & 0022) == 0 )) || fail "TLS 인증서는 group/other 쓰기가 금지된다: $path"
  fi
  for parent in "$(dirname "$path")" "$(dirname "$resolved")"; do
    while :; do
      owner=$(stat -Lc %u -- "$parent")
      mode=$(stat -Lc %a -- "$parent")
      [ "$owner" = 0 ] && (( (8#$mode & 0022) == 0 )) || fail "안전하지 않은 TLS 상위 경로: $parent"
      [ "$parent" != / ] || break
      parent=$(dirname "$parent")
    done
  done
}

if [ "$TLS_SOURCE" = files ]; then
  command -v openssl >/dev/null || fail 'openssl이 필요하다.'
  if [ -z "${TLS_CERT_FILE:-}" ] && [ -z "${TLS_KEY_FILE:-}" ]; then
    existing_site=/etc/nginx/conf.d/masiton.click.conf
    [ -f "$existing_site" ] || fail '첫 설치는 TLS_CERT_FILE과 TLS_KEY_FILE을 지정해야 한다.'
    TLS_CERT_FILE=$(awk '$1 == "ssl_certificate" {sub(/;$/, "", $2); print $2}' "$existing_site" | sort -u)
    TLS_KEY_FILE=$(awk '$1 == "ssl_certificate_key" {sub(/;$/, "", $2); print $2}' "$existing_site" | sort -u)
  fi
  [ -n "${TLS_CERT_FILE:-}" ] && [ -n "${TLS_KEY_FILE:-}" ] || fail 'TLS_CERT_FILE과 TLS_KEY_FILE을 함께 지정해야 한다.'
  validate_tls_path "$TLS_CERT_FILE" cert
  validate_tls_path "$TLS_KEY_FILE" key
  openssl x509 -in "$TLS_CERT_FILE" -noout -checkend 0 >/dev/null 2>&1 || fail 'TLS 인증서가 만료됐거나 유효하지 않다.'
  not_before=$(openssl x509 -in "$TLS_CERT_FILE" -noout -startdate)
  not_before_epoch=$(date -d "${not_before#notBefore=}" +%s) || fail 'TLS 시작일을 해석할 수 없다.'
  [ "$not_before_epoch" -le "$(date +%s)" ] || fail 'TLS 인증서가 아직 유효하지 않다.'
  openssl x509 -in "$TLS_CERT_FILE" -noout -checkhost masiton.click 2>/dev/null | grep -q 'does match certificate' || fail 'TLS 인증서가 masiton.click과 일치하지 않는다.'
  cert_pubkey=$(openssl x509 -in "$TLS_CERT_FILE" -noout -pubkey | openssl pkey -pubin -outform DER | openssl dgst -sha256) || fail 'TLS 인증서 공개키를 읽을 수 없다.'
  key_pubkey=$(openssl pkey -in "$TLS_KEY_FILE" -passin pass: -pubout -outform DER 2>/dev/null | openssl dgst -sha256) || fail 'TLS 개인키가 유효하지 않거나 암호화돼 있다.'
  [ "$cert_pubkey" = "$key_pubkey" ] || fail 'TLS 인증서와 개인키가 일치하지 않는다.'
  # 경로 이외의 TLS 구성을 조용히 수용하지 않는다.
  grep -q 'ssl_certificate /etc/nginx/tls/masiton.click.fullchain.pem;' "$STAGE/masiton.click.conf" || fail '스테이징 TLS 인증서 자리표시자가 없다.'
  grep -q 'ssl_certificate_key /etc/nginx/tls/masiton.click.key;' "$STAGE/masiton.click.conf" || fail '스테이징 TLS 개인키 자리표시자가 없다.'
fi
trusted_cidrs="${NGINX_TRUSTED_PROXY_CIDRS:-127.0.0.1}"
IFS=',' read -r -a trusted_proxy_cidrs <<< "$trusted_cidrs"
for cidr in "${trusted_proxy_cidrs[@]}"; do
  case "$cidr" in *[!0-9a-fA-F:./]*) fail "신뢰 proxy CIDR 형식이 잘못됐다: $cidr" ;; esac
done
if [ "$CHECK_CONFIG" = yes ]; then
  echo "Nginx 입력 검증 완료: TLS_SOURCE=$TLS_SOURCE (설치/nginx -t 미실행)"
  exit 0
fi

if ! command -v nginx >/dev/null 2>&1; then
  dnf install -y nginx >/dev/null
fi
echo "nginx: $(nginx -v 2>&1)"

if [ "$TLS_SOURCE" = acm ]; then
  install -d -m 0755 "$OPT_DIR/bin"
  install -m 0750 "$STAGE/tls-deploy-cert.sh" "$OPT_DIR/bin/tls-deploy-cert.sh"
fi

SITE_CONF=/etc/nginx/conf.d/masiton.click.conf
UPGRADE_MAP_CONF=/etc/nginx/conf.d/00-masiton-upgrade-map.conf
NGINX_CONF=/etc/nginx/nginx.conf
REAL_IP_CONF=/etc/nginx/conf.d/10-masiton-real-ip.conf
BASIC_AUTH_DROPIN=/etc/systemd/system/nginx.service.d/10-masiton-basic-auth.conf
OLD_AUTH_MAP=/etc/nginx/conf.d/01-masiton-api-auth-map.conf
TLS_RENEW_TIMER=masiton-tls-renew.timer
TIMER_STATE_CAPTURED=no
TIMER_WAS_ENABLED=no
TIMER_WAS_ACTIVE=no

# Nginx와 백엔드가 함께 gate-free 릴리즈로 전환된다. 새 설정의 구문은
# 유효하지만 smoke가 실패하는 경우를 포함해, 설치 중 바꾼 Nginx 산출물을
# 직전 상태로 되돌릴 수 있어야 한다. 이전 설정이 검증 gate를 포함한
# 이 스크립트는 app-deploy의 rollback trap 안에서 실행된다. 구버전이면
# nginx-install의 Nginx 복구와 app-deploy의 paired app rollback이 함께 수행된다.
ROLLBACK_DIR=$(mktemp -d)
backup_or_ignore() {
  local source="$1" target="$2"
  if [ -f "$source" ]; then
    mkdir -p "$(dirname "$target")"
    cp -p "$source" "$target"
  fi
}
backup_or_ignore "$SITE_CONF" "$ROLLBACK_DIR/site.conf"
backup_or_ignore "$UPGRADE_MAP_CONF" "$ROLLBACK_DIR/upgrade-map.conf"
backup_or_ignore "$NGINX_CONF" "$ROLLBACK_DIR/nginx.conf"
backup_or_ignore "$REAL_IP_CONF" "$ROLLBACK_DIR/real-ip.conf"
backup_or_ignore "$BASIC_AUTH_DROPIN" "$ROLLBACK_DIR/basic-auth-dropin.conf"
backup_or_ignore "$OLD_AUTH_MAP" "$ROLLBACK_DIR/auth-map.conf"

capture_acm_timer_state() {
  [ "$TLS_SOURCE" = files ] || return 0
  TIMER_STATE_CAPTURED=yes
  if systemctl is-enabled --quiet "$TLS_RENEW_TIMER"; then
    TIMER_WAS_ENABLED=yes
  fi
  if systemctl is-active --quiet "$TLS_RENEW_TIMER"; then
    TIMER_WAS_ACTIVE=yes
  fi
}

disable_acm_timer_for_files() {
  [ "$TLS_SOURCE" = files ] || return 0
  capture_acm_timer_state
  if [ "$TIMER_WAS_ACTIVE" = yes ]; then
    systemctl stop "$TLS_RENEW_TIMER"
  fi
  if [ "$TIMER_WAS_ENABLED" = yes ]; then
    systemctl disable "$TLS_RENEW_TIMER" >/dev/null
  fi
}

restore_acm_timer_state() {
  [ "$TIMER_STATE_CAPTURED" = yes ] || return 0
  if [ "$TIMER_WAS_ENABLED" = yes ]; then
    systemctl enable "$TLS_RENEW_TIMER" >/dev/null || {
      echo "ACM 갱신 timer enable 상태 복구에 실패했다: $TLS_RENEW_TIMER" >&2
    }
  else
    systemctl disable "$TLS_RENEW_TIMER" >/dev/null || true
  fi
  if [ "$TIMER_WAS_ACTIVE" = yes ]; then
    systemctl start "$TLS_RENEW_TIMER" >/dev/null || {
      echo "ACM 갱신 timer active 상태 복구에 실패했다: $TLS_RENEW_TIMER" >&2
    }
  else
    systemctl stop "$TLS_RENEW_TIMER" >/dev/null || true
  fi
}

restore_or_remove() {
  local backup="$1" target="$2"
  if [ -f "$backup" ]; then
    install -d "$(dirname "$target")"
    install -m 0644 "$backup" "$target"
  elif [ -e "$target" ] || [ -L "$target" ]; then
    rm -f "$target"
  fi
}

on_install_failure() {
  local status="${1:-$?}"
  [ "${INSTALL_ROLLBACK_ACTIVE:-no}" = yes ] || return "$status"
  INSTALL_ROLLBACK_ACTIVE=no
  trap - ERR EXIT INT TERM HUP
  echo "Nginx gate-free 전환에 실패했다. 직전 Nginx 구성을 복구한다." >&2
  restore_or_remove "$ROLLBACK_DIR/site.conf" "$SITE_CONF" || true
  restore_or_remove "$ROLLBACK_DIR/upgrade-map.conf" "$UPGRADE_MAP_CONF" || true
  restore_or_remove "$ROLLBACK_DIR/nginx.conf" "$NGINX_CONF" || true
  restore_or_remove "$ROLLBACK_DIR/real-ip.conf" "$REAL_IP_CONF" || true
  restore_or_remove "$ROLLBACK_DIR/basic-auth-dropin.conf" "$BASIC_AUTH_DROPIN" || true
  restore_or_remove "$ROLLBACK_DIR/auth-map.conf" "$OLD_AUTH_MAP" || true
  systemctl daemon-reload || true
  systemctl restart nginx || echo "Nginx 복구 후 재시작도 실패했다. paired rollback과 수동 복구가 필요하다." >&2
  restore_acm_timer_state || true
  rm -rf "$ROLLBACK_DIR" || true
  exit "$status"
}
INSTALL_ROLLBACK_ACTIVE=yes
trap 'on_install_failure $?' ERR
trap 'on_install_failure 129' HUP
trap 'on_install_failure 130' INT
trap 'on_install_failure 143' TERM
trap 'on_install_failure $?' EXIT

disable_acm_timer_for_files


# 인증서를 먼저 내려받아야 Nginx가 기동한다. ssl_certificate 파일이 없으면
# 설정 검사부터 실패한다.
if [ "$TLS_SOURCE" = acm ]; then
  AWS_REGION="${AWS_REGION:-ap-northeast-2}" "$OPT_DIR/bin/tls-deploy-cert.sh"
fi

install -m 0644 "$STAGE/00-masiton-upgrade-map.conf" "$UPGRADE_MAP_CONF"
if [ "$TLS_SOURCE" = files ]; then
  sed -e "s|ssl_certificate /etc/nginx/tls/masiton.click.fullchain.pem;|ssl_certificate $TLS_CERT_FILE;|g" \
      -e "s|ssl_certificate_key /etc/nginx/tls/masiton.click.key;|ssl_certificate_key $TLS_KEY_FILE;|g" \
      "$STAGE/masiton.click.conf" > "$ROLLBACK_DIR/rendered-site.conf"
  install -m 0644 "$ROLLBACK_DIR/rendered-site.conf" "$SITE_CONF"
else
  install -m 0644 "$STAGE/masiton.click.conf" "$SITE_CONF"
fi
rm -f "$BASIC_AUTH_DROPIN" "$OLD_AUTH_MAP"

# 최상위 설정을 저장소 산출물로 교체한다. 배포판 기본 설정에는
# /usr/share/nginx/html을 서비스하는 server 블록이 있어 도메인 밖 접근에
# 기본 페이지가 응답한다. 첫 교체 때 원본을 한 번만 남긴다.
if [ ! -f /etc/nginx/nginx.conf.masiton-orig ]; then
  cp -p /etc/nginx/nginx.conf /etc/nginx/nginx.conf.masiton-orig
fi
install -m 0644 "$STAGE/nginx.conf" "$NGINX_CONF"

# 직접 접속 구조에서는 proxy가 없으므로 기본값 127.0.0.1만 신뢰한다. 외부가
# 보낸 X-Forwarded-For를 신뢰하지 않도록 별도 proxy CIDR은 배포 입력으로만 허용한다.
{
  echo '# Generated by nginx-install.sh; do not edit on the instance.'
  echo 'real_ip_header X-Forwarded-For;'
  echo 'real_ip_recursive on;'
  trusted_cidrs="${NGINX_TRUSTED_PROXY_CIDRS:-127.0.0.1}"
  IFS=',' read -r -a trusted_proxy_cidrs <<< "$trusted_cidrs"
  for cidr in "${trusted_proxy_cidrs[@]}"; do
    [ -n "$cidr" ] || continue
    case "$cidr" in
      *[!0-9a-fA-F:./]*) echo "신뢰 proxy CIDR 형식이 잘못됐다: $cidr" >&2; exit 1 ;;
    esac
    printf 'set_real_ip_from %s;\n' "$cidr"
  done
} > "$REAL_IP_CONF"

nginx -t
systemctl daemon-reload
systemctl enable nginx >/dev/null
systemctl restart nginx
if [ "$(systemctl is-active nginx)" != "active" ]; then
  echo "Nginx 재시작이 active 상태로 확인되지 않았다." >&2
  exit 1
fi

bash "$STAGE/nginx-smoke.sh"

if [ "$TLS_SOURCE" = acm ]; then
install -m 0644 "$STAGE/masiton-tls-renew.service" /etc/systemd/system/masiton-tls-renew.service
install -m 0644 "$STAGE/masiton-tls-renew.timer" /etc/systemd/system/masiton-tls-renew.timer
systemctl daemon-reload
systemctl enable --now masiton-tls-renew.timer >/dev/null
fi

INSTALL_ROLLBACK_ACTIVE=no
trap - ERR EXIT INT TERM HUP
TIMER_STATE_CAPTURED=no
rm -rf "$ROLLBACK_DIR"

echo "nginx: enabled=$(systemctl is-enabled nginx) active=$(systemctl is-active nginx)"
if [ "$TLS_SOURCE" = acm ]; then
  echo "timer: enabled=$(systemctl is-enabled masiton-tls-renew.timer) active=$(systemctl is-active masiton-tls-renew.timer)"
else
  echo 'TLS files: 기존 인증서와 ACM 갱신 timer를 비활성화했다.'
fi
