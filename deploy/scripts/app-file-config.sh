#!/usr/bin/env bash
# 파일 설정을 데이터로만 파싱한다. stdout은 검증 완료된 비밀 아닌 KEY=VALUE뿐이다.
# 호출자는 실패 상태를 확인한 뒤 read/printf -v로 읽는다. source/eval 금지.
app_file_config_read() {
  python3 - "${APP_ENV_FILE:-/etc/masiton/app.env}" "${APP_SECRETS_DIR:-/etc/masiton/secrets}" "${1:-all}" <<'PY'
import os
import re
import stat
import sys
from pathlib import Path


def fail():
    # 입력의 키·경로에도 비밀값이 섞일 수 있으므로 원문을 출력하지 않는다.
    sys.exit("파일 설정 검증 실패: 필수 항목, 허용 목록, 형식 또는 소유권·권한을 확인한다")


def checked(path, uid, mode, directory=False):
    if not path.is_absolute() or any(p.is_symlink() for p in [path, *path.parents]):
        fail()
    info = path.lstat()
    kind = stat.S_ISDIR if directory else stat.S_ISREG
    if not kind(info.st_mode) or info.st_uid != uid or stat.S_IMODE(info.st_mode) != mode:
        fail()
    if not directory and (info.st_nlink != 1 or info.st_size > 65536):
        fail()
    # 부모를 통해 파일을 교체할 수 없어야 한다. sticky tmp는 테스트 경로에 허용한다.
    for parent in path.parents:
        st = parent.stat()
        if st.st_uid != 0 and not (parent == Path(sys.argv[2]) and st.st_uid == 1001 and stat.S_IMODE(st.st_mode) == 0o500):
            fail()
        if st.st_mode & 0o022 and not st.st_mode & stat.S_ISVTX:
            fail()


defaults = {
    "SPRING_PROFILES_ACTIVE": "prod",
    "KAKAO_BASE_URL": "https://dapi.kakao.com",
    "YOUTUBE_BASE_URL": "https://www.googleapis.com",
    "KAKAO_MOBILITY_ENABLED": "false",
    "KAKAO_MOBILITY_FREE_TIER_VERIFIED": "false",
    "MAIL_HEALTH_ENABLED": "true",
    "DEPENDENCY_HEALTH_COMPONENTS": "db,redis,mail",
    "AUTH_ALLOWED_ORIGINS": "https://masiton.click",
    "MEMBER_TRUSTED_PROXY_ADDRESSES": "127.0.0.1",
    "MEMBER_REVERSE_PROXY_ENABLED": "true",
    "AUTH_LOGIN_TRUSTED_PROXY_ADDRESSES": "127.0.0.1",
    "AUTH_LOGIN_REVERSE_PROXY_ENABLED": "true",
    "RESTAURANT_MAP_TRUSTED_PROXY_ADDRESSES": "127.0.0.1",
    "RESTAURANT_MAP_REVERSE_PROXY_ENABLED": "true",
    "YOUTUBE_WEBHOOK_CALLBACK_URL": "https://masiton.click/api/webhooks/youtube/channel-updates",
    "PASSWORD_RESET_PUBLIC_URL": "https://masiton.click/password-reset",
    "AI_WORKER_ENABLED": "false",
    "AI_WORKER_PROVIDER_QUOTA_LIMIT": "0",
    "AI_WORKER_APPLICATION_QUOTA_LIMIT": "0",
    "AI_WORKER_QUOTA_WINDOW": "P1D",
    "GEMINI_ENABLED": "false",
    "GEMINI_FREE_TIER_VERIFIED": "false",
    "GEMINI_PAID_BILLING_ENABLED": "false",
    "REQUIRE_SHARED_REDIS": "true",
    # 공인 endpoint를 써야 하는 환경은 app.env에 주소를 명시한다. 공인 주소는
    # 이 allowlist에 있는 값만 app-run/app-deploy가 통과시킨다.
    "REDIS_ALLOWED_PUBLIC_HOSTS": "",
}
required_env = {"DB_URL", "DB_USERNAME", "REDIS_HOST", "REDIS_PORT", "MAIL_HOST", "MAIL_PORT"}
required_secrets = {
    "spring.datasource.password", "spring.data.redis.password",
    "masiton.security.jwt.key-id", "masiton.security.jwt.private-key-pem",
    "masiton.security.jwt.public-key-pem", "masiton.member.action-mail.active-key-id",
    "masiton.member.action-mail.active-key", "masiton.member.action-mail.from-address",
    "masiton.member.rate-limit.secret", "spring.mail.username", "spring.mail.password",
}
optional_secrets = {
    "masiton.integration.kakao.rest-api-key", "masiton.integration.youtube.api-key",
    "masiton.integration.kakao-mobility.rest-api-key", "masiton.ai.provider.gemini.api-key",
    "masiton.ai.youtube-webhook.secret", "masiton.ai.temporary-input.active-key-id",
    "masiton.ai.temporary-input.active-key",
}
try:
    env_file, secret_dir = map(Path, sys.argv[1:3])
    # Docker volume의 구분 문자와 제어 문자는 호스트 경로에 허용하지 않는다.
    if not re.fullmatch(r"/[A-Za-z0-9_./-]+", str(secret_dir)):
        fail()
    secrets_only = sys.argv[3] == "secrets"
    if not secrets_only:
        checked(env_file, 0, 0o600)
    values = {}
    for line in ([] if secrets_only else env_file.read_text(encoding="utf-8").splitlines()):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or key not in required_env | defaults.keys() | {"SPRING_FLYWAY_TARGET", "SECRETS_DIR"} or key in values:
            fail()
        # 평문 KEY=VALUE만 허용한다. 셸 인용·확장·주석을 해석하지 않는다.
        if not value or not re.fullmatch(r"[A-Za-z0-9_./,:?=&%+@~-]+", value):
            fail()
        values[key] = value
    if secrets_only:
        values = dict(DB_URL="jdbc:postgresql://localhost:5432/validation", DB_USERNAME="validation", REDIS_HOST="10.0.0.1", REDIS_PORT="6379", MAIL_HOST="localhost", MAIL_PORT="587")
    if not required_env <= values.keys():
        fail()
    values = defaults | values
    if values["SPRING_PROFILES_ACTIVE"] != "prod":
        fail()
    # 자격 증명이나 임의 JDBC 옵션이 Docker 환경에 들어가는 것을 막는다.
    if not re.fullmatch(r"jdbc:postgresql://[A-Za-z0-9.-]+:[0-9]{1,5}/[A-Za-z0-9_-]+", values["DB_URL"]):
        fail()
    for key in ("MAIL_PORT", "REDIS_PORT"):
        if not values[key].isdigit() or not 1 <= int(values[key]) <= 65535:
            fail()
    for key in defaults:
        if defaults[key] in ("true", "false") and values[key] not in ("true", "false"):
            fail()
    for key in ("AI_WORKER_PROVIDER_QUOTA_LIMIT", "AI_WORKER_APPLICATION_QUOTA_LIMIT"):
        if not values[key].isdigit():
            fail()
    for key in ("KAKAO_BASE_URL", "YOUTUBE_BASE_URL", "YOUTUBE_WEBHOOK_CALLBACK_URL",
                "PASSWORD_RESET_PUBLIC_URL", "AUTH_ALLOWED_ORIGINS"):
        if values[key] != defaults[key]:
            fail()
    checked(secret_dir, 1001, 0o500, directory=True)
    names = set()
    for path in secret_dir.iterdir():
        if path.name not in required_secrets | optional_secrets and not re.fullmatch(r"masiton\.ai\.temporary-input\.keys\.[A-Za-z0-9_-]+", path.name):
            fail()
        checked(path, 1001, 0o400)
        if not path.read_bytes().strip():
            fail()
        names.add(path.name)
    if not required_secrets <= names:
        fail()
    for flag, secret in (("GEMINI_ENABLED", "masiton.ai.provider.gemini.api-key"),
                         ("KAKAO_MOBILITY_ENABLED", "masiton.integration.kakao-mobility.rest-api-key")):
        if values[flag] == "true" and secret not in names:
            fail()
    pair = {"masiton.ai.temporary-input.active-key-id", "masiton.ai.temporary-input.active-key"}
    if bool(names & pair) and not pair <= names:
        fail()
    if values["AI_WORKER_ENABLED"] == "true" and not pair <= names:
        fail()
    values["SECRETS_DIR"] = "/run/masiton/secrets"
    if not secrets_only:
        for key, value in values.items():
            print(f"{key}={value}")
except (OSError, UnicodeError, ValueError):
    fail()
PY
}

load_file_config() {
  local parsed key value
  # 별도 명령으로 실패를 전파한다. process substitution은 parser 실패를 숨긴다.
  parsed=$(app_file_config_read) || return 1
  while IFS='=' read -r key value; do
    printf -v "$key" '%s' "$value"
    export "$key"
  done <<< "$parsed"
  APP_SECRET_MOUNT="${APP_SECRETS_DIR:-/etc/masiton/secrets}"
}

validate_file_secrets() {
  app_file_config_read secrets
}
