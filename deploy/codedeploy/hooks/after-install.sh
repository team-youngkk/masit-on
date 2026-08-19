#!/usr/bin/env bash
set -euo pipefail

ROOT=/opt/masiton/revision
TAG_FILE="$ROOT/revision.env"
[ -f "$TAG_FILE" ] || { echo 'CodeDeploy revision.env가 없다' >&2; exit 1; }
IFS= read -r image_tag < "$TAG_FILE"
case "$image_tag" in
  ''|*[!0-9a-f]*) echo 'CodeDeploy image tag가 소문자 SHA가 아니다' >&2; exit 1 ;;
esac
[ "${#image_tag}" -eq 40 ] || { echo 'CodeDeploy image tag 길이가 40자가 아니다' >&2; exit 1; }

chmod +x "$ROOT"/instance-bootstrap.sh "$ROOT"/redis-install.sh \
  "$ROOT"/app-deploy.sh "$ROOT"/nginx-install.sh "$ROOT"/runtime-health.sh \
  "$ROOT"/cloudwatch-install.sh "$ROOT"/health-metrics.sh
"$ROOT/instance-bootstrap.sh" "$image_tag" "$ROOT"
