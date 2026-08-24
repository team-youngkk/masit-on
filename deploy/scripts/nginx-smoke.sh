#!/usr/bin/env bash
# Verify the public, gate-free proxy and the network-boundary invariants.
set -euo pipefail

BASE_URL="${NGINX_SMOKE_BASE_URL:-https://masiton.click}"
SMOKE_HOST="${NGINX_SMOKE_HOST:-masiton.click}"
SMOKE_ADDRESS="${NGINX_SMOKE_ADDRESS:-127.0.0.1}"
RESPONSE_FILE=$(mktemp)
trap 'rm -f "$RESPONSE_FILE"' EXIT

request() {
  local method=$1 path=$2 body=${3-} status
  local args=(--silent --show-error --max-time 10 --noproxy '*' \
    --resolve "${SMOKE_HOST}:443:${SMOKE_ADDRESS}" --output "$RESPONSE_FILE" \
    --write-out '%{http_code}' --request "$method")
  if [ -n "$body" ]; then args+=(--header 'Content-Type: application/json' --data "$body"); fi
  status=$(curl "${args[@]}" "${BASE_URL}${path}")
  printf '%s' "$status"
}

assert_reaches_backend() {
  local method=$1 path=$2 body=${3-} status
  status=$(request "$method" "$path" "$body")
  case "$status" in
    000|3*|502|503|504)
      echo "backend proxy failed: ${method} ${path} (HTTP ${status})" >&2; exit 1 ;;
  esac
}

assert_status() {
  local expected=$1 method=$2 path=$3 status
  status=$(request "$method" "$path")
  [ "$status" = "$expected" ] || {
    echo "unexpected status: ${method} ${path}, expected ${expected}, got ${status}" >&2
    exit 1
  }
}

for path in /api /api/restaurants /api/unknown-route; do
  assert_reaches_backend GET "$path"
done
assert_reaches_backend POST /api/auth/tokens '{}'
# The public root intentionally redirects to the canonical exploration page.
assert_status 307 GET /
assert_status 200 GET /restaurants

# Webhook GET/POST reach the application; other methods stop at Nginx.
assert_reaches_backend GET /api/webhooks/youtube/channel-updates
assert_reaches_backend POST /api/webhooks/youtube/channel-updates '{}'
assert_status 405 PATCH /api/webhooks/youtube/channel-updates

assert_status 404 GET /internal
assert_status 404 GET /internal/health/live
assert_status 404 POST /internal/health/ready

echo "Nginx gate-free public proxy smoke passed."
