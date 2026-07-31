#!/usr/bin/env bash
# 관리자 계정을 생성한다 — M2-12.
#
# 사용: sudo ./admin-account-create.sh
#
# `/masiton/admin/accounts/{loginId}/password`에 등록된 파라미터를 훑어 계정을
# 만든다. loginId를 스크립트에 박지 않으므로 계정을 추가할 때 파라미터만 넣으면 된다.
#
# 비밀번호 평문은 운영자가 Parameter Store에 넣고 이 스크립트가 인스턴스 안에서만
# 읽는다. 명령행 인자로 넘기지 않으며(`htpasswd -i`가 표준 입력에서 읽는다) 어디에도
# 출력하지 않는다. API 계약 9절의 "운영 명령으로 생성"에 해당한다.
#
# **생성 후 운영자가 password 파라미터를 삭제한다.** DB에 해시가 있으면 평문을 더
# 보관할 이유가 없고, 남겨두면 비밀번호 저장소가 하나 더 생기는 셈이다. 삭제를 이
# 스크립트가 하지 않는 이유는 인스턴스 역할이 Parameter Store 읽기 전용이기
# 때문이다(`masiton-parameter-store-read`). 쓰기 권한을 주면 앱 호스트가 비밀값을
# 지울 수 있게 되어 경계가 넓어진다.
#
# 이미 있는 loginId는 건너뛴다. 재실행해도 결과가 같다.
set -euo pipefail

REGION="${AWS_REGION:-ap-northeast-2}"
PARAMETER_PREFIX="${ADMIN_ACCOUNT_PREFIX:-/masiton/admin/accounts/}"
# 애플리케이션 BCryptPasswordEncoder 기본 강도와 같아야 로그인이 성립한다.
BCRYPT_COST="${BCRYPT_COST:-10}"
# 로그인 API가 요구하는 최소 길이. 미달이면 계정은 만들어지지만 로그인 요청이
# 인증 단계 전에 400으로 막혀 원인을 찾기 어렵다.
MIN_PASSWORD_LENGTH=12

command -v htpasswd >/dev/null 2>&1 || dnf install -y httpd-tools >/dev/null

password_names=$(aws ssm get-parameters-by-path --region "$REGION" \
  --path "$PARAMETER_PREFIX" --recursive --with-decryption \
  --query "Parameters[?ends_with(Name, '/password')].Name" --output text)

if [ -z "$password_names" ]; then
  echo "생성할 계정이 없다. $PARAMETER_PREFIX 아래에 password 파라미터를 등록한다." >&2
  exit 1
fi

export PGPASSWORD
PGPASSWORD=$(aws ssm get-parameter --region "$REGION" --name /masiton/db/password \
  --with-decryption --query 'Parameter.Value' --output text)
db_host=$(aws ssm get-parameter --region "$REGION" --name /masiton/db/url \
  --query 'Parameter.Value' --output text | sed 's#jdbc:postgresql://##; s#:5432/masiton##')
db_user=$(aws ssm get-parameter --region "$REGION" --name /masiton/db/username \
  --query 'Parameter.Value' --output text)

for name in $password_names; do
  login_id=${name#"$PARAMETER_PREFIX"}
  login_id=${login_id%/password}

  password=$(aws ssm get-parameter --region "$REGION" --name "$name" --with-decryption \
    --query 'Parameter.Value' --output text | tr -d '\r\n')

  if [ "${#password}" -lt "$MIN_PASSWORD_LENGTH" ]; then
    echo "  $login_id -> 건너뜀 (비밀번호가 ${MIN_PASSWORD_LENGTH}자 미만이라 로그인이 400으로 막힌다)"
    unset password
    continue
  fi

  hash=$(printf %s "$password" | htpasswd -nbBC "$BCRYPT_COST" -i x 2>/dev/null | cut -d: -f2)
  unset password
  case "$hash" in
    \$2[aby]\$*) ;;
    *) echo "  $login_id -> 실패 (BCrypt 형식이 아니다)" >&2; exit 1 ;;
  esac

  result=$(psql -h "$db_host" -U "$db_user" -d masiton -t -A -v ON_ERROR_STOP=1 \
    -v loginId="$login_id" -v passwordHash="$hash" <<'SQL'
INSERT INTO admin_account (id, login_id, password_hash, role, active, created_at, updated_at)
VALUES (gen_random_uuid(), :'loginId', :'passwordHash', 'ADMIN', true, now(), now())
ON CONFLICT (login_id) DO NOTHING
RETURNING 'created';
SQL
)
  unset hash
  if [ "$result" = "created" ]; then
    echo "  $login_id -> 생성"
  else
    echo "  $login_id -> 이미 존재 (변경하지 않았다)"
  fi
done

echo "----- admin_account 현황 (해시 미출력) -----"
psql -h "$db_host" -U "$db_user" -d masiton -t -A -F ' | ' \
  -c "select login_id, role, active, left(password_hash, 7) || '...', created_at from admin_account order by created_at"
unset PGPASSWORD
