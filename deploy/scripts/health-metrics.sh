#!/usr/bin/env bash
# 상태 확인 결과를 CloudWatch 지표로 올린다 — M2-10.
#
# `/internal/**`은 인터넷에서 차단되므로(ADR-WEB-003) 외부 감시 서비스로는 볼 수
# 없다. 그래서 인스턴스 안에서 1분 주기로 호출해 지표로 올리고, 알람이 그 지표의
# 연속 실패를 본다.
#
# 지표는 정상 1 / 실패 0이다. 알람은 `1 미만이 연속 3회`로 걸어 "상태 확인 연속
# 3회 실패"와 "저장소 연결 실패 연속 3회"를 각각 표현한다.
set -uo pipefail

REGION="${AWS_REGION:-ap-northeast-2}"
NAMESPACE="${METRIC_NAMESPACE:-masiton/health}"
BASE="${HEALTH_BASE:-http://127.0.0.1:8080}"
instance_id=$(curl -sS -m 3 -H "X-aws-ec2-metadata-token: $(curl -sS -m 3 -X PUT \
  -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' http://169.254.169.254/latest/api/token)" \
  http://169.254.169.254/latest/meta-data/instance-id)

# IMDSv2가 강제돼 있어 토큰을 먼저 받아야 한다. 실패하면 지표에 차원을 붙일 수
# 없으므로 중단한다. 값이 없는 채로 올리면 알람이 다른 차원을 보게 된다.
if [ -z "$instance_id" ]; then
  echo "instance-id를 읽지 못했다" >&2
  exit 1
fi

# 정상 1 / 실패 0. jq가 없으므로 python3으로 판정한다.
probe() {
  local path="$1" component="$2"
  local body status
  body=$(curl -sS -m 5 "$BASE/internal/health/$path" 2>/dev/null)
  status=$?
  if [ "$status" -ne 0 ] || [ -z "$body" ]; then
    echo 0
    return
  fi
  python3 -c "
import json, sys
try:
    d = json.loads(sys.argv[1])
except Exception:
    print(0); sys.exit()
component = sys.argv[2]
if component:
    value = d.get('components', {}).get(component, {}).get('status')
else:
    value = d.get('status')
print(1 if value == 'UP' else 0)
" "$body" "$component"
}

live=$(probe live "")
ready=$(probe ready "")
db=$(probe dependencies db)
redis=$(probe dependencies redis)

# Nginx에 **설치된** 인증서의 남은 일수를 올린다. ACM의 DaysToExpiry는 ACM이 가진
# 인증서만 보므로, ACM이 갱신했는데 EC2 재배포가 실패한 경우를 잡지 못한다.
# 계획 4.1절이 감시하려는 위험이 정확히 그 경우다.
CERT="${TLS_CERT:-/etc/nginx/tls/masiton.click.fullchain.pem}"
cert_days=""
if [ -f "$CERT" ]; then
  not_after=$(openssl x509 -in "$CERT" -noout -enddate 2>/dev/null | cut -d= -f2)
  if [ -n "$not_after" ]; then
    cert_days=$(( ( $(date -d "$not_after" +%s) - $(date +%s) ) / 86400 ))
  fi
fi

metric_data=(
  "MetricName=HealthLive,Value=$live,Unit=None,Dimensions=[{Name=InstanceId,Value=$instance_id}]"
  "MetricName=HealthReady,Value=$ready,Unit=None,Dimensions=[{Name=InstanceId,Value=$instance_id}]"
  "MetricName=DependencyPostgres,Value=$db,Unit=None,Dimensions=[{Name=InstanceId,Value=$instance_id}]"
  "MetricName=DependencyRedis,Value=$redis,Unit=None,Dimensions=[{Name=InstanceId,Value=$instance_id}]"
)
# 인증서를 읽지 못했으면 지표를 올리지 않는다. 0을 올리면 만료 임박으로 오탐하고,
# 임의값을 올리면 실제 만료를 가린다. 지표가 끊기면 알람이 breaching으로 잡는다.
if [ -n "$cert_days" ]; then
  metric_data+=("MetricName=InstalledCertificateDaysToExpiry,Value=$cert_days,Unit=Count,Dimensions=[{Name=InstanceId,Value=$instance_id}]")
fi

aws cloudwatch put-metric-data --region "$REGION" --namespace "$NAMESPACE" \
  --metric-data "${metric_data[@]}"

echo "live=$live ready=$ready postgres=$db redis=$redis cert_days=${cert_days:-미확인}"
