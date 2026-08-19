#!/usr/bin/env bash
# 새 인스턴스가 트래픽을 받을 수 있는지 판정하는 최소 런타임 계약.
set -euo pipefail

BASE_URL="${HEALTH_BASE_URL:-http://127.0.0.1:8080}"
FRONTEND_URL="${FRONTEND_HEALTH_URL:-http://127.0.0.1:3000/}"

curl -fsS -m 5 "$BASE_URL/internal/health/live" >/dev/null
curl -fsS -m 5 "$BASE_URL/internal/health/ready" >/dev/null

dependencies=$(mktemp)
trap 'rm -f "$dependencies"' EXIT
status=$(curl -sS -m 5 -o "$dependencies" -w '%{http_code}' \
  "$BASE_URL/internal/health/dependencies")
[ "$status" = 200 ] || { echo "dependency health HTTP $status" >&2; exit 1; }
python3 - "$dependencies" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as response:
    components = json.load(response).get("components", {})
for name in ("db", "redis", "mail"):
    if components.get(name, {}).get("status") != "UP":
        raise SystemExit(f"dependency is not UP: {name}")
PY
curl -fsS -m 5 -o /dev/null "$FRONTEND_URL"
echo "runtime health: backend and frontend are ready"
