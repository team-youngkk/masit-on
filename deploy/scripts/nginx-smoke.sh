#!/usr/bin/env bash
# Nginx 재기동 직후 제한 공개 경계를 실제 프록시 경유 요청으로 확인한다.
# 정상 데이터나 자격 증명은 사용하지 않고 상태 코드와 공통 오류 코드만 판정한다.
set -euo pipefail

BASE_URL="${NGINX_SMOKE_BASE_URL:-https://masiton.click}"
SMOKE_HOST="${NGINX_SMOKE_HOST:-masiton.click}"
SMOKE_ADDRESS="${NGINX_SMOKE_ADDRESS:-127.0.0.1}"
PUBLIC_ORIGIN="${NGINX_SMOKE_ORIGIN:-https://masiton.click}"
RESPONSE_FILE=$(mktemp)
trap 'rm -f "$RESPONSE_FILE"' EXIT

curl_request() {
  local method=$1
  local path=$2
  local body=${3-}
  local origin=${4-}
  local status
  local curl_args=(
    --silent --show-error --max-time 10 --noproxy '*'
    --resolve "${SMOKE_HOST}:443:${SMOKE_ADDRESS}"
    --output "$RESPONSE_FILE" --write-out '%{http_code}'
  )

  if [ "$method" = "HEAD" ]; then
    curl_args+=(--head)
  else
    curl_args+=(--request "$method")
  fi
  if [ -n "$body" ]; then
    curl_args+=(--header 'Content-Type: application/json' --data "$body")
  fi
  if [ -n "$origin" ]; then
    curl_args+=(--header "Origin: ${origin}")
  fi
  status=$(curl "${curl_args[@]}" "${BASE_URL}${path}")
  printf '%s' "$status"
}

is_api_routing_failure() {
  local status=$1
  [[ "$status" =~ ^(000|3[0-9][0-9]|502|503|504)$ ]]
}

assert_not_validation_gate() {
  local method=$1
  local path=$2
  local body=${3-}
  local status
  status=$(curl_request "$method" "$path" "$body")
  if [ "$status" = "401" ] || is_api_routing_failure "$status" \
      || grep -Fq 'VALIDATION_ACCESS_REQUIRED' "$RESPONSE_FILE"; then
    echo "Nginx 공개 경로 smoke 실패: ${method} ${path}가 backend API 응답에 도달하지 못했다 (HTTP ${status})." >&2
    exit 1
  fi
}

assert_not_validation_access_error() {
  local method=$1
  local path=$2
  local body=${3-}
  local origin=${4-}
  local status
  status=$(curl_request "$method" "$path" "$body" "$origin")
  if grep -Fq 'VALIDATION_ACCESS_REQUIRED' "$RESPONSE_FILE" \
      || is_api_routing_failure "$status"; then
    echo "Nginx 공개 경로 smoke 실패: ${method} ${path}가 Spring 응답에 도달하지 못했다 (HTTP ${status})." >&2
    exit 1
  fi
}

assert_error_code() {
  local expected_status=$1
  local expected_code=$2
  local method=$3
  local path=$4
  local body=${5-}
  local origin=${6-}
  local status
  status=$(curl_request "$method" "$path" "$body" "$origin")
  if [ "$status" != "$expected_status" ] || ! grep -Fq "\"code\":\"${expected_code}\"" "$RESPONSE_FILE"; then
    echo "Nginx API smoke 실패: ${method} ${path}가 ${expected_status} ${expected_code}를 반환하지 않았다 (HTTP ${status})." >&2
    exit 1
  fi
}

assert_validation_gate() {
  local method=$1
  local path=$2
  local body=${3-}
  local status
  status=$(curl_request "$method" "$path" "$body")
  if [ "$status" != "401" ] || ! grep -Fq 'VALIDATION_ACCESS_REQUIRED' "$RESPONSE_FILE"; then
    echo "Nginx 보호 경로 smoke 실패: ${method} ${path}의 검증 gate 응답이 아니다 (HTTP ${status})." >&2
    exit 1
  fi
}

# HEAD 응답에는 HTTP 의미상 본문이 없으므로 공통 오류 code 대신 gate의 401 상태를 판정한다.
assert_validation_gate_status() {
  local method=$1
  local path=$2
  local status
  status=$(curl_request "$method" "$path")
  if [ "$status" != "401" ]; then
    echo "Nginx 보호 경로 smoke 실패: ${method} ${path}가 검증 gate 401이 아니다 (HTTP ${status})." >&2
    exit 1
  fi
}

assert_status() {
  local expected=$1
  local method=$2
  local path=$3
  local body=${4-}
  local origin=${5-}
  local status
  status=$(curl_request "$method" "$path" "$body" "$origin")
  if [ "$status" != "$expected" ]; then
    echo "Nginx 경로 smoke 실패: ${method} ${path}는 HTTP ${expected}여야 하나 ${status}였다." >&2
    exit 1
  fi
}

opaque_segment=nginx-smoke-opaque
for path in \
  /api/restaurants \
  "/api/restaurants/${opaque_segment}" \
  /api/curations \
  "/api/curations/${opaque_segment}" \
  /api/creators \
  "/api/creators/${opaque_segment}" \
  "/api/creators/${opaque_segment}/restaurants" \
  "/api/creators/${opaque_segment}/videos"; do
  assert_not_validation_gate GET "$path"
done

# 공개 POST는 controller/service를 실행하지 않는 잘못된 JSON으로 Spring의 역직렬화 응답까지 도달해야 한다.
for path in \
  /api/auth/registrations \
  /api/auth/email-verifications \
  /api/auth/email-verifications/resend \
  /api/auth/password-resets/requests \
  /api/auth/password-resets/confirmations \
  /api/auth/tokens \
  /api/restaurants/course-routes \
  /api/restaurants/natural-language-search; do
  assert_not_validation_access_error POST "$path" '{'
done
# Refresh는 정상 Origin과 쿠키 누락을 사용해 부작용 없이 애플리케이션 고유 401을 구분한다.
assert_error_code 401 INVALID_REFRESH_TOKEN POST /api/auth/tokens/refresh '' "$PUBLIC_ORIGIN"

# 검증 세션 진입점도 정상 Origin에서 gate를 우회해야 한다. 빈 자격 증명은 Spring 400,
# 쿠키 없는 DELETE는 멱등하게 204이며 쿠키 값이나 비밀을 출력하지 않는다.
assert_not_validation_access_error POST /api/verification/sessions '{}' "$PUBLIC_ORIGIN"
assert_status 204 DELETE /api/verification/sessions '' "$PUBLIC_ORIGIN"

# Webhook은 자체 token/signature 검증으로 401일 수 있으므로 제한 공개 오류 코드가 아닌지만 판정한다.
assert_not_validation_access_error GET /api/webhooks/youtube/channel-updates
assert_not_validation_access_error POST /api/webhooks/youtube/channel-updates
assert_validation_gate_status HEAD /api/webhooks/youtube/channel-updates
assert_validation_gate PATCH /api/webhooks/youtube/channel-updates

# 통합 로그인 POST는 공개 경로다. 빈 자격 증명은 Spring 입력 검증 400이어도
# backend에 도달한 결과이므로 verification gate 401로 판정하지 않는다.
assert_not_validation_access_error POST /api/auth/tokens '{}'
assert_validation_gate GET /api/nginx-smoke-unknown
# 공개 location 또는 검증 세션 location에 먼저 매칭돼도 비허용 메서드는 공통 JSON gate로 돌아가야 한다.
assert_validation_gate PATCH /api/restaurants
assert_validation_gate GET /api/verification/sessions
assert_status 404 GET /internal
assert_status 404 GET /internal/health/live

echo "Nginx 제한 공개 smoke 검증을 통과했다."
