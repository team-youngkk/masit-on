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

aws cloudwatch put-metric-data --region "$REGION" --namespace "$NAMESPACE" \
  --metric-data \
  "MetricName=HealthLive,Value=$live,Unit=None,Dimensions=[{Name=InstanceId,Value=$instance_id}]" \
  "MetricName=HealthReady,Value=$ready,Unit=None,Dimensions=[{Name=InstanceId,Value=$instance_id}]" \
  "MetricName=DependencyPostgres,Value=$db,Unit=None,Dimensions=[{Name=InstanceId,Value=$instance_id}]" \
  "MetricName=DependencyRedis,Value=$redis,Unit=None,Dimensions=[{Name=InstanceId,Value=$instance_id}]"

echo "live=$live ready=$ready postgres=$db redis=$redis"
