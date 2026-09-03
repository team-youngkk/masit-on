#!/usr/bin/env bash
# 이전 배포가 설치한 외부 관측성 구성만 정리한다.
#
# 새 인스턴스와 기존 인스턴스에서 모두 실행할 수 있어야 하므로, 이미 제거된
# unit·패키지·파일이 없어도 성공한다. 앱·Nginx·SSM·Redis 구성은 건드리지 않는다.
set -euo pipefail

for unit in \
  masiton-health-metrics.timer \
  masiton-health-metrics.service \
  amazon-cloudwatch-agent.service; do
  systemctl disable --now "$unit" >/dev/null 2>&1 || true
done

if rpm -q amazon-cloudwatch-agent >/dev/null 2>&1; then
  if command -v dnf >/dev/null 2>&1; then
    dnf remove -y amazon-cloudwatch-agent >/dev/null
  elif command -v yum >/dev/null 2>&1; then
    yum remove -y amazon-cloudwatch-agent >/dev/null
  else
    rpm -e amazon-cloudwatch-agent
  fi
fi

rm -f \
  /etc/systemd/system/masiton-health-metrics.timer \
  /etc/systemd/system/masiton-health-metrics.service \
  /opt/masiton/bin/health-metrics.sh
rm -rf /opt/aws/amazon-cloudwatch-agent
systemctl daemon-reload

echo '이전 외부 관측성 구성 정리 완료'
