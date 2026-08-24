---
id: ADR-DEPLOY-006
title: 검증 참여자 gate 없는 정식 공개 전환과 운영 경계 유지
status: Accepted
decision_date: 2026-08-24
owners:
  - 이우람
related_requirements:
  - NFR-SECURITY-001
  - NFR-SECURITY-002
  - NFR-SECURITY-003
  - NFR-AVAILABILITY-001
  - NFR-DEPLOYMENT-002
  - NFR-DEPLOYMENT-003
  - NFR-DEPLOYMENT-004
related_documents:
  - ../../00-overview/scope.md
  - ../../00-overview/service-overview.md
  - ../../00-overview/glossary.md
  - ../../01-requirements/non-functional-requirements.md
  - ../../02-analysis/README.md
  - ../../02-analysis/first-expansion-workstreams.md
  - ../../05-specs/api/common/authentication-contract.md
  - ../../05-specs/api/common/validation-access-contract.md
  - ../../05-specs/api/admin/ai-video-extraction-api.md
  - ../../06-architecture/security-boundary.md
  - ../../08-planning/m2-deployment-plan.md
  - ../../08-planning/deployment-hardening-impact-review.md
  - deploy-002-validation-deployment-before-expansion.md
  - deploy-003-validation-cookie-session.md
  - deploy-004-public-api-validation-gate-boundary.md
  - web-005-application-port-binding.md
  - web-006-unified-login-rbac-route.md
supersedes:
  - ADR-DEPLOY-004
superseded_by: null
---

# ADR-DEPLOY-006 검증 참여자 gate 없는 정식 공개 전환과 운영 경계 유지

## 1. 상태

Accepted. 2026-08-24부터 정식 공개의 진입 조건에서 M2 검증 참여자 제한 공개 gate를 제거한다. [ADR-DEPLOY-004](deploy-004-public-api-validation-gate-boundary.md)의 제한 공개 gate 결정은 역사적 단계로 보존하고 이 ADR이 현재 최종 결정을 대체한다.

M2의 제한 공개는 초기 운영 환경을 실제 사용자 흐름으로 확인하기 위한 완료된 전환 단계였다. 이 결정은 회원·관리자 인증이나 제품 API의 인증·인가 계약을 바꾸지 않으며, 실제 운영 배포와 `v1.0.0` annotated tag 생성은 별도 승인·운영 확인이 필요한 후속 항목이다.

## 2. 결정 요약

- 화면과 제품 API는 검증 참여자 쿠키·Basic Auth·Nginx `auth_request` 없이 정식 공개한다.
- `/api`의 공개·회원·관리자 인증 계약은 기존 문서를 그대로 사용한다. 공개 조회는 `permitAll`, 회원 기능은 통합 Bearer JWT와 Refresh 쿠키, `/api/admin/**`는 통합 Bearer JWT와 현재 `ADMIN` 역할을 요구한다.
- 회원·관리자 인증의 Redis `auth:session:` session, `__Secure-masiton-refresh` 쿠키, Origin 검증, 역할·상태 검증과 rate limit은 유지한다.
- YouTube Webhook은 제품 API의 별도 외부 수신 경계로 유지한다. 구독 확인 Token, 알림 HMAC 자체 인증, 요청 크기와 경로별 rate limit을 validation gate 제거로 완화하지 않는다.
- 허용 Host 검증과 알 수 없는 Host·Elastic IP 직접 접근 차단을 유지한다.
- `/internal/**`의 인터넷 외부 `404`, 허용된 상태 확인 경로의 내부 호출 경계와 운영 애플리케이션의 loopback 바인딩을 유지한다. Nginx를 우회한 애플리케이션 포트 직결은 허용하지 않는다.

## 3. 배경과 역사적 단계

[ADR-DEPLOY-002](deploy-002-validation-deployment-before-expansion.md)은 M2에서 최초 운영 환경을 먼저 배포하고 검증 참여자에게 제한 공개하도록 결정했다. [ADR-DEPLOY-003](deploy-003-validation-cookie-session.md)은 Basic Auth를 전용 쿠키 세션으로 바꿨고, [ADR-DEPLOY-004](deploy-004-public-api-validation-gate-boundary.md)은 비관리자 공개 API까지 포함하는 gate 범위를 정했다.

M2 제한 공개와 1~3차 확장 기준선 검증은 완료된 역사적 운영 단계다. 당시의 `/api/verification/sessions`, `/verification/login`, `__Host-masiton-verification` 쿠키, Redis `auth:verification:*` namespace와 검증 전용 Parameter Store 값은 현재 제품 계약이 아니다. 세부 API·쿠키·Redis 형식은 [역사적 검증 참여자 제한 공개 API 계약](../../05-specs/api/common/validation-access-contract.md)에 보존한다.

## 4. 현재 공개·인증 경계

### 4.1 제품 API

정식 공개 전환은 제품 API의 경로·Method·응답·인증 방식을 변경하지 않는다.

| 요청 범위 | 현재 경계 |
|---|---|
| 공개 탐색·상세 API | 통합 인증 없이 허용. 공개·활성 상태와 업무 규칙은 Application에서 확인 |
| 회원가입·이메일·비밀번호·통합 로그인·Refresh | 각 API의 자격 증명·일회용 Token·`__Secure-masiton-refresh` 쿠키·Origin·요청 제한을 적용 |
| `/api/me/**`와 회원 개인화 | 현재 계정의 Bearer JWT를 요구 |
| `/api/admin/**` | Bearer JWT와 현재 DB 역할 `ADMIN`을 요구 |
| 정의되지 않은 `/api/**` | 기본 거부 |

회원·관리자 인증의 통합 계정, principal, JWT, Refresh session과 Redis 장애 fail-closed 정책은 [ADR-AUTH-007](../security/auth-007-unified-account-rbac-session.md)과 API 인증 계약을 따른다.

### 4.2 Webhook 자체 인증과 남용 방지

`/api/webhooks/youtube/channel-updates`는 검증 참여자 gate의 예외가 아니라 자체 인증된 외부 수신 API다. 구독 확인 `GET`은 서버가 발급한 검증 Token과 채널 상태를 확인하고, 알림 `POST`는 협상한 `hub.secret`의 HMAC을 raw payload에 대해 검증한다. 서명이 없거나 불일치하면 작업을 만들지 않는다.

Webhook의 요청 크기 제한, 허용 Method, 출처별 호출률 제한과 `429` 응답은 현재 운영 경계로 유지한다. Webhook 처리기는 AI 호출·Kakao·YouTube 추가 호출·정식 Entity 저장을 동기 요청 안에서 수행하지 않는다. 상세 필드와 오류 코드는 [관리자 AI 영상 추출 API](../../05-specs/api/admin/ai-video-extraction-api.md) 4절을 정본으로 한다.

### 4.3 Host·내부 경계

- Nginx는 허용된 서비스 Host만 정상 라우팅하고 알 수 없는 Host와 Elastic IP 직접 접근은 차단한다.
- 인터넷에서 `/internal`과 `/internal/**`은 자격 증명 유무와 무관하게 `404`이며 Backend로 전달하지 않는다.
- `live`, `ready`, `dependencies` 상태 확인은 EC2 내부 Agent·컨테이너에서만 호출한다.
- Spring Boot와 Next.js 운영 포트는 `127.0.0.1` loopback에만 바인딩한다. 보안 그룹이나 Nginx 설정이 바뀌어도 외부가 애플리케이션 포트에 직결할 수 없어야 한다.

이 경계의 상세 결정은 [ADR-WEB-005](web-005-application-port-binding.md), 경로 분배는 [ADR-WEB-006](web-006-unified-login-rbac-route.md)이 소유한다.

## 5. 제거 대상과 보존 대상

### 5.1 정식 공개 전환으로 제거하는 항목

다음은 검증 참여자 제한 공개 전용이며 현재 운영·제품 계약에서 제거한다.

1. `/verification/login`, `/api/verification/sessions`와 내부 검증 Adapter
2. `__Host-masiton-verification` 쿠키와 `auth:verification:*` Redis key
3. 검증 참여자 자격 증명·전용 Parameter Store 주입
4. Nginx의 제한 공개 `auth_request`, 검증 전용 redirect·`401 VALIDATION_ACCESS_REQUIRED`
5. 검증 참여자 전용 테스트·알람·운영 runbook 단계

제거는 회원·관리자 인증, Webhook 자체 인증·rate limit, Host 검증, `/internal` 외부 `404`, loopback 포트 경계를 삭제하거나 완화하지 않는다.

### 5.2 현재 유지하는 운영·배포 검증

정식 공개에서도 CI 빌드·테스트 품질 게이트, 배포 전후 Smoke Test, 상태 확인, 로그·비밀정보 검사, rollback 준비와 운영 승인 절차는 유지한다. 이는 사용자 접근을 제한하는 validation gate가 아니라 배포 품질과 운영 안전성 확인이다.

## 6. 승인·운영 확인이 필요한 후속 항목

- 현재 저장소 문서와 구현에서 검증 참여자 전용 진입점·쿠키·Redis key·비밀정보·gate 구성이 제거됐는지 owner가 확인한다.
- 실제 정식 운영 배포는 배포 후보, DB migration 호환성, rollback, Webhook·Host·`/internal`·loopback 경계의 운영 smoke 결과를 확인한 뒤 별도 승인한다.
- `v1.0.0` annotated tag 생성은 운영 배포가 실제로 확인된 뒤 별도 승인으로 수행한다. 이 ADR은 태그를 만들거나 운영 자원을 변경하지 않는다.

## 7. 검증 기준

- 검증 참여자 쿠키 없이 공개 화면·공개 API가 정상 동작한다.
- 회원·관리자 로그인, Refresh, `/api/me/**`, `/api/admin/**`의 기존 인증·인가 회귀가 통과한다.
- Webhook의 Token·HMAC 자체 인증, Method·본문 크기·rate limit과 중복·비동기 접수가 유지된다.
- 허용되지 않은 Host와 Elastic IP 직접 접근이 차단되고, 인터넷의 `/internal/**`이 `404`이며 애플리케이션 포트 외부 직결이 실패한다.
- 오류 응답·로그에 비밀번호, Token, Cookie, Webhook secret과 내부 주소가 노출되지 않는다.
