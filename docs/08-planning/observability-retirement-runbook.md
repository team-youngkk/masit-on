---
status: Ready
decision_date: 2026-09-03
owners:
  - 이우람
related_documents:
  - ../07-adr/quality/obs-002-local-operations-without-cloudwatch.md
  - ../07-adr/quality/obs-001-logging-observability.md
  - ../../infra/production/README.md
  - m2-provisioning-record.md
---

# CloudWatch 관측성 폐기 런북

## 1. 목적과 범위

현재 단일 EC2 운영에서 사용하지 않는 CloudWatch Agent·로그 그룹·custom metric·alarm과 SNS·Slack notifier 경로를 확인하고 정리한다. 이 문서는 실행 절차만 기록하며, 실제 AWS 삭제는 코드 변경 PR이 `develop`에 반영되고 운영 담당자가 인벤토리를 검토한 뒤 수행한다.

EC2 재배포 시 [`observability-cleanup.sh`](../../deploy/scripts/observability-cleanup.sh)가 기존 호스트의 Agent·timer·수집 스크립트를 정리한다. 이 런북은 호스트 밖에 남은 AWS 리소스와 수동 생성 리소스를 대상으로 한다.

## 2. 사전 조건

- [ADR-OBS-002](../07-adr/quality/obs-002-local-operations-without-cloudwatch.md)의 로컬 로그·내부 health 운영 경계를 승인한다.
- `develop` 반영 후 직접 SSH 배포 또는 legacy bootstrap을 한 번 이상 실행해 호스트 정리 결과를 확인한다.
- 삭제 전 아래 인벤토리 결과를 파일로 보관하고, 운영에 필요한 SSM 파라미터·KMS·ECR·SSM Agent 권한과 구분한다.
- AWS CLI profile과 `ap-northeast-2` 리전을 명시한다. 명령에서 나온 ARN·이름을 확인하지 않고 삭제하지 않는다.

## 3. 인벤토리

```bash
export AWS_PROFILE=<운영-profile>
export AWS_REGION=ap-northeast-2

aws logs describe-log-groups \
  --log-group-name-prefix /masiton \
  --region "$AWS_REGION" > cloudwatch-log-groups.json
aws cloudwatch describe-alarms \
  --alarm-name-prefix masiton \
  --region "$AWS_REGION" > cloudwatch-alarms.json
aws sns list-topics --region "$AWS_REGION" > sns-topics.json
aws lambda list-functions --region "$AWS_REGION" > lambda-functions.json
aws iam list-roles --query 'Roles[?starts_with(RoleName, `masiton`)]' > iam-roles.json
aws ssm get-parameters-by-path \
  --path /masiton/alerts/ \
  --with-decryption \
  --recursive \
  --region "$AWS_REGION" > alert-parameters.json
```

`alert-parameters.json`에는 비밀값이 포함될 수 있으므로 공유하거나 커밋하지 않는다. 결과에서 대상 alarm·SNS topic·Lambda function·IAM role·SSM parameter의 실제 이름과 의존 관계를 확인한다.

## 4. 정리 순서

1. CloudWatch alarm의 `AlarmActions`와 `OKActions`에 연결된 SNS topic을 확인하고, 새 배포나 운영 알림에서 사용하지 않는지 확인한다.
2. CloudWatch alarm을 삭제한다. 삭제 전 alarm 이름과 현재 상태를 인벤토리와 대조한다.
3. SNS topic 구독과 topic을 삭제한다. Lambda가 다른 용도로 구독된 경우 해당 구독만 먼저 제거한다.
4. Slack notifier Lambda를 삭제하고, 전용 Lambda 실행 역할의 inline·managed policy를 분리한 뒤 역할을 삭제한다. 공용 역할이나 다른 함수가 참조하면 삭제하지 않는다.
5. Lambda가 더 이상 읽지 않는 `/masiton/alerts/slack-webhook-url` 같은 전용 SSM SecureString을 삭제한다. 운영 애플리케이션의 비밀 파라미터는 삭제하지 않는다.
6. `/masiton/*` 로그 그룹을 인벤토리와 대조해 삭제한다. 애플리케이션이 현재 로그를 로컬에만 남기는지 확인한 뒤 실행한다.
7. Terraform을 적용해 CloudWatch·Logs 전용 IAM 권한이 state와 실제 역할에서 사라졌는지 확인한다. SSM, KMS, ECR, S3 secret, SSM Agent 권한은 보존한다.
8. Terraform plan에서 단일 EC2의 `monitoring = false`가 유지되고 Detailed Monitoring 변경이 남지 않는지 확인한다.

AWS 리소스 삭제 명령은 인벤토리의 실제 이름을 변수에 대입한 뒤, 한 리소스씩 실행한다. 이 런북에는 오삭제를 막기 위해 재사용 가능한 `delete-all` 명령을 두지 않는다.

## 5. 완료 검증

```bash
aws cloudwatch describe-alarms --alarm-name-prefix masiton --region "$AWS_REGION"
aws logs describe-log-groups --log-group-name-prefix /masiton --region "$AWS_REGION"
aws sns list-topics --region "$AWS_REGION"
aws lambda list-functions --region "$AWS_REGION"
aws iam list-attached-role-policies --role-name <운영-app-role>
```

호스트에서는 다음을 확인한다.

- `systemctl is-enabled masiton-health-metrics.timer`가 `disabled` 또는 unit 없음이다.
- `systemctl is-active amazon-cloudwatch-agent`가 `inactive` 또는 unit 없음이다.
- `/opt/masiton/bin/health-metrics.sh`와 Agent 설정·실행 파일이 없다.
- `/internal/health/live`, `/internal/health/ready`, `/internal/health/dependencies`와 로컬 Docker/Nginx 로그 확인이 정상이다.

다음 청구 주기에서 CloudWatch·SNS·Lambda 관련 비용이 0인지 Cost Explorer와 청구서에서 대조하고, 비용이 남으면 삭제하지 않은 리소스와 다른 리전을 다시 인벤토리한다.
