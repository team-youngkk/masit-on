---
id: ADR-SEC-002
title: 전용 Redis 부트스트랩 비밀값 전달 경로
status: Accepted
decision_date: 2026-08-28
owners:
  - 이우람
related_requirements:
  - NFR-SECURITY-003
  - NFR-DEPLOYMENT-001
related_documents:
  - sec-001-secrets-workload-identity.md
  - ../../06-architecture/technology-policy.md
  - ../../../infra/production/terraform-redis/README.md
  - ../../../deploy/scripts/redis-render-conf.sh
supersedes: []
superseded_by: null
---

# ADR-SEC-002 전용 Redis 부트스트랩 비밀값 전달 경로

## 1. 상태

Accepted. [ADR-SEC-001](sec-001-secrets-workload-identity.md)의 일반 원칙을 대체하지 않고, 사설 subnet의 전용 Redis 부트스트랩에만 적용하는 구체화 결정이다.

## 2. 결정 요약

전용 Redis가 기동할 때 필요한 `requirepass`는 기존 S3 버킷의 별도 버전 관리 객체에서 읽는다. 객체는 SSE-KMS로 암호화하고, Redis EC2 IAM Role에는 해당 객체의 `s3:GetObject`·`s3:GetObjectVersion`과 S3 경유 KMS 복호화만 허용한다. Redis는 기존 S3 Gateway Endpoint로 객체를 받아 `/run/masiton/redis.conf`에만 렌더링한다.

애플리케이션의 기존 `/masiton/redis/password` Parameter Store 경로는 이 변경에서 삭제하지 않는다. 앱의 다른 운영 비밀값과 함께 읽는 현재 경로 및 rollback 경계를 보존하며, 비밀번호를 교체할 때 두 저장소를 같은 변경 절차로 갱신한다.

## 3. 배경

Redis는 private subnet에 있고 NAT Gateway를 사용하지 않는다. Parameter Store를 매 기동마다 읽기 위한 SSM interface endpoint를 유지하면 월 비용의 큰 부분이 남는다. 반면 Redis는 이미 S3 Gateway Endpoint로 배포 자산을 받으므로 별도 유료 endpoint 없이 동일한 private 경로를 재사용할 수 있다.

## 4. 보안 경계

- 비밀번호 본문은 Git, Terraform 변수·state, AMI, `user_data`, systemd 환경변수, 명령행, 로그에 넣지 않는다.
- secret 객체 key는 일반 Redis 배포 자산 prefix(`masiton/redis/assets`)와 분리한 `masiton/redis/secret/redis-password`로 고정한다. Terraform precondition도 두 경로의 중첩을 거부한다.
- S3 버킷의 versioning과 Public Access Block을 유지하고, Redis Role에는 secret 객체 ARN 한 개만 허용한다.
- SSE-KMS는 S3용 AWS 관리형 키를 사용한다. IAM의 `kms:Decrypt`는 `kms:ViaService = s3.<region>.amazonaws.com`으로 제한한다.
- 다운로드한 값은 `/run` tmpfs의 임시 파일로만 읽고, 최종 Redis 설정은 `0400`·Redis UID/GID 소유로 둔다. 객체 다운로드 실패·빈 값·공백 포함 값이면 Redis를 인증 없이 시작하지 않고 실패한다.

## 5. 운영 영향과 교체

secret 객체는 Terraform resource로 관리하지 않는다. 그래야 실제 비밀번호가 Terraform state에 기록되지 않는다. 승인된 운영자 절차에서 SSM의 기존 값을 읽어 SSE-KMS 객체로 업로드하고, `head-object`로 key·version·암호화 방식만 확인한다.

비밀번호 교체 시 기존 SSM Parameter와 S3 객체를 같은 새 값으로 갱신한 뒤 Redis를 재시작하고, 파일 기반 `AUTH + PING`을 확인한다. 이어서 앱의 backend 컨테이너를 재시작해 SSM 값을 다시 읽게 하고, 앱 dependency health와 인증 흐름을 확인한다. 실패하면 이전 S3 object version과 기존 SSM Parameter 값을 함께 복구한 뒤 Redis와 backend를 모두 재시작한다.

## 6. 삭제 게이트

S3 객체를 준비한 뒤 Redis 재시작·EC2 재부팅·앱 연결 검증을 모두 통과해야 SSM과 `ssmmessages` interface endpoint를 삭제한다. endpoint 삭제 후에도 public IP가 있는 앱 EC2의 SSM 배포와 외부 HTTPS/API smoke를 재확인한다. Redis data EBS와 S3 Gateway Endpoint는 삭제하지 않는다.

## 7. 재검토 조건

앱도 private subnet으로 이동하거나, Redis가 다중 인스턴스로 확장되거나, secret rotation을 자동화해야 하거나, 외부 감사가 더 강한 secret manager를 요구하면 이 결정을 재검토한다.
