#!/usr/bin/env bash
# 검증 참여자 제한 공개용 htpasswd를 tmpfs에 렌더링한다 — M2-11.
#
# Nginx 기동 직전에 실행한다(nginx.service drop-in의 ExecStartPre).
# /run은 tmpfs라 재기동하면 사라지므로 매 기동마다 다시 만든다. 자격 증명이
# 루트 볼륨과 볼륨 스냅샷에 남지 않게 하려는 것이다(NFR-SECURITY-003).
#
# 해시는 openssl의 apr1을 쓴다. httpd-tools(htpasswd)를 새로 설치하지 않으려는
# 선택이며 Nginx가 apr1을 지원한다.
set -euo pipefail

REGION="${AWS_REGION:-ap-northeast-2}"
USER_PARAMETER="${BASIC_AUTH_USER_PARAMETER:-/masiton/access/basic-auth-username}"
PASSWORD_PARAMETER="${BASIC_AUTH_PASSWORD_PARAMETER:-/masiton/access/basic-auth-password}"
RUN_DIR="${RUN_DIR:-/run/masiton}"
HTPASSWD="${HTPASSWD:-$RUN_DIR/htpasswd}"
# Nginx worker가 읽어야 한다. 파일 소유자를 nginx로 두고 0400으로 잠근다.
NGINX_USER="${NGINX_USER:-nginx}"

install -d -m 0755 "$RUN_DIR"

username=$(aws ssm get-parameter --region "$REGION" --name "$USER_PARAMETER" \
  --query 'Parameter.Value' --output text | tr -d '\r\n')
password=$(aws ssm get-parameter --region "$REGION" --name "$PASSWORD_PARAMETER" --with-decryption \
  --query 'Parameter.Value' --output text | tr -d '\r\n')

if [ -z "$username" ] || [ "$username" = "None" ] || [ -z "$password" ] || [ "$password" = "None" ]; then
  echo "Basic Auth 자격 증명을 Parameter Store에서 읽지 못했다" >&2
  exit 1
fi

umask 077
hash=$(printf %s "$password" | openssl passwd -apr1 -stdin)
unset password

printf '%s:%s\n' "$username" "$hash" > "$HTPASSWD"
chown "$NGINX_USER" "$HTPASSWD"
chmod 0400 "$HTPASSWD"

echo "htpasswd 렌더링 완료: $HTPASSWD (사용자 $username)"
