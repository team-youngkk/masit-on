#!/usr/bin/env bash
# ACM exportable 인증서를 내보내 Nginx에 반영한다 — M2-08.
#
# ACM은 만료 45일 전에 인증서를 자동 갱신하지만, 갱신본을 EC2에 다시 내보내는 것은
# 자동이 아니다(계획 4.1절). 이 스크립트를 systemd timer로 매일 실행해 그 공백을 메운다.
# 갱신되지 않았으면 파일이 같아 아무 것도 하지 않고 끝난다.
#
# 재실행해도 결과가 같다. 내용이 바뀐 경우에만 파일을 교체하고 Nginx를 reload한다.
set -euo pipefail

REGION="${AWS_REGION:-ap-northeast-2}"
ARN_PARAMETER="${TLS_ARN_PARAMETER:-/masiton/tls/certificate-arn}"
PASSPHRASE_PARAMETER="${TLS_PASSPHRASE_PARAMETER:-/masiton/tls/export-passphrase}"
TLS_DIR="${TLS_DIR:-/etc/nginx/tls}"
FULLCHAIN="$TLS_DIR/masiton.click.fullchain.pem"
KEY="$TLS_DIR/masiton.click.key"

# 중간 산출물은 tmpfs에만 둔다. 개인키 평문과 내보내기 암호가 루트 볼륨과
# 볼륨 스냅샷에 남지 않게 하기 위한 것이다(NFR-SECURITY-003).
install -d -m 0700 /run/masiton
work=$(mktemp -d /run/masiton/tls.XXXXXXXX)
trap 'rm -rf "$work"' EXIT
umask 077

certificate_arn=$(aws ssm get-parameter --region "$REGION" --name "$ARN_PARAMETER" \
  --query 'Parameter.Value' --output text)
aws ssm get-parameter --region "$REGION" --name "$PASSPHRASE_PARAMETER" --with-decryption \
  --query 'Parameter.Value' --output text | tr -d '\r\n' > "$work/passphrase"

# 암호를 명령행 인자로 넘기지 않는다. 같은 인스턴스의 `ps`에서 읽힌다.
aws acm export-certificate --region "$REGION" \
  --certificate-arn "$certificate_arn" \
  --passphrase "fileb://$work/passphrase" > "$work/export.json"

python3 - "$work" <<'PY'
import json
import pathlib
import sys

work = pathlib.Path(sys.argv[1])
export = json.loads((work / "export.json").read_text())
(work / "fullchain.pem").write_text(export["Certificate"].rstrip("\n") + "\n" + export["CertificateChain"].rstrip("\n") + "\n")
(work / "key.enc.pem").write_text(export["PrivateKey"])
PY

# 내보낸 개인키는 내보내기 암호로 암호화된 PEM이다. Nginx가 암호 입력 없이 읽도록 푼다.
openssl pkey -in "$work/key.enc.pem" -passin "file:$work/passphrase" -out "$work/key.pem"

# 인증서와 개인키가 짝인지 확인한다. 어긋난 쌍을 배포하면 Nginx가 기동하지 못한다.
cert_pubkey=$(openssl x509 -in "$work/fullchain.pem" -noout -pubkey | openssl sha256)
key_pubkey=$(openssl pkey -in "$work/key.pem" -pubout | openssl sha256)
if [ "$cert_pubkey" != "$key_pubkey" ]; then
  echo "인증서와 개인키가 짝이 아니다. 배포하지 않는다." >&2
  exit 1
fi

install -d -m 0755 "$TLS_DIR"
if [ -f "$FULLCHAIN" ] && [ -f "$KEY" ] \
  && cmp -s "$work/fullchain.pem" "$FULLCHAIN" && cmp -s "$work/key.pem" "$KEY"; then
  echo "변경 없음: $(openssl x509 -in "$FULLCHAIN" -noout -enddate)"
  exit 0
fi

install -m 0644 "$work/fullchain.pem" "$FULLCHAIN"
install -m 0600 "$work/key.pem" "$KEY"
echo "인증서 반영: $(openssl x509 -in "$FULLCHAIN" -noout -serial -enddate | tr '\n' ' ')"

# Nginx가 아직 설정되지 않은 첫 실행에서는 reload를 시도하지 않는다.
if systemctl is-enabled nginx >/dev/null 2>&1; then
  nginx -t
  systemctl reload nginx
  echo "nginx reload 완료"
else
  echo "nginx가 아직 활성화되지 않아 reload를 건너뛴다"
fi
