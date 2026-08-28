---
related_documents:
  - ../../infra/production/README.md
  - ../../.github/workflows/ci.yml
  - ../08-planning/blue-green-cleanup-runbook.md
---

# 2026-08-28 단일 EC2 전환 후 운영 자원 정리

## 배경

단일 앱 EC2로 DNS와 배포 경로를 전환한 뒤, 기존 ALB·CodeDeploy Blue-Green 경로가 다시 생성되지 않도록 Terraform 정의와 GitHub Actions를 SSM 단일 EC2 경로로 동기화했다.

## 실행 결과

- Terraform을 적용해 ALB, HTTP/HTTPS listener, ALB security group/rule, ALB 관련 CloudWatch alarm, CodeDeploy application/deployment group/IAM을 삭제했다.
- CodeDeploy transient ASG 5개와 레거시 앱 EC2 3대를 종료했다. 레거시 인스턴스의 연결 EBS volume도 삭제되었다.
- RDS 인스턴스가 없는 것을 확인한 뒤 전환용 수동 snapshot 3개를 삭제했다.
- ALB 전용 EIP 2개는 ALB 삭제와 함께 해제되었고, 앱 EIP 1개만 유지되었다.

## 유지한 리소스

- 앱 EC2, PostgreSQL EC2, Redis EC2와 각 운영 volume
- 앱 EIP와 `masiton.click` Route53 A record
- `masiton-prod-blue-asg` seed ASG와 `masiton-prod-blue` target group
- Redis secret 경로를 S3 SSE-KMS로 전환하고 Redis 재부팅·앱 SSM smoke를 통과한 뒤 SSM·SSMMessages interface endpoint를 삭제했다. S3 Gateway Endpoint는 유지한다.
- SSM command pointer와 Redis secret 객체를 보관하는 기존 S3 bucket
- 다른 프로젝트 소유인 `roviq.click` hosted zone

## 검증

- 앱 EC2·PostgreSQL EC2·Redis EC2가 모두 `running`이다.
- 앱 EC2의 SSM Agent가 `Online`이다.
- VPC에는 S3 Gateway Endpoint만 남고 SSM·SSMMessages interface endpoint는 없다.
- Redis가 재부팅 후에도 S3 secret을 렌더링하고, 인증 없는 PING은 거부하며 인증 PING은 성공한다.
- `masiton.click`이 앱 EIP를 반환한다.
- `https://masiton.click/`은 `307`, `/api/restaurants`는 `200`, `/internal/health/live`는 외부에서 `404`다.
- transient CodeDeploy ASG와 수동 RDS snapshot이 남아 있지 않다.

## 비용 메모

ALB·RDS 인스턴스·수동 snapshot·레거시 EC2를 제거했다. Redis secret 경로를 S3 Gateway Endpoint로 전환하고 SSM·SSMMessages interface endpoint 2개도 삭제했다. 앱·PostgreSQL·Redis EC2 3대, EBS, 앱 EIP와 Route 53을 유지하는 기본 구성은 데이터 전송·요청량·KMS·백업 등을 제외하면 월 `$37` 안팎으로 추정하며, 실제 청구액은 Cost Explorer에서 재확인한다.
