#!/usr/bin/env bash
# Verify the public sitemap, robots declaration, and every sitemap URL.
set -euo pipefail

BASE_URL="${SITEMAP_SMOKE_BASE_URL:-https://masiton.click}"
SMOKE_HOST="${SITEMAP_SMOKE_HOST:-masiton.click}"
SMOKE_ADDRESS="${SITEMAP_SMOKE_ADDRESS:-127.0.0.1}"
SMOKE_SITE_URL="${SITEMAP_SMOKE_SITE_URL:-https://masiton.click}"
BASE_URL="${BASE_URL%/}"
SMOKE_SITE_URL="${SMOKE_SITE_URL%/}"
USER_AGENT='Googlebot/2.1 (+http://www.google.com/bot.html)'
WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

curl_args=(
  --silent
  --show-error
  --max-time 10
  --noproxy '*'
  --resolve "${SMOKE_HOST}:443:${SMOKE_ADDRESS}"
  --header "User-Agent: ${USER_AGENT}"
)

fetch() {
  local url=$1 body_file=$2 header_file=$3
  curl "${curl_args[@]}" \
    --dump-header "$header_file" \
    --output "$body_file" \
    --write-out '%{http_code}' \
    "$url"
}

assert_status() {
  local expected=$1 actual=$2 description=$3
  [ "$actual" = "$expected" ] || {
    echo "unexpected status: ${description}, expected ${expected}, got ${actual}" >&2
    exit 1
  }
}

assert_header() {
  local header_file=$1 pattern=$2 description=$3
  grep -Eiq "$pattern" "$header_file" || {
    echo "unexpected header: ${description}" >&2
    exit 1
  }
}

robots_body="$WORK_DIR/robots.txt"
robots_headers="$WORK_DIR/robots.headers"
robots_status=$(fetch "${BASE_URL}/robots.txt" "$robots_body" "$robots_headers")
assert_status 200 "$robots_status" 'GET /robots.txt'
grep -Fq "Sitemap: ${SMOKE_SITE_URL}/sitemap.xml" "$robots_body" || {
  echo 'robots.txt does not declare the expected sitemap' >&2
  exit 1
}

sitemap_body="$WORK_DIR/sitemap.xml"
sitemap_headers="$WORK_DIR/sitemap.headers"
sitemap_status=$(fetch "${BASE_URL}/sitemap.xml" "$sitemap_body" "$sitemap_headers")
assert_status 200 "$sitemap_status" 'GET /sitemap.xml'
assert_header "$sitemap_headers" '^Content-Type:[[:space:]]*application/xml([;[:space:]]|$)' 'sitemap content type'

url_list="$WORK_DIR/urls.txt"
python3 - "$sitemap_body" "$SMOKE_SITE_URL" > "$url_list" <<'PY'
import sys
import xml.etree.ElementTree as ET
from urllib.parse import urlparse

sitemap_path, site_url = sys.argv[1:]
site_url = site_url.rstrip('/')
site_origin = urlparse(site_url)

try:
    root = ET.parse(sitemap_path).getroot()
except (ET.ParseError, OSError) as exc:
    raise SystemExit(f'invalid sitemap XML: {exc}')

locs = []
for element in root.iter():
    if element.tag.rsplit('}', 1)[-1] != 'loc':
        continue
    if not element.text or not element.text.strip():
        raise SystemExit('sitemap contains an empty loc')
    locs.append(element.text.strip())

if not locs:
    raise SystemExit('sitemap contains no loc elements')
if len(locs) != len(set(locs)):
    raise SystemExit('sitemap contains duplicate loc elements')

for loc in locs:
    parsed = urlparse(loc)
    if (parsed.scheme, parsed.netloc) != (site_origin.scheme, site_origin.netloc):
        raise SystemExit(f'sitemap loc is outside the canonical origin: {loc}')
    if parsed.path != '/restaurants' and not parsed.path.startswith('/restaurants/'):
        raise SystemExit(f'sitemap loc is not a public restaurant URL: {loc}')
    print(loc)
PY

url_count=0
while IFS= read -r loc; do
  url_count=$((url_count + 1))
  path="${loc#"${SMOKE_SITE_URL}"}"
  body_file="$WORK_DIR/url-${url_count}.html"
  header_file="$WORK_DIR/url-${url_count}.headers"
  status=$(fetch "${BASE_URL}${path}" "$body_file" "$header_file")
  assert_status 200 "$status" "GET ${path}"

  python3 - "$body_file" "$loc" <<'PY'
import re
import sys

body_path, url = sys.argv[1:]
body = open(body_path, encoding='utf-8').read()

for tag in re.findall(r'<meta\b[^>]*>', body, flags=re.IGNORECASE):
    name = re.search(r'\bname\s*=\s*(["\'])(.*?)\1', tag, flags=re.IGNORECASE)
    content = re.search(r'\bcontent\s*=\s*(["\'])(.*?)\1', tag, flags=re.IGNORECASE)
    if name and name.group(2).strip().lower() == 'robots' and content:
        if re.search(r'\bnoindex\b', content.group(2), flags=re.IGNORECASE):
            raise SystemExit(f'sitemap URL points to a noindex page: {url}')
PY
done < "$url_list"

echo "Sitemap smoke passed: ${url_count} public URLs returned 200 without noindex."
