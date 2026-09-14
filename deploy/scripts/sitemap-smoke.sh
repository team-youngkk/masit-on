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

python3 - "$robots_body" "$SMOKE_SITE_URL" "$url_list" "$USER_AGENT" <<'PY'
import re
import sys
from urllib.parse import unquote, urlparse

robots_path, site_url, url_list_path, user_agent = sys.argv[1:]
site_url = site_url.rstrip('/')
product = user_agent.split('/', 1)[0].lower()


def parse_groups(lines):
    groups = []
    agents = []
    rules = []

    def flush():
        if agents:
            groups.append((tuple(agents), tuple(rules)))
        agents.clear()
        rules.clear()

    for raw_line in lines:
        line = raw_line.split('#', 1)[0].strip()
        if not line:
            flush()
            continue
        if ':' not in line:
            continue
        directive, value = line.split(':', 1)
        directive = directive.strip().lower()
        value = value.strip()
        if directive == 'user-agent':
            if rules:
                flush()
            if value:
                agents.append(value.lower())
        elif directive in {'allow', 'disallow'} and agents:
            rules.append((directive == 'allow', value))
    flush()
    return groups


def selected_rules(groups):
    specific = [
        group for group in groups
        if any(agent != '*' and (agent == product or agent in product) for agent in group[0])
    ]
    if specific:
        most_specific = max(
            len(agent)
            for agents, _ in specific
            for agent in agents
            if agent != '*' and (agent == product or agent in product)
        )
        return [
            rule
            for agents, rules in specific
            if any(
                agent != '*' and len(agent) == most_specific
                and (agent == product or agent in product)
                for agent in agents
            )
            for rule in rules
        ]
    return [
        rule
        for agents, rules in groups
        if '*' in agents
        for rule in rules
    ]


def can_fetch(rules, url):
    parsed = urlparse(unquote(url))
    target = parsed.path or '/'
    if parsed.params:
        target += f';{parsed.params}'
    if parsed.query:
        target += f'?{parsed.query}'

    matches = []
    for allow, path in rules:
        if not path:
            if not allow:
                continue
            matches.append((0, allow))
            continue
        if '*' in path:
            expression = '^' + re.escape(path).replace(r'\*', '.*')
            if path.endswith('$'):
                expression = expression[:-2] + '$'
            if not re.match(expression, target):
                continue
        elif not target.startswith(path):
            continue
        matches.append((len(path.rstrip('$')), allow))

    if not matches:
        return True
    return max(matches, key=lambda match: (match[0], match[1]))[1]


with open(robots_path, encoding='utf-8') as robots_file:
    rules = selected_rules(parse_groups(robots_file.read().splitlines()))

urls = [f'{site_url}/sitemap.xml']
with open(url_list_path, encoding='utf-8') as url_list:
    urls.extend(line.strip() for line in url_list if line.strip())

for url in urls:
    if not can_fetch(rules, url):
        raise SystemExit(f'robots.txt disallows Googlebot access: {url}')
PY

url_count=0
while IFS= read -r loc; do
  url_count=$((url_count + 1))
  path="${loc#"${SMOKE_SITE_URL}"}"
  body_file="$WORK_DIR/url-${url_count}.html"
  header_file="$WORK_DIR/url-${url_count}.headers"
  status=$(fetch "${BASE_URL}${path}" "$body_file" "$header_file")
  assert_status 200 "$status" "GET ${path}"

  python3 - "$body_file" "$header_file" "$loc" <<'PY'
import re
import sys
from html.parser import HTMLParser

body_path, header_path, url = sys.argv[1:]
body = open(body_path, encoding='utf-8').read()

headers = open(header_path, encoding='iso-8859-1').read()
for line in headers.splitlines():
    if ':' not in line:
        continue
    name, value = line.split(':', 1)
    if name.strip().lower() == 'x-robots-tag' and re.search(r'\bnoindex\b', value, flags=re.IGNORECASE):
        raise SystemExit(f'sitemap URL response contains X-Robots-Tag noindex: {url}')


class RobotsMetaParser(HTMLParser):
    def handle_starttag(self, tag, attrs):
        if tag.lower() != 'meta':
            return
        attributes = {name.lower(): value or '' for name, value in attrs}
        name = attributes.get('name', '').strip().lower()
        content = attributes.get('content', '')
        if name in {'robots', 'googlebot'} and re.search(r'\bnoindex\b', content, flags=re.IGNORECASE):
            raise SystemExit(f'sitemap URL page contains {name} noindex: {url}')


RobotsMetaParser().feed(body)
PY
done < "$url_list"

echo "Sitemap smoke passed: ${url_count} public URLs returned 200 without noindex."
