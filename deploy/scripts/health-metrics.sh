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
# fleet 집계 지표의 범위를 가르는 이름이다. CodeDeploy alarm은 asg만 본다.
# 기존 단일 EC2에 이 스크립트를 설치할 때는 다른 값을 넘겨야 두 환경이 섞이지 않는다.
ENVIRONMENT="${METRIC_ENVIRONMENT:-asg}"
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
  # 위 지표는 InstanceId 차원을 가져 ASG처럼 인스턴스가 계속 바뀌는 환경에서는
  # 알람 대상으로 고정할 수 없다. 차원 없는 같은 값을 함께 올려 fleet 전체를
  # 하나의 지표로 본다. 차원 집합이 다르면 CloudWatch가 별개 지표로 다루므로
  # 위 InstanceId 지표와 섞이지 않는다. Minimum으로 집계하면 한 대라도 0이면
  # 0이 되어 "어느 인스턴스든 Redis가 끊겼다"를 표현한다.
  #
  # Redis만 올린다. Postgres는 ready 그룹에 있어 이미 ALB가 target을 드레인하지만
  # Redis는 ready에 없어 어느 경로로도 감지되지 않는다(ADR-DEPLOY-005 5절).
  #
  # 차원을 완전히 비우면 이 계정·리전의 어떤 인스턴스가 올린 값이든 같은 지표에
  # 섞인다. 기존 단일 EC2가 이 스크립트를 받게 되면 그 인스턴스의 동거 Redis를
  # 종료하는 순간 ASG의 Redis는 멀쩡한데도 배포가 차단된다. 인스턴스가 바뀌어도
  # 변하지 않는 환경 이름으로 범위를 좁힌다.
  "MetricName=FleetDependencyRedis,Value=$redis,Unit=None,Dimensions=[{Name=Environment,Value=$ENVIRONMENT}]"
)
# 인증서를 읽지 못했으면 지표를 올리지 않는다. 0을 올리면 만료 임박으로 오탐하고,
# 임의값을 올리면 실제 만료를 가린다. 지표가 끊기면 알람이 breaching으로 잡는다.
if [ -n "$cert_days" ]; then
  metric_data+=("MetricName=InstalledCertificateDaysToExpiry,Value=$cert_days,Unit=Count,Dimensions=[{Name=InstanceId,Value=$instance_id}]")
fi

# 전송 실패를 삼키지 않는다. FleetDependencyRedis가 올라가지 않으면 CodeDeploy
# alarm은 결측을 notBreaching으로 다뤄 영원히 OK로 남고, "Redis 장애 시 배포가
# 차단된다"는 계약이 아무도 모르는 사이에 거짓이 된다. 권한 누락이나 네트워크
# 문제는 systemd 단위 실패와 배포 실패로 즉시 드러나야 한다.
put_status=0
aws cloudwatch put-metric-data --region "$REGION" --namespace "$NAMESPACE" \
  --metric-data "${metric_data[@]}" || put_status=$?

echo "live=$live ready=$ready postgres=$db redis=$redis cert_days=${cert_days:-미확인}"

if [ "$put_status" -ne 0 ]; then
  echo "CloudWatch 지표 전송에 실패했다 (exit $put_status). 감지 경로가 동작하지 않는다." >&2
  exit "$put_status"
fi
