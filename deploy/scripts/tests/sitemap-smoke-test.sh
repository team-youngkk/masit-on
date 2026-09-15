#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
SMOKE_SCRIPT="$REPOSITORY_ROOT/deploy/scripts/sitemap-smoke.sh"
TEST_ROOT="$(mktemp -d)"
FAKE_BIN="$TEST_ROOT/bin"
mkdir -p "$FAKE_BIN"
trap 'rm -rf "$TEST_ROOT"' EXIT

cat > "$FAKE_BIN/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -euo pipefail

body_file=''
header_file=''
url=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --dump-header|--output|--write-out|--max-time|--noproxy|--resolve|--header)
      [ "$#" -ge 2 ] || exit 2
      if [ "$1" = --dump-header ]; then
        header_file="$2"
      elif [ "$1" = --output ]; then
        body_file="$2"
      fi
      shift 2
      ;;
    --silent|--show-error)
      shift
      ;;
    *)
      url="$1"
      shift
      ;;
  esac
done

[ -n "$body_file" ] && [ -n "$header_file" ] && [ -n "$url" ]

status=200
content_type='text/html'
body=''
extra_header=''
if [[ "$url" == */robots.txt ]]; then
  content_type='text/plain'
  case "$FIXTURE" in
    happy|meta-robots|meta-googlebot|header-noindex)
      body=$'User-agent: Googlebot\nDisallow: /\nAllow: /sitemap.xml\nAllow: /restaurants\nUser-agent: *\nDisallow: /\nSitemap: https://masiton.click/sitemap.xml'
      ;;
    meta-none|header-googlebot-none|header-otherbot)
      body=$'User-agent: Googlebot\nDisallow: /\nAllow: /sitemap.xml\nAllow: /restaurants\nUser-agent: *\nDisallow: /\nSitemap: https://masiton.click/sitemap.xml'
      ;;
    googlebot-blocked)
      body=$'User-agent: Googlebot\nDisallow: /\nUser-agent: *\nAllow: /\nSitemap: https://masiton.click/sitemap.xml'
      ;;
    wildcard-blocked)
      body=$'User-agent: *\nDisallow: /\nSitemap: https://masiton.click/sitemap.xml'
      ;;
    encoded-path-blocked)
      body=$'User-agent: Googlebot\nDisallow: /restaurants/a%2Fb$\nAllow: /restaurants/\nSitemap: https://masiton.click/sitemap.xml'
      ;;
    *)
      echo "알 수 없는 fixture: $FIXTURE" >&2
      exit 2
      ;;
  esac
elif [[ "$url" == */sitemap.xml ]]; then
  content_type='application/xml'
  if [ "$FIXTURE" = encoded-path-blocked ]; then
    body='<?xml version="1.0"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>https://masiton.click/restaurants/a%2Fb</loc></url></urlset>'
  else
    body='<?xml version="1.0"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>https://masiton.click/restaurants</loc></url><url><loc>https://masiton.click/restaurants/1</loc></url></urlset>'
  fi
elif [[ "$url" == */restaurants* ]]; then
  case "$FIXTURE" in
    meta-robots)
      body='<html><head><meta name="robots" content="NOINDEX, follow"></head><body>ok</body></html>'
      ;;
    meta-googlebot)
      body='<html><head><meta content="noindex, follow" NAME="GoogleBot"></head><body>ok</body></html>'
      ;;
    meta-none)
      body='<html><head><meta name="robots" content="NONE, follow"></head><body>ok</body></html>'
      ;;
    header-noindex)
      body='<html><head><meta name="description" content="ok"></head><body>ok</body></html>'
      extra_header=$'x-robots-tag: NoIndex, follow'
      ;;
    header-googlebot-none)
      body='<html><head><meta name="description" content="ok"></head><body>ok</body></html>'
      extra_header=$'X-Robots-Tag: GoogleBot: none'
      ;;
    header-otherbot)
      body='<html><head><meta name="description" content="ok"></head><body>ok</body></html>'
      extra_header=$'X-Robots-Tag: otherbot: noindex'
      ;;
    *)
      body='<html><head><meta name="robots" content="index, follow"></head><body>ok</body></html>'
      ;;
  esac
else
  status=404
  body='not found'
fi

printf 'HTTP/1.1 %s OK\nContent-Type: %s\n%s\n' "$status" "$content_type" "$extra_header" > "$header_file"
printf '%s' "$body" > "$body_file"
printf '%s' "$status"
FAKE_CURL
chmod 0755 "$FAKE_BIN/curl"

run_case() {
  local fixture="$1" expected="$2" expected_message="$3" output status
  if output=$(PATH="$FAKE_BIN:$PATH" \
      FIXTURE="$fixture" \
      SITEMAP_SMOKE_BASE_URL=https://masiton.click \
      SITEMAP_SMOKE_HOST=masiton.click \
      SITEMAP_SMOKE_ADDRESS=127.0.0.1 \
      SITEMAP_SMOKE_SITE_URL=https://masiton.click \
      bash "$SMOKE_SCRIPT" 2>&1); then
    status=0
  else
    status=$?
  fi

  if [ "$expected" = pass ]; then
    [ "$status" -eq 0 ] || {
      echo "fixture가 통과해야 한다: $fixture" >&2
      echo "$output" >&2
      exit 1
    }
  else
    [ "$status" -ne 0 ] || {
      echo "fixture가 실패해야 한다: $fixture" >&2
      exit 1
    }
    grep -Fq "$expected_message" <<< "$output" || {
      echo "실패 사유가 기대와 다르다: $fixture" >&2
      echo "$output" >&2
      exit 1
    }
  fi
}

run_case happy pass ''
run_case googlebot-blocked fail 'robots.txt disallows Googlebot access'
run_case wildcard-blocked fail 'robots.txt disallows Googlebot access'
run_case encoded-path-blocked fail 'robots.txt disallows Googlebot access'
run_case meta-robots fail 'page contains robots noindex'
run_case meta-googlebot fail 'page contains googlebot noindex'
run_case meta-none fail 'page contains robots none'
run_case header-noindex fail 'response contains X-Robots-Tag noindex'
run_case header-googlebot-none fail 'response contains X-Robots-Tag none'
run_case header-otherbot pass ''

echo 'Sitemap smoke fixture contract: PASS'
