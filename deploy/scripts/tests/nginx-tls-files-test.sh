#!/usr/bin/env bash
# 실제 OpenSSL 인증서를 이용한 입력 검사. OS 소유권만 격리된 stat으로 모사한다.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
NGINX_INSTALL="$ROOT/deploy/scripts/nginx-install.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

assert_contains() {
  local file="$1" expected="$2"
  grep -Fq -- "$expected" "$file" || {
    echo "기대 문자열이 없다: $expected ($file)" >&2
    exit 1
  }
}

assert_contains "$NGINX_INSTALL" "trap 'on_install_failure 129' HUP"
assert_contains "$NGINX_INSTALL" "trap 'on_install_failure 130' INT"
assert_contains "$NGINX_INSTALL" "trap 'on_install_failure 143' TERM"
assert_contains "$NGINX_INSTALL" "trap 'on_install_failure \$?' EXIT"
assert_contains "$NGINX_INSTALL" 'disable_acm_timer_for_files'
assert_contains "$NGINX_INSTALL" 'restore_acm_timer_state'

mkdir -p "$work/bin" "$work/stage" "$work/tls"
cp "$ROOT"/deploy/nginx/{nginx.conf,masiton.click.conf,00-masiton-upgrade-map.conf} "$work/stage/"
cp "$ROOT/deploy/scripts/nginx-smoke.sh" "$work/stage/"
openssl req -x509 -newkey rsa:2048 -nodes -subj /CN=masiton.click \
  -keyout "$work/tls/key.pem" -out "$work/tls/cert.pem" -days 2 >/dev/null 2>&1
openssl genpkey -algorithm RSA -out "$work/tls/other.pem" >/dev/null 2>&1
openssl req -new -key "$work/tls/key.pem" -subj /CN=masiton.click -out "$work/tls/request.pem" >/dev/null 2>&1
openssl x509 -req -in "$work/tls/request.pem" -signkey "$work/tls/key.pem" \
  -days 0 -out "$work/tls/expired.pem" >/dev/null 2>&1
cat > "$work/bin/stat" <<'SH'
#!/usr/bin/env bash
case "$2" in
  %u) echo "${TEST_OWNER:-0}" ;;
  %a) if [[ "${@: -1}" = *.pem ]]; then echo "${TEST_MODE:-600}"; else echo 755; fi ;;
  *) exit 1 ;;
esac
SH
cat > "$work/bin/forbidden" <<'SH'
#!/usr/bin/env bash
echo mutation >> "$TEST_MUTATIONS"
exit 99
SH
chmod +x "$work/bin/"*
for cmd in aws systemctl install dnf nginx; do cp "$work/bin/forbidden" "$work/bin/$cmd"; done
export PATH="$work/bin:$PATH" TEST_MUTATIONS="$work/mutations"
export DEPLOYMENT_ENV_FILE="$work/absent.env"
export TLS_CERT_FILE="$work/tls/cert.pem" TLS_KEY_FILE="$work/tls/key.pem"
unset TLS_SOURCE
run_check() { bash "$ROOT/deploy/scripts/nginx-install.sh" --check-config "$work/stage"; }
reject() {
  if run_check > "$work/output" 2>&1; then echo "예상한 거부 없음: $1" >&2; exit 1; fi
  grep -q "$1" "$work/output" || { cat "$work/output"; exit 1; }
}
run_check
TLS_KEY_FILE="$work/tls/other.pem" reject '일치하지 않는다'
TLS_CERT_FILE="$work/tls/expired.pem" reject '만료됐거나'
TEST_MODE=644 reject '0400/0600'
TEST_OWNER=1000 reject 'root 소유'
TLS_KEY_FILE='' reject '함께 지정'
TLS_CERT_FILE="$work/tls/missing.pem" reject '읽을 수 없다'
TLS_SOURCE=unknown reject 'files 또는 acm'
NGINX_TRUSTED_PROXY_CIDRS='127.0.0.1;return' reject 'CIDR 형식'
TLS_SOURCE=acm reject 'masiton-tls-renew.service'
cp "$ROOT"/deploy/nginx/masiton-tls-renew.{service,timer} "$work/stage/"
cp "$ROOT/deploy/scripts/tls-deploy-cert.sh" "$work/stage/"
TLS_SOURCE=acm run_check
[ ! -e "$TEST_MUTATIONS" ] || { echo '검사 중 외부 호출 또는 설치 발생' >&2; exit 1; }
echo 'PASS: files 기본값, 키 불일치, 만료, 권한, 소유권, 누락, 잘못된 입력, explicit acm, 검사 무변경'
