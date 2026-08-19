#!/usr/bin/env bash
set -euo pipefail

systemctl is-active --quiet masiton-backend.service
systemctl is-active --quiet masiton-frontend.service
