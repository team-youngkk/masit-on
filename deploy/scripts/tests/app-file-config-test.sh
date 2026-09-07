#!/usr/bin/env bash
# Linux에서 sudo bash deploy/scripts/tests/app-file-config-test.sh 로 실행한다.
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo 'Linux root 권한이 필요한 파일 권한 테스트' >&2; exit 1; }
SOURCE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST_ROOT=$(mktemp -d /tmp/masiton-file-test.XXXXXX)
trap 'rm -rf -- "$TEST_ROOT"' EXIT
export APP_ENV_FILE="$TEST_ROOT/app.env" APP_SECRETS_DIR="$TEST_ROOT/secrets"
export TEST_ROOT
unset APP_CONFIG_SOURCE
mkdir "$TEST_ROOT/bin"
for script in app-run.sh app-secrets-render.sh app-file-config.sh; do
  sed 's/\r$//' "$SOURCE/$script" > "$TEST_ROOT/$script"
  bash -n "$TEST_ROOT/$script"
done
cat > "$TEST_ROOT/bin/aws" <<'SH'
#!/usr/bin/env bash
touch "$TEST_ROOT/aws-called"
exit 98
SH
cat > "$TEST_ROOT/bin/docker" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$TEST_ROOT/docker-args"
env > "$TEST_ROOT/docker-env"
SH
chmod 700 "$TEST_ROOT/bin/"*
export PATH="$TEST_ROOT/bin:$PATH"
# 실행 경계만 fixture로 바꾼 복사본을 실행한다. 실제 Docker·이미지 참조는 건드리지 않는다.
sed -i "s|/usr/bin/docker|$TEST_ROOT/bin/docker|; s|/opt/masiton/etc/\${component}.image|$TEST_ROOT/backend.image|" "$TEST_ROOT/app-run.sh"
printf '%s\n' 'example/backend@sha256:fixture' > "$TEST_ROOT/backend.image"

fixture() {
  python3 - <<'PY'
import os
from pathlib import Path
p = Path(os.environ['APP_ENV_FILE'])
p.write_text('DB_URL=jdbc:postgresql://10.0.0.2:5432/masiton\nDB_USERNAME=masiton\nREDIS_HOST=10.0.0.3\nREDIS_PORT=6379\nREDIS_ALLOWED_PUBLIC_HOSTS=43.201.7.105\nMAIL_HOST=smtp.example.com\nMAIL_PORT=587\nSECRETS_DIR=/etc/masiton/secrets\n')
p.chmod(0o600)
d = Path(os.environ['APP_SECRETS_DIR'])
d.mkdir(exist_ok=True)
d.chmod(0o500)
os.chown(d, 1001, 1001)
for name in ('spring.datasource.password', 'spring.data.redis.password', 'masiton.security.jwt.key-id',
             'masiton.security.jwt.private-key-pem', 'masiton.security.jwt.public-key-pem',
             'masiton.member.action-mail.active-key-id', 'masiton.member.action-mail.active-key',
             'masiton.member.action-mail.from-address', 'masiton.member.rate-limit.secret',
             'spring.mail.username', 'spring.mail.password'):
    f = d / name
    f.write_text('file-mode-sensitive-sentinel')
    f.chmod(0o400)
    os.chown(f, 1001, 1001)
PY
}
check() { bash "$TEST_ROOT/app-run.sh" --check-config; }
reject() {
  if check > "$TEST_ROOT/output" 2>&1; then
    echo "거부해야 할 설정을 허용했다: $1" >&2; exit 1
  fi
  ! grep -q 'sensitive-sentinel' "$TEST_ROOT/output"
  [ ! -e "$TEST_ROOT/aws-called" ]
}
fixture
rm "$TEST_ROOT/backend.image"
check
# parser 및 secret-only 공개 인터페이스를 실제로 호출한다.
source "$TEST_ROOT/app-file-config.sh"
load_file_config
[ "$REDIS_HOST" = 10.0.0.3 ] && [ "$REDIS_PORT" = 6379 ] && [ "$REDIS_ALLOWED_PUBLIC_HOSTS" = 43.201.7.105 ]
[ "$APP_SECRET_MOUNT" = "$APP_SECRETS_DIR" ]
[ "$SECRETS_DIR" = /run/masiton/secrets ]
validate_file_secrets
before=$(find "$APP_SECRETS_DIR" -type f -exec sha256sum {} \; | sort)
bash "$TEST_ROOT/app-secrets-render.sh"
[ "$before" = "$(find "$APP_SECRETS_DIR" -type f -exec sha256sum {} \; | sort)" ]
printf '%s\n' 'example/backend@sha256:fixture' > "$TEST_ROOT/backend.image"
bash "$TEST_ROOT/app-run.sh" backend
grep -Fxq "$APP_SECRETS_DIR:/run/masiton/secrets:ro" "$TEST_ROOT/docker-args"
grep -Fxq 'SECRETS_DIR=/run/masiton/secrets' "$TEST_ROOT/docker-env"
grep -Fxq 'PASSWORD_RESET_PUBLIC_URL=https://masiton.click/password-reset' "$TEST_ROOT/docker-env"
! grep -q 'sensitive-sentinel' "$TEST_ROOT/docker-args" "$TEST_ROOT/docker-env"
[ ! -e "$TEST_ROOT/aws-called" ]

sed -i '/DB_URL=/d' "$APP_ENV_FILE"; reject missing-env; fixture
printf '%s\n' 'DB_PASSWORD=file-mode-sensitive-sentinel' >> "$APP_ENV_FILE"; reject secret-env; fixture
printf '%s\n' 'UNKNOWN=file-mode-sensitive-sentinel' >> "$APP_ENV_FILE"; reject unknown-env; fixture
printf '%s\n' 'DB_USERNAME=duplicate' >> "$APP_ENV_FILE"; reject duplicate-env; fixture
printf '%s\n' 'DB_USERNAME=$(touch /tmp/sensitive-sentinel)' >> "$APP_ENV_FILE"; reject shell-expansion; fixture
sed -i 's|jdbc:postgresql://.*|jdbc:postgresql://10.0.0.2:5432/masiton?password=sensitive-sentinel|' "$APP_ENV_FILE"; reject jdbc-secret; fixture
chmod 644 "$APP_ENV_FILE"; reject env-permissions; fixture
chown 1001 "$APP_ENV_FILE"; reject env-owner; chown 0 "$APP_ENV_FILE"; fixture
rm "$APP_SECRETS_DIR/spring.mail.password"; reject missing-secret; fixture
chmod 440 "$APP_SECRETS_DIR/spring.mail.password"; reject secret-permissions; fixture
chown 0 "$APP_SECRETS_DIR/spring.mail.password"; reject secret-owner; fixture
chmod 700 "$APP_SECRETS_DIR"; reject directory-permissions; fixture
printf ' ' > "$APP_SECRETS_DIR/spring.mail.password"; reject blank-secret; fixture
touch "$APP_SECRETS_DIR/unknown.property"; reject unknown-secret; rm "$APP_SECRETS_DIR/unknown.property"
mv "$APP_SECRETS_DIR/spring.mail.password" "$TEST_ROOT/password"
ln -s "$TEST_ROOT/password" "$APP_SECRETS_DIR/spring.mail.password"; reject symlink-secret
rm "$APP_SECRETS_DIR/spring.mail.password"; fixture
printf '%s\n' 'GEMINI_ENABLED=true' >> "$APP_ENV_FILE"; reject enabled-provider-missing-key; fixture
sed -i 's/10.0.0.3/8.8.8.8/' "$APP_ENV_FILE"; reject unapproved-public-redis; fixture
sed -i 's/10.0.0.3/43.201.7.105/' "$APP_ENV_FILE"; check; fixture
sed -i 's/10.0.0.3/127.0.0.1/' "$APP_ENV_FILE"; reject loopback-shared-redis; fixture
APP_CONFIG_SOURCE=unknown reject unknown-source
# 명시적 legacy 모드만 AWS에 도달한다. 실패 상태는 최상위까지 전파된다.
if APP_CONFIG_SOURCE=ssm bash "$TEST_ROOT/app-run.sh" --check-config > "$TEST_ROOT/output" 2>&1; then
  echo 'legacy AWS 실패를 삼켰다' >&2; exit 1
fi
[ -f "$TEST_ROOT/aws-called" ]
echo 'App file config runtime: PASS'
