#!/usr/bin/env bash
# CloudWatch Agent와 상태 확인 지표 수집을 설치한다 — M2-10.
#
# 사용: sudo ./cloudwatch-install.sh [스테이징 디렉터리]
#
# 스테이징에 필요한 파일:
#   amazon-cloudwatch-agent.json  health-metrics.sh
#   masiton-health-metrics.service  masiton-health-metrics.timer
#
# 재실행해도 결과가 같다.
set -euo pipefail

STAGE="${1:-/tmp/masiton-deploy}"
REGION="${AWS_REGION:-ap-northeast-2}"
OPT_DIR=/opt/masiton
AGENT_CONFIG=/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json

for f in amazon-cloudwatch-agent.json health-metrics.sh \
         masiton-health-metrics.service masiton-health-metrics.timer; do
  [ -f "$STAGE/$f" ] || { echo "스테이징에 $f 가 없다: $STAGE" >&2; exit 1; }
done

# agent는 로그 전송용이고 상태 지표 수집과는 독립적이다. 이 스크립트가
# CodeDeploy AfterInstall에서 실행되므로, 패키지 저장소 일시 장애로 배포 전체가
# 실패하지 않도록 agent 설치·기동 실패는 경고로 남기고 계속 진행한다. 아래
# 지표 수집 설치는 감지 경로 자체이므로 그대로 치명적으로 다룬다.
agent_ready=true
if ! rpm -q amazon-cloudwatch-agent >/dev/null 2>&1; then
  if ! dnf install -y amazon-cloudwatch-agent >/dev/null; then
    agent_ready=false
    echo '경고: amazon-cloudwatch-agent 설치에 실패했다. 로그 전송 없이 계속한다.' >&2
  fi
fi

if [ "$agent_ready" = true ]; then
  echo "agent: $(rpm -q amazon-cloudwatch-agent)"

  install -d -m 0755 "$(dirname "$AGENT_CONFIG")"
  install -m 0644 "$STAGE/amazon-cloudwatch-agent.json" "$AGENT_CONFIG"

  # fetch-config는 설정을 적용하고 agent를 재기동한다. -s로 즉시 시작한다.
  if /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
    -a fetch-config -m ec2 -s -c "file:$AGENT_CONFIG" >/dev/null; then
    echo "agent 상태: $(/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a status -m ec2 | tr -d '\n')"
  else
    echo '경고: amazon-cloudwatch-agent 설정 적용에 실패했다. 로그 전송 없이 계속한다.' >&2
  fi
fi

install -d -m 0755 "$OPT_DIR/bin"
install -m 0750 "$STAGE/health-metrics.sh" "$OPT_DIR/bin/health-metrics.sh"
install -m 0644 "$STAGE/masiton-health-metrics.service" /etc/systemd/system/masiton-health-metrics.service
install -m 0644 "$STAGE/masiton-health-metrics.timer" /etc/systemd/system/masiton-health-metrics.timer
systemctl daemon-reload
systemctl enable --now masiton-health-metrics.timer >/dev/null

# 첫 지표를 즉시 올려 알람이 INSUFFICIENT_DATA에서 벗어나게 한다.
AWS_REGION="$REGION" "$OPT_DIR/bin/health-metrics.sh"

echo "timer: enabled=$(systemctl is-enabled masiton-health-metrics.timer) active=$(systemctl is-active masiton-health-metrics.timer)"

