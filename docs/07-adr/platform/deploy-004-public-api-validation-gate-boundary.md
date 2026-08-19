---
id: ADR-DEPLOY-004
title: 비관리자 공개 API 검증 세션 gate 경계
status: Accepted
decision_date: 2026-08-14
owners:
  - 이우람
related_requirements:
  - NFR-SECURITY-001
  - NFR-SECURITY-003
  - NFR-DEPLOYMENT-002
  - NFR-DEPLOYMENT-004
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../02-analysis/first-expansion-workstreams.md
  - ../../05-specs/api/common/validation-access-contract.md
  - ../../06-architecture/security-boundary.md
  - ../../08-planning/expansion-1-task-breakdown.md
  - deploy-003-validation-cookie-session.md
  - web-006-unified-login-rbac-route.md
supersedes:
  - ADR-DEPLOY-003
supersedes_decision: 검증 로그인·세션 진입점과 자체 인증 Callback을 제외한 전체 화면·API의 검증 세션 gate 적용
superseded_by: null
---

# ADR-DEPLOY-004 비관리자 공개 API 검증 세션 gate 경계

## 1. 상태

Accepted. 2026-08-14 Issue #197에서 Spring Security의 비관리자 공개 API와 Nginx 검증 세션 gate가 서로 다른 공개 범위를 적용해 운영 요청을 차단한 문제를 해결하기 위해 [ADR-DEPLOY-003](deploy-003-validation-cookie-session.md)을 대체한다. 검증 참여자 쿠키 세션 자체는 유지하되, 제품 API의 공개·보호 경계를 별도 운영 인증이 뒤집지 않도록 Nginx 예외 조건을 변경한다.

## 2. 결정 요약

검증 참여자 제한 공개는 전용 로그인 화면과 서버 측 쿠키 세션을 계속 사용한다. 비관리자 공개 제품 API는 Spring Security가 `permitAll`로 공개한 경로와 Method만 검증 세션 gate 전에 Backend로 전달한다. 관리자·미정의 API와 공개 경로의 비허용 Method는 계속 Nginx `auth_request`로 검증 세션을 요구한다.

검증 로그인·세션 진입점, 로그인 정적 자산과 자체 인증 외부 Callback 예외도 유지한다. 실제 제외 목록과 경로별 인증 수단은 [검증 참여자 제한 공개 API 계약](../../05-specs/api/common/validation-access-contract.md) 4절이 단일 목록으로 소유한다.

## 3. 배경

ADR-DEPLOY-003은 Basic Auth와 회원·관리자 Bearer의 `Authorization` 충돌을 제거하면서 로그인·세션 진입점과 자체 인증 Callback 외 화면·`/api/**`에 검증 세션을 요구했다. 이후 회원 인증, 큐레이션과 AI 조회 API가 추가되며 Spring Security의 비관리자 `permitAll` 범위가 넓어졌지만 Nginx는 기존 전체 API gate를 유지했다.

그 결과 애플리케이션이 공개한 API도 검증 쿠키가 없으면 `401 VALIDATION_ACCESS_REQUIRED`로 차단됐고, YouTube Callback은 브라우저 쿠키를 보낼 수 없어 별도 예외가 필요했다. 제한 공개 진입 경계가 제품 API의 인증·인가 계약보다 더 좁은 독립 권한 체계처럼 동작한 것이 원인이다.

## 4. 결정 문제

검증 참여자 쿠키 세션을 유지하면서 Spring Security의 공개·보호 API 계약, 선택적 회원 인증과 외부 Callback을 Nginx에서 어떻게 일관되게 라우팅할 것인가.

## 5. 고려한 선택지

| 선택지 | 판단 | 이유 |
|---|---|---|
| 전체 화면·`/api/**` gate 유지 | 기각 | Spring Security 공개 계약과 상충하고 검증 세션 장애가 공개 API까지 차단한다. |
| 비관리자 `permitAll` 경로·Method만 gate 제외 | 채택 | 제품 API 계약을 유지하면서 관리자·미정의 API의 제한 공개 경계를 보존한다. |
| `/api/**` 전체 gate 제외 | 기각 | 관리자·미정의 API까지 검증 참여자 제한을 우회해 범위가 과도하게 넓어진다. |
| Bearer 또는 Cookie 존재 여부로 gate 제외 | 기각 | 가짜 헤더로 우회할 수 있고 선택적 인증·Refresh Cookie 의미를 Nginx가 추측하게 된다. |

## 6. 결정

### 6.1 검증 쿠키 세션

- 쿠키 이름과 속성은 `__Host-masiton-verification`, `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, `Domain` 미지정, 7일 고정 만료를 유지한다.
- 세션 원문은 128-bit 이상 불투명 값이며 Redis에는 SHA-256 해시와 만료만 저장한다.
- 검증 세션은 회원·관리자 Principal과 역할을 만들지 않고 정식 공개 시 전용 경계 전체를 제거한다.

### 6.2 Nginx gate 제외 조건

- `/verification/login`, `POST|DELETE /api/verification/sessions`와 로그인 화면에 필요한 정적 자산은 gate 전에 허용한다.
- 비관리자 공개 제품 API는 Spring Security가 `permitAll`로 공개한 exact 경로·Method와 anchored 단일 segment 동적 경로만 gate 전에 허용한다.
- 외부 시스템 Callback은 자체 인증 수단이 있고 Nginx가 Method·본문 크기·호출률을 제한할 때만 gate 전에 허용한다.
- 목록에 없거나 경로는 같아도 Method가 다른 `/api/**`, `/api/admin/**`와 보호 화면은 검증 세션을 요구한다.
- `/internal`과 `/internal/**`은 자격 증명과 무관하게 외부 `404`를 유지한다.

### 6.3 자격 증명 경계

- 일반 공개 API는 `Authorization`과 `Cookie`를 보존해 Backend가 선택적 회원 Bearer와 Refresh Token을 계약대로 해석하게 한다.
- 검증 세션 Endpoint는 회원·관리자 `Authorization`을 제거하되 세션 종료를 위해 검증 Cookie를 전달한다.
- YouTube Callback은 허용 Method와 gate를 거친 비허용 Method 모두 Backend 전달 전에 `Authorization`과 `Cookie`를 제거한다.
- 검증 쿠키가 있어도 회원 보호 API는 회원 Bearer, 관리자 API는 관리자 Bearer와 `ADMIN` 권한을 별도로 검증한다.

### 6.4 실패와 장애

- API gate 실패는 redirect가 아니라 `401 VALIDATION_ACCESS_REQUIRED` JSON으로 반환한다.
- 화면 gate 실패만 `/verification/login`으로 이동하며 `WWW-Authenticate: Basic`은 사용하지 않는다.
- Redis에서 검증 세션을 확인할 수 없으면 gate 대상 요청은 fail-closed한다.
- gate에서 제외한 비관리자 공개 API와 자체 인증 Callback은 검증 세션 Redis 장애와 독립적으로 동작한다.

## 7. 선택 근거

Nginx는 제한 공개 운영 진입 여부만 판단하고 제품 권한은 Spring Security와 각 API 계약이 소유해야 한다. 경로·Method allowlist는 헤더 기반 추측 없이 이 경계를 표현하며, 관리자와 정의되지 않은 API의 제한 공개 gate는 유지한다. 실제 목록을 API 계약 한 곳에서 소유하면 확장 API가 추가될 때 검토 기준점도 명확하다.

## 8. 트레이드오프

비관리자 공개 API를 gate에서 제외하면 해당 API 자체는 검증 참여자에게만 한정되지 않는다. 이는 제품이 이미 승인한 공개 범위와 일치하지만, 정식 공개 전 전체 사이트를 단일 비밀로 가리는 모델은 더 이상 아니다.

공개 API가 추가·변경될 때 Spring Security, API 계약과 Nginx allowlist를 함께 갱신해야 하므로 경계 드리프트 비용이 생긴다. 정적 계약 테스트가 공개 매트릭스와 Nginx location을 대조하고 운영 smoke가 실제 프록시 응답과 3xx false green을 검사해야 한다.

## 9. 적용 범위

Nginx `auth_request`와 location 우선순위, Spring Security `permitAll` matcher, 검증 참여자 API 계약, 배포 설치·rollback 및 재기동 후 smoke에 적용한다.

## 10. 강제 규칙

- 공개 예외는 경로와 Method를 함께 고정하고 prefix 확장이나 UUID 형식을 가정하지 않는다.
- `/api/admin/**`, 정의되지 않은 `/api/**`와 공개 경로의 비허용 Method는 gate를 유지한다.
- 일반 공개 API의 선택적 회원 자격 증명은 보존하고 검증 Endpoint·Callback 자격 증명은 각 계약대로 격리한다.
- 서버 수준 `auth_request`로 공개 location 예외를 무효화하지 않는다.
- API smoke는 `VALIDATION_ACCESS_REQUIRED`, upstream 장애와 모든 3xx를 Backend API 미도달로 판정한다.

## 11. 금지 사항

요청 헤더 존재 여부에 따른 gate 우회, `/api/**` 포괄 예외, 비고정 동적 하위 경로 예외, Callback으로 회원·관리자 자격 증명 전달, API 실패의 로그인 HTML redirect를 금지한다.

## 12. 구현 및 운영 영향

Nginx는 공개 API별 exact 또는 anchored regex location에서 허용 Method만 `@public_api`로 보내고 나머지는 `@verification_api`로 보낸다. Callback 비허용 Method는 자격 증명 제거가 있는 전용 gate proxy를 사용한다. `nginx-install.sh`는 재기동 뒤 `nginx-smoke.sh`가 성공한 후에만 rollback trap을 해제한다.

## 13. 검증 방법

- Spring Security 비관리자 공개 API 전체와 Nginx 공개 경로·Method 목록이 일치하는지 정적 계약 테스트로 확인한다.
- 공개 GET·POST가 검증 쿠키 없이 Backend 응답에 도달하고 3xx 로그인 redirect를 반환하지 않는지 실제 Nginx 경유 smoke로 확인한다.
- 공개 경로의 비허용 Method, `/api/admin/**`와 정의되지 않은 `/api/**`가 `401 VALIDATION_ACCESS_REQUIRED`를 반환하는지 확인한다.
- `/internal/**` 외부 404와 검증 세션 Endpoint의 Method·Origin 경계를 확인한다.
- Callback GET·POST의 자체 인증·유량·본문 제한과 모든 전달 경로의 `Authorization`·`Cookie` 제거를 확인한다.
- Nginx 1.30.3 `nginx -t`와 실패 시 직전 구성 rollback을 확인한다.

## 14. 정식 공개 전환

정식 공개 Task는 Nginx `auth_request`와 예외 location, 검증 로그인·세션 API·내부 Adapter, Redis `auth:verification:*`, 검증 쿠키·Parameter Store 비밀정보, 전용 테스트·알람을 같은 변경 단위에서 제거한다. 회원·관리자 인증과 제품 API 공개 계약은 이 제거의 영향을 받지 않아야 한다.

## 15. 재검토 조건

정식 공개로 검증 참여자 경계를 제거할 때, API를 별도 Origin이나 Gateway로 분리할 때, 비관리자 공개 API의 인증 모델을 바꿀 때 재검토한다.

## 16. 관련 문서

- [검증 참여자 제한 공개 API 계약](../../05-specs/api/common/validation-access-contract.md)
- [보안 경계](../../06-architecture/security-boundary.md)
- [통합 로그인과 역할 기반 관리자 화면 진입](web-006-unified-login-rbac-route.md)
- [대체한 ADR-DEPLOY-003](deploy-003-validation-cookie-session.md)
