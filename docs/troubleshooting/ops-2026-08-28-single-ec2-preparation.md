---
related_documents:
  - ../../infra/production/README.md
  - ../08-planning/postgres-ec2-single-instance-transition.md
  - ../08-planning/deployment-hardening-cutover-record.md
  - ops-2026-08-19-alb-cutover-review.md
---

# 운영 전환: PostgreSQL EC2·단일 앱 EC2 준비 적용

## 1. 개요

2026-08-28에 backend 전환 준비 PR이 `develop`에 병합된 뒤, AWS 운영 계정에 단일 direct app EC2와 PostgreSQL·Redis 분리 경로를 병행으로 준비했다. Route53은 기존 ALB를 계속 가리키며, legacy ALB·ASG·CodeDeploy를 삭제하거나 트래픽을 전환하지 않았다.

이 저장소는 공개되어 있으므로 실제 보안 그룹 ID·인스턴스 ID·공인 IP·계정 식별자는 기록하지 않는다. 실제 값은 Terraform state와 AWS 콘솔에서 확인한다.

## 2. 적용 결과

| 영역 | 결과 |
|---|---|
| production Terraform | direct app EC2·EIP·direct SG·health alarm·SSM 배포 IAM과 PostgreSQL SG 규칙을 생성했다. 최종 재계획은 변경 없음이다. |
| PostgreSQL EC2 | legacy DB SG를 유지한 채 전용 DB SG를 추가했다. 전용 SG의 5432 ingress source는 앱 SG만으로 제한했다. |
| Redis Terraform | direct app SG를 Redis 6379와 SSM interface endpoint 443에 추가했고 Redis 역할의 KMS 복호화를 Redis 비밀번호 parameter로 제한했다. 최종 재계획은 변경 없음이다. |
| IAM | 앱·Redis 역할의 KMS 복호화를 SSM 경유와 허용 parameter ARN context로 제한했다. GitHub SSM 역할에서 사용하지 않는 조회 권한을 제거했다. |
| DNS | 변경하지 않았다. apex A record는 기존 ALB alias를 유지한다. |
| legacy 앱 | backend의 `/internal/health/live`, `/internal/health/ready`, `/internal/health/dependencies` 직접 확인 결과가 모두 200이었다. |

## 3. 실제로 드러난 문제와 처리

### 3.1 수동 DB ingress와 Terraform 생성 규칙의 중복

전용 DB SG를 만든 뒤 legacy app SG의 5432 ingress를 먼저 추가했다. 이어 production plan을 적용하자 같은 source·port 규칙을 Terraform이 생성하려 하면서 `InvalidPermission.Duplicate`가 발생했다.

AWS 규칙을 삭제하지 않고 해당 보안 그룹 규칙을 Terraform state에 import했다. 이후 Terraform이 설명과 공통 태그만 동기화했고, 최종 plan은 `0 add, 0 change, 0 destroy`가 됐다.

재발 방지:

- SG 규칙을 수동으로 추가하기 전에 Terraform state와 AWS 규칙을 먼저 조회한다.
- 수동으로 만든 규칙을 계속 관리할 경우 생성보다 `terraform import`를 먼저 수행한다.
- 운영 전환 plan은 적용 전후 모두 `destroy=0`과 legacy resource `no-op`를 확인한다.

### 3.2 direct app 부트스트랩 후 서비스가 비활성

새 direct app EC2는 SSM `Online`이지만 `nginx`, backend, frontend는 비활성이고 로컬 health는 연결 거부였다. cloud-init 로그에서 Docker와 SSM Agent 설치 및 환경 파일 생성까지 정상 종료한 것을 확인했다. direct app의 애플리케이션 산출물은 AMI가 아니라 SSM 배포가 주입하는 설계이므로, 이는 부트스트랩 실패가 아니다.

현재 `develop` 병합 commit은 ECR image push 대상 브랜치가 아니어서 backend·frontend image가 아직 없다. 이전 ECR image로 우회하지 않는다.

## 4. 다음 실행 게이트

1. 보안 변경과 Terraform state import를 후속 PR로 병합한다.
2. 운영 배포가 허용된 `main` 또는 승인된 `deploy/m2` ref에서 병합 commit의 backend·frontend image를 생성·검증·ECR에 push한다. 현재 원격에는 `deploy/m2` ref가 없으므로 임의로 만들지 않는다.
3. GitHub Actions `workflow_dispatch`에서 `deployment_target=ssm`을 선택하고 `PRODUCTION_INSTANCE_ID`가 가리키는 direct app에 배포한다. production environment 승인을 거친다.
4. SSM 배포 후 앱·Nginx·PostgreSQL·Redis health와 외부 HTTPS/API smoke를 확인한다.
5. 검증이 끝난 별도 plan에서만 `direct_traffic_enabled=true`로 Route53을 EIP로 전환한다. ALB·ASG·CodeDeploy 정리는 DNS 전환 후 별도 승인 plan으로 다룬다.

## 5. 분류와 후속 지표

| 항목 | 분류 | 발견 경로 | 예방 지표 |
|---|---|---|---|
| SG rule 중복 | 인프라 / 배포 | 실제 Terraform apply | apply 전 AWS rule 조회, import 후 plan 0 변경 |
| direct app 서비스 미기동 | 배포 전제 | 새 EC2 SSM smoke | image tag ECR 존재 여부, SSM 배포 성공, health 3종 200 |
| legacy 경로 영향 | 안전성 | 기존 앱 SSM smoke | backend health 3종 200, Route53 ALB 유지 |
