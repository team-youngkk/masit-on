---
id: ADR-PERF-003
title: 격리 성능 검증 환경 Terraform과 상태 저장소
status: Accepted
decision_date: 2026-08-16
owners:
  - 이우람
reviewers: []
related_requirements:
  - NFR-TEST-002
  - RV-NFR-011
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../08-planning/issue-207-isolated-performance-result.md
  - perf-001-k6-load-testing.md
  - ../../06-architecture/technology-policy.md
  - ../platform/runtime-001-docker.md
  - ../security/sec-001-secrets-workload-identity.md
  - ../adr-index.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-PERF-003 격리 성능 검증 환경 Terraform과 상태 저장소

## 1. 상태

`Accepted` — 2026-08-16 팀 리뷰에서 Terraform 도입과 격리 환경 egress 축소안을 승인했다. S3 bucket·DynamoDB table bootstrap과 접근 role 지정은 실제 AWS 실행 전 운영 절차로 남긴다.

## 2. 결정 요약

이슈 #207의 반복 가능한 격리 성능 환경은 Terraform으로 관리한다. Terraform은 기존 VPC·서브넷을 data source로만 읽고, 앱 EC2·k6 loadgen EC2·RDS·보안 그룹·IAM·SSM 리소스만 실행별로 생성한다.

Terraform state는 AWS S3에 암호화·versioning으로 저장하고 DynamoDB table로 locking한다.

| 항목 | 결정 |
|---|---|
| Terraform | `1.6.6` |
| AWS provider | `hashicorp/aws 5.100.0` |
| app egress | HTTPS·VPC DNS·RDS 5432 |
| loadgen egress | HTTPS·VPC DNS·app 8080 |
| db egress | 없음. stateful 응답 트래픽만 허용 |
| S3 region | `ap-northeast-2` |
| S3 bucket 제안 이름 | `masiton-terraform-state-711457211155` |
| state key | `performance/issue-207/terraform.tfstate` |
| DynamoDB table 제안 이름 | `masiton-terraform-state-lock` |

제안 이름의 bucket·table은 이 PR에서 자동 생성하지 않는다. 별도 bootstrap 절차로 한 번 생성·보호한 뒤 `backend.hcl`을 통해 연결한다.

## 3. 배경

[ADR-PERF-001](perf-001-k6-load-testing.md)은 운영과 분리된 임시 EC2에서 성능 검증을 수행하도록 결정했지만, 반복 실행을 위한 인프라 프로비저닝 방식은 정하지 않았다. 수동 AWS 프로비저닝은 실행별 리소스 누락·운영 리소스 오변경·정리 누락을 사람이 기억해야 하므로 재현성과 격리 검증에 불리하다.

## 4. 선택 근거

- Terraform은 선언된 변경 계획과 destroy 경로를 제공해 실행별 리소스 범위를 리뷰할 수 있다.
- S3 backend는 state 암호화·versioning과 IAM 접근 통제를 적용할 수 있다.
- app·loadgen은 HTTPS와 VPC DNS, 필요한 내부 대상만 egress로 허용하고 RDS SG에는 egress를 두지 않아 노출 범위를 줄인다.
- Terraform 1.6 호환성을 유지해야 하므로 S3 native lockfile 대신 DynamoDB locking을 사용한다.
- backend 리소스 자체는 관리 대상 state와 분리해 bootstrap한다. 같은 state로 bucket과 locking table을 만들면 초기화 순환이 생긴다.

## 5. 보안·운영 경계

- bucket은 private, SSE, versioning, public access block을 사용한다.
- DynamoDB table은 `LockID` 문자열 hash key와 `PAY_PER_REQUEST`를 사용한다.
- `db_password`와 SSM SecureString 값이 state에 포함될 수 있으므로 state를 local backend로 실행하거나 저장소에 커밋하지 않는다.
- backend bucket·table은 성능 검증 담당 AWS role만 접근한다.
- backend가 준비되지 않은 상태에서는 `terraform plan`·`apply`를 실행하지 않는다.

## 6. 미결정 및 승인

팀 리뷰에서 Terraform 도입과 egress 축소안을 승인했다. 제안한 bucket·table 이름, 접근 role, bootstrap 소유자는 실제 AWS bootstrap 전에 확인한다.

## 7. 관련 구현

- [Terraform backend 선언](../../../infra/performance/terraform/backend.tf)
- [backend 설정 예시](../../../infra/performance/terraform/backend.hcl.example)
- [격리 성능 환경 실행 절차](../../../infra/performance/README.md)
