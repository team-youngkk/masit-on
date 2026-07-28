---
id: ADR-SEC-001
title: 비밀정보와 AWS 워크로드 인증
status: Accepted
decision_date: 2026-07-27
owners:
  - 이우람
related_requirements:
  - NFR-SECURITY-003
  - NFR-EXTERNAL-003
  - NFR-DEPLOYMENT-001
  - NFR-PRIVACY-002
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../03-team/ownership.md
  - ../../06-architecture/technology-policy.md
  - auth-001-spring-security-jwt.md
  - ../integration/ext-001-reference-verification.md
  - ../platform/runtime-001-docker.md
  - ../platform/ci-001-github-actions-quality-gate.md
  - ../platform/deploy-002-validation-deployment-before-expansion.md
  - ../../02-analysis/mvp-workstreams.md
  - ../adr-traceability.md
  - ../../03-team/roles.md
  - ../architecture/arch-002-external-ports-adapters.md
supersedes: []
superseded_by: null
---

# ADR-SEC-001 비밀정보와 AWS 워크로드 인증

## 1. 상태

Accepted

## 2. 결정 요약

M2부터 운영 비밀값은 Parameter Store SecureString과 KMS, EC2의 AWS 접근은 IAM Role, GitHub Actions의 AWS 접근은 OIDC 단기 자격 증명을 사용한다.

## 3. 배경

관리자 JWT 서명 키, DB·Redis 자격 증명과 Kakao·YouTube API 키는 모든 단계에서 코드·GitHub 저장소와 분리해야 한다. MVP 구현은 로컬 전용 비밀 주입 방식을 사용하고, Parameter Store·KMS·IAM Role·OIDC는 M2부터 적용한다.

## 4. 결정 문제

JWT 서명 키, DB·Redis 자격 증명, Kakao·YouTube API 키와 같은 운영 비밀을 어디에 보관하고, EC2 런타임과 GitHub Actions CI는 각각 무슨 자격 증명으로 AWS 리소스에 접근할 것인가.

## 5. 고려한 선택지

- Parameter Store·KMS + IAM Role·OIDC: AWS 관리형 SecureString 저장소와 EC2 IAM Role, GitHub Actions OIDC 단기 자격 증명을 조합한다.
- 저장소·환경 파일에 장기 비밀 저장: `.env` 파일이나 GitHub 저장소에 JWT 서명 키·DB 비밀번호·Kakao·YouTube API 키를 평문 또는 커밋된 파일로 둔다. 학생 팀 저장소 특성상 커밋 이력 노출, 여러 로컬 클론과 브랜치 공유로 유출 경로가 많고, 네 가지 실제 비밀 중 하나라도 새어 나가면 관리자 인증 전체(JWT 서명 위조)나 외부 API 키 도용(과금·차단) 피해가 즉시 발생하므로 받아들일 수 없다.
- EC2·CI의 장기 IAM Access Key: EC2 인스턴스와 GitHub Actions에 만료되지 않는 IAM Access Key를 발급해 사용한다. 키 교체를 수동으로 챙기지 않으면 장기간 노출 위험이 누적되고, 4명이 각자 담당 Workstream 개발에 집중해야 하는 팀 구조상([roles.md](../../03-team/roles.md)) 별도 키 교체 운영을 지속적으로 챙길 여력이 크지 않다. IAM Role(EC2)과 OIDC(GitHub Actions)는 만료되는 단기 자격 증명을 자동 발급하므로 이 운영 부담 자체를 없앤다.
- (참고, 배제) 제3자 상용 비밀 관리 서비스(예: HashiCorp Vault): Vault 자체의 서버 운영(가용성, 백업, unseal 절차)과 인프라 비용이 별도로 필요해, 15만 원 목표 예산과 단일 EC2 인스턴스 구조에 새로운 상시 운영 대상을 하나 더 얹는 셈이 된다. AWS Parameter Store·KMS는 EC2·GitHub Actions와 동일한 AWS 계정 안에서 IAM 정책만으로 접근을 통제할 수 있어 별도 서버나 운영 인력 없이 동일한 목적(비밀 암호화 저장, 접근 통제, 감사)을 낮은 비용으로 달성한다. MVP 규모(관리자 JWT 키, DB·Redis 자격 증명, Kakao·YouTube API 키 정도의 소수 비밀)에서는 Vault의 동적 시크릿·다중 백엔드 같은 고급 기능이 실제로 필요하지도 않다.

## 6. 결정

로컬 단계에서는 Git에 포함되지 않은 환경별 비밀 주입을 사용한다. M2부터 비밀값을 Parameter Store SecureString과 KMS로 보호하고, EC2 런타임은 IAM Role, GitHub Actions는 OIDC 기반 단기 자격 증명을 사용한다.

## 7. 선택 근거

이 조합은 이미 확정된 배포 구조(단일 EC2, GitHub Actions→ECR→EC2, [technology-policy.md](../../06-architecture/technology-policy.md) 13절)와 예산 제약(월 15만 원 목표) 위에 추가 인프라 없이 얹을 수 있는 유일한 선택지다. IAM Role·OIDC는 AWS가 별도 비용 없이 제공하는 워크로드 신원 메커니즘이므로 별도 구독료나 운영 인력 없이 장기 키 배포·수동 교체 부담을 없앤다. Parameter Store SecureString + KMS 역시 사용량 기준의 낮은 비용으로 관리자 JWT 서명 키, DB·Redis 자격 증명, Kakao·YouTube API 키라는 실제 존재하는 소수의 구체적 비밀을 감당하기에 충분하며, 이보다 무거운 전용 비밀 관리 서비스를 도입할 만큼 비밀의 종류나 접근 정책이 복잡하지 않다.

## 8. 트레이드오프

평문 저장이나 장기 키보다 최초 설정 비용(KMS key policy, Parameter 경로 설계, IAM trust policy, OIDC 조건 설정)이 든다. 별도 인프라 전담자가 없는 4인 팀 구조에서 이 설정을 담당하는 이우람(인프라 기술 의사결정 담당, [roles.md](../../03-team/roles.md))에게 초기 부담이 집중된다. Redis 장애 시 관리자 재로그인이 필요해지는 fail-closed 정책([ADR-AUTH-001](auth-001-spring-security-jwt.md))처럼, 비밀 접근 계층 장애 시에도 애플리케이션 시작이 지연되거나 배포가 막힐 수 있다. 이 위험은 부트스트랩 절차 문서화와 배포 전후 헬스체크([roles.md](../../03-team/roles.md)의 "배포 전후 검사와 헬스체크 기준 확인" 책임)로 완화하고, 초기 설정 비용은 EC2·CI 각각 한 번만 구성하면 되는 일회성 비용이라는 점으로 상쇄한다.

## 9. 적용 범위

JWT 서명 키, DB·Redis 자격 증명, Kakao·YouTube API 키, 배포 권한과 모든 운영 비밀에 적용한다. 로컬 개발 환경(Docker PostgreSQL·Redis)은 [technology-policy.md](../../06-architecture/technology-policy.md) 5절에 따라 운영 비밀과 분리된 별도 설정을 사용하며 이 ADR의 대상이 아니다.

## 10. 강제 규칙

최소 권한, 환경 분리(개발·운영), 키 사용 감사와 애플리케이션 로그 마스킹을 적용한다. Kakao·YouTube API 키는 Adapter 계층([ADR-ARCH-002](../architecture/arch-002-external-ports-adapters.md))에서만 참조하고 그 밖의 코드에 전파하지 않는다.

## 11. 금지 사항

평문 저장·로그·응답·커밋, 개발 환경에 운영 권한 배포, EC2·GitHub Actions의 장기 AWS Access Key 발급을 금지한다.

## 12. 구현 및 운영 영향

KMS key policy, Parameter 경로 설계(서비스·환경별 네임스페이스), IAM trust policy, GitHub Actions OIDC 조건(리포지토리·브랜치 제한)과 키 교체 절차 문서화가 필요하다. Redis 장애 시 Refresh Token 재발급이 막히는 fail-closed 정책([ADR-AUTH-001](auth-001-spring-security-jwt.md))과 마찬가지로, Parameter Store·KMS 접근 실패 시 애플리케이션이 비밀 없이 기동되지 않고 안전하게 시작 실패하도록 부트스트랩 절차를 정의한다.

## 13. 검증 방법

비밀 스캔(커밋·PR에서 API 키·비밀번호 패턴 검사), 최소 권한 검토(IAM 정책이 필요한 리소스로만 한정되는지), 잘못된 신원(다른 리포지토리·브랜치의 OIDC, 권한 없는 Role)의 접근 거부, OIDC·IAM Role의 단기 자격 증명 발급과 만료를 검증한다. 배포 파이프라인에서 Parameter Store 접근이 실패할 경우 애플리케이션이 비밀 없이 기동되지 않고 명확히 실패하는지 확인한다.

## 14. 재검토 조건

AWS 계정·플랫폼 자체가 바뀌거나, 비밀 종류·접근 정책이 크게 늘어나 Parameter Store의 계층 구조로 관리하기 어려워지거나, 예산 여력이 커져 전용 비밀 관리 서비스 도입이 실질적 이득을 주거나, 외부 감사 요구가 강화될 때 재검토한다.

## 15. 관련 문서

- [기술 정책](../../06-architecture/technology-policy.md)
- [NFR](../../01-requirements/non-functional-requirements.md)
- [관리자 JWT 인증 ADR](../security/auth-001-spring-security-jwt.md)
- [외부 기준정보 확인 서비스 ADR](../integration/ext-001-reference-verification.md)
