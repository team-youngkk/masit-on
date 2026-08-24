---
id: ADR-WEB-006
title: 통합 로그인과 역할 기반 관리자 화면 진입
status: Accepted
decision_date: 2026-08-18
owners:
  - 양성훈
  - 김인안
  - 이우람
related_requirements:
  - FR-AUTH-001
  - FR-AUTH-002
  - FR-AUTH-004
  - FR-ADMIN-001
  - NFR-SECURITY-001
  - NFR-AVAILABILITY-001
  - NFR-DEPLOYMENT-002
related_documents:
  - ../../01-requirements/functional-requirements.md
  - ../../04-product/user-flows/first-expansion-user-flows.md
  - ../../04-product/wireframes/first-expansion-wireframes.md
  - ../../05-specs/api/README.md
  - ../../05-specs/api/account/member-authentication-api.md
  - ../../06-architecture/security-boundary.md
  - ../security/auth-007-unified-account-rbac-session.md
  - web-001-frontend-platform.md
  - web-002-data-state.md
  - web-003-routing-boundary.md
  - runtime-001-docker.md
supersedes:
  - ADR-WEB-003
superseded_by: null
---

# ADR-WEB-006 통합 로그인과 역할 기반 관리자 화면 진입

## 1. 상태

Accepted

이 문서는 [ADR-WEB-003](web-003-routing-boundary.md)을 전체 대체한다. 유지되는 Nginx·API·운영 경계까지 아래에 다시 기술하므로 현재 경로 계약은 이 문서만으로 해석한다. 계정 역할과 백엔드 인증 권한 계약은 [ADR-AUTH-007](../security/auth-007-unified-account-rbac-session.md)이 소유한다.

## 2. 결정 요약

회원과 관리자는 역할 선택이 없는 하나의 `/login` 화면에서 이메일과 비밀번호로 로그인한다. 로그인·재발급 응답의 현재 계정 역할을 TanStack Query `['auth', 'session']`에 보관하고 공통 헤더와 `/admin/**` 라우트 가드에 사용한다. `ADMIN`에게만 메인 페이지의 정확한 `/admin` 링크를 노출하며, 서버 API의 권한 검사를 최종 판정으로 유지한다.

## 3. 배경

기존 계약은 일반 회원 로그인과 `/admin/login`을 분리하고 관리자 화면 진입을 관리자 전용 인증 흐름으로 다뤘다. 그러나 `member_account` 계정에 `MEMBER`·`ADMIN` 역할을 부여하는 단일 계정 모델에서는 로그인 전에 사용자가 자신의 역할을 선택하게 할 이유가 없고, 같은 자격 증명 처리와 세션 복구가 화면별로 중복된다.

메인 공개 화면에서 관리자 기능으로 이동하는 발견 가능한 경로도 필요하다. 다만 메뉴 표시만으로 권한을 보장할 수 없으며, 새로고침 시 메모리 Access Token이 사라지는 상황, 오래된 역할 캐시, 악의적인 `returnTo`, `401`·`403`·일시 장애의 서로 다른 복구를 함께 정해야 한다.

## 4. 결정 문제

단일 로그인 화면과 역할 기반 관리자 진입을 도입하면서 화면 경로, 클라이언트 세션 상태, API 인증 복구, Nginx·운영 경계를 어떻게 일관되게 적용할 것인가.

## 5. 결정

### 5.1 외부 경로 소유권과 운영 경계

- `/api/**`: Spring Boot 백엔드 API
- `/internal/**`: Spring Boot 운영 상태 확인 경로이며 Nginx 인터넷 진입점에서는 차단
- 그 외 경로: Next.js 화면과 정적 자산
- API 버전 접두사 `/v1`은 도입하지 않는다.
- Nginx는 경로 접두사만 사용하며 `Accept`, User-Agent 또는 응답 형식으로 목적지를 구분하지 않는다.

M2의 검증 참여자 gate는 [ADR-DEPLOY-003](deploy-003-validation-cookie-session.md)과 [ADR-DEPLOY-004](deploy-004-public-api-validation-gate-boundary.md)에 역사적으로 기록되어 있으며, 정식 공개 전환으로 제거한다. 현재 공개·회원·관리자 API 경계와 Webhook 자체 인증·rate limit, Host·`/internal`·loopback 경계는 [ADR-DEPLOY-006](deploy-006-public-release-without-validation-gate.md)과 각 API 계약을 따른다. 운영 애플리케이션 포트의 loopback 바인딩은 [ADR-WEB-005](web-005-application-port-binding.md)가 소유한다.

### 5.2 화면 경로

| 경로 | 동작 |
|---|---|
| `/` | `/restaurants`로 이동 |
| `/login` | 회원·관리자 공용 이메일·비밀번호 로그인 |
| `/admin/login` | 호환성을 위해 `/login?returnTo=/admin`으로 이동 |
| `/restaurants` | 맛집 검색·필터·목록 |
| `/restaurants/{restaurantId}` | 맛집 상세 |
| `/creators/{creatorId}` | 유튜버 상세 |
| `/admin` | 권한 확인 뒤 관리자 대시보드 표시 |
| `/admin/restaurants/new` | 맛집 등록 |
| `/admin/creators/new` | 유튜버 등록 |
| `/admin/videos/new` | 영상 등록 |
| `/admin/visits/new` | 방문 관계 등록 |

`/login`에는 역할 선택 UI를 두지 않는다. 로그인 성공 뒤 `returnTo`가 유효하면 그 내부 경로로, 없으면 `/restaurants`로 이동한다. `/admin/login`은 로그인 폼을 렌더링하지 않고 위 호환 이동만 수행한다.

### 5.3 역할 기반 화면 진입

공통 헤더는 `['auth', 'session']`의 현재 역할이 정확히 `ADMIN`일 때만 목적지가 정확히 `/admin`인 `관리자` 링크를 표시한다. `MEMBER`, 비로그인, 세션 복구 중에는 링크를 표시하지 않는다. 역할 문자열의 접두사·부분 일치나 `/admin-tools` 같은 경로 접두사 일치로 권한을 판단하지 않는다.

`/admin/**` 진입 결과는 다음과 같다.

| 현재 상태 | 화면 처리 |
|---|---|
| 세션 미확정·메모리 Token 없음 | 재발급을 최대 한 번 시도하고 판정을 보류 |
| 비로그인 확정 | `/login?returnTo={검증된 내부 관리자 경로}`로 이동 |
| `MEMBER` | 관리자 Layout 안에 `403` StatePanel 표시, 재발급·재로그인 반복 없음 |
| `ADMIN` | 요청한 관리자 화면 허용 |

`returnTo`는 같은 Origin의 allowlist에 포함된 내부 경로만 허용한다. 최소 allowlist는 이 문서의 `/admin` 및 정확한 관리자 기능 경로이며, 절대 URL, protocol-relative URL, 역슬래시·이중 인코딩 우회와 allowlist 밖 경로는 폐기하고 기본 `/restaurants`를 사용한다. 이 UI 판정은 편의 기능일 뿐이며 Spring Security가 모든 보호 API의 최종 권한을 다시 검증한다.

관리자 공통 내비게이션의 활성 항목도 정확한 pathname 일치로 정한다. `/admin`은 대시보드 항목만 활성화하며 `/admin/restaurants/new`, `/admin/creators/new`, `/admin/videos/new`, `/admin/visits/new`는 각각 자기 항목만 활성화한다. 별도 `/403` Route를 만들지 않으며, 권한 없음은 관리자 Layout의 접근성 있는 StatePanel로 표현한다.

### 5.4 TanStack Query 세션과 Token 책임

`@tanstack/react-query`는 정확히 `5.101.4`를 사용한다. Query Key `['auth', 'session']`에는 현재 계정의 비밀이 아닌 `id`, `email`, `role`만 둔다. 원문 Access Token은 메모리 전용 인증 모듈에서만 보관하고 Query 캐시·Local Storage·Session Storage에 저장하지 않는다.

- 로그인 성공: 응답의 `role`을 세션 Query에 즉시 반영하고 Access Token을 메모리에 설정한 뒤 `GET /api/me`를 다시 조회해 `id`, `email`, `role`을 완성한다.
- 재발급 성공: 회전된 Access Token과 응답의 최신 `role`을 함께 교체한 뒤 `GET /api/me`로 현재 계정 정보를 다시 검증한다.
- 로그아웃 처리 또는 재발급까지 실패한 확정 `401`: 서버 세션 폐기 성공 여부를 사용자에게 정확히 알리면서도 클라이언트의 Token, 세션 Query와 인증 범위 Query 캐시는 제거한다.
- 서버가 역할 변경을 알린 경우: 오래된 권한 화면을 유지하지 않고 Token과 인증 범위 캐시를 제거한 뒤 최신 세션으로 다시 판정한다.
- `403`: 현재 입력과 화면 맥락을 보존한 채 권한 없음으로 전환하며 재발급 또는 재로그인 loop를 시작하지 않는다.
- `5xx`·네트워크 오류: 로그인 입력 또는 현재 화면을 보존하고 명시적인 재시도 행동을 제공한다. 이를 자격 증명 실패로 표시하거나 세션을 임의로 제거하지 않는다.

브라우저 복원·새로고침과 보호 API `401`에서 재발급은 각각 한 인증 복구 사이클에 최대 한 번만 수행한다. 성공 뒤 원래 요청도 한 번만 재실행한다. 병렬 `401`은 같은 재발급 결과를 공유해야 하며 각 요청이 별도 재발급을 시작해서는 안 된다.

### 5.5 API 인증 matcher와 공개 경로

1. `POST /api/auth/registrations`, `POST /api/auth/email-verifications`, `POST /api/auth/email-verifications/resend`, `POST /api/auth/password-resets/requests`, `POST /api/auth/password-resets/confirmations`, `POST /api/auth/tokens`, `POST /api/auth/tokens/refresh`는 Bearer JWT 없이 각 계약의 자격 증명·일회용 Token·Refresh 쿠키를 검증한다.
2. `DELETE /api/auth/tokens`는 Bearer JWT와 Refresh Token 쿠키를 함께 검증한다. Refresh 쿠키는 `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/auth/tokens`, `Domain` 생략을 사용한다.
3. `GET /api/me`, `DELETE /api/me`와 회원 개인화 API는 통합 Bearer JWT의 현재 계정 권한을 서버 계약대로 검증한다.
4. `/api/admin/**`는 통합 Bearer JWT와 현재 활성 계정의 정확한 `ADMIN` 역할을 요구한다. 과거 `/api/admin/auth/tokens*`는 redirect·alias 없이 정의되지 않은 관리자 경로로 거부한다.
5. `GET /api/restaurants`, `GET /api/restaurants/{restaurantId}`, `GET /api/creators`, `GET /api/creators/{creatorId}`, `GET /api/creators/{creatorId}/restaurants`, `GET /api/creators/{creatorId}/videos`는 인증 없이 허용한다. 공개 유튜버 상세 세 조회는 회원 문맥을 쓰지 않으므로 Bearer Token을 해석하지 않는다.
6. 표에 정의되지 않은 `/api/auth/**`, `/api/me/**`와 그 밖의 정의되지 않은 API 요청은 기본 거부한다.

구체적인 HTTP Method·인증 예외 경로를 먼저 선언하고 포괄적인 보호 matcher를 뒤에 둔다. 정확한 공용 인증 API 경로와 쿠키 Path는 [ADR-AUTH-007](../security/auth-007-unified-account-rbac-session.md) 및 API 계약을 따른다.

### 5.6 상태 확인 경로

| 경로 | 목적 | 의존성 |
|---|---|---|
| `/internal/health/live` | 프로세스 생존 확인 | 없음 |
| `/internal/health/ready` | 핵심 서비스 준비 확인 | PostgreSQL |
| `/internal/health/dependencies` | 장애 원인 구분 | PostgreSQL과 Redis를 개별 표시 |

Docker Healthcheck는 `live`, 배포 후 Smoke Test는 `ready`, CloudWatch Agent와 운영자 점검은 `dependencies`를 사용한다. Kakao와 YouTube 상태는 포함하지 않는다. 응답에는 접속 정보·예외 메시지·비밀값을 넣지 않는다. 세 경로는 애플리케이션 인증 없이 EC2 내부 Agent·컨테이너 네트워크에서만 접근하며, 그 밖의 `/internal/**`은 허용하지 않는다.

## 6. 선택 근거

로그인 전에 역할을 선택하지 않고 서버가 검증한 계정 역할을 사용하는 편이 계정 모델과 일치하고 역할 위조 가능성을 줄인다. 하나의 세션 Query는 헤더와 라우트 가드가 같은 최신 역할을 보게 하며, 비밀 Token을 별도 메모리에 두어 캐시 검사 도구와 영구 저장소 노출을 피한다.

정확한 `/admin` 링크와 경로 allowlist는 관리자 진입을 발견 가능하게 하면서 열린 redirect와 접두사 기반 오판정을 막는다. `401`은 인증 복구, `403`은 권한 거부, `5xx`·네트워크 오류는 일시 장애로 분리해야 사용자가 비밀번호 오류와 서비스 장애를 구분하고 입력·작업 맥락을 잃지 않는다.

## 7. 트레이드오프

메모리 Access Token은 새로고침 때 재발급 호출이 필요하고 Redis 장애 중 보호 화면 복원이 지연된다. 헤더와 라우트 가드는 세션 확인 전 잠깐 보류 상태를 표현해야 한다. `/admin/login` 호환 redirect도 과거 링크를 유지하는 동안 관리해야 한다. 대신 Token 영구 저장과 이중 로그인 폼을 피하고, 역할 변경이 모든 화면에 한 캐시 전이로 반영된다.

## 8. 적용 범위

Next.js App Router, 공통 헤더, 로그인·권한 없음·관리자 화면, TanStack Query 인증 캐시, API Client의 재발급 조정, Spring Security matcher, Refresh Token 쿠키, Nginx, Docker Healthcheck와 운영 점검에 적용한다. M2 검증 참여자 로그인 화면·쿠키·gate는 현재 적용 범위에서 제외한다.

## 9. 강제 규칙

- 로그인 화면은 `/login` 하나이며 역할 선택을 제공하지 않는다.
- 현재 역할은 서버 로그인·재발급 응답에서 받고 `GET /api/me`로 다시 검증해 `['auth', 'session']`에 저장한다.
- Access Token은 메모리에만 보관한다.
- `ADMIN`에게만 정확한 `/admin` 헤더 링크를 표시한다.
- `returnTo`는 같은 Origin의 명시적 내부 allowlist로 검증한다.
- `401` 복구 재발급과 원래 요청 재실행은 각각 한 번으로 제한한다.
- `403`에서 재발급·재로그인 loop를 시작하지 않는다.
- 백엔드는 UI 가드와 무관하게 보호 API 권한을 검증한다.
- `/api/**`만 백엔드 외부 API로 전달하고 `/internal/**`은 인터넷에서 차단한다.

## 10. 금지 사항

회원·관리자 로그인 화면 분리, 로그인 역할 선택, 역할·경로 prefix 비교, 검증하지 않은 `returnTo`, Access Token의 Query·Local Storage·Session Storage 저장, stale role로 관리자 링크 유지, `401`·`403` 무한 재시도, 장애를 자격 증명 실패로 표시, 루트 화면·API 경로 공유, 헤더 기반 라우팅을 금지한다.

## 11. 구현 및 운영 영향

기존 `/admin/login` 링크는 호환 redirect로 바꾸고 신규 링크는 `/login`만 사용한다. 로그인·재발급 응답 DTO에 현재 계정 역할이 포함되어야 하며 프론트엔드 Query Provider와 인증 모듈이 로그인·로그아웃·역할 변경 캐시 전이를 공유한다. 공개 화면과 공개 API는 인증·Redis 장애와 무관하게 계속 사용할 수 있어야 한다.

## 12. 검증 방법

- `/login`에 이메일·비밀번호만 있고 역할 선택이 없는지, `/admin/login`이 안전한 호환 redirect인지 확인한다.
- 비로그인·`MEMBER`·`ADMIN`별 헤더 링크와 `/admin/**` 결과를 검증한다.
- 외부·protocol-relative·인코딩 우회 `returnTo`가 거부되는지 확인한다.
- 로그인·재발급·로그아웃·역할 변경 때 세션 Query, 메모리 Token과 인증 범위 캐시가 함께 전이하는지 확인한다.
- 복원 및 `401` 재발급이 한 번으로 제한되고 병렬 요청이 한 결과를 공유하는지 확인한다.
- `403`은 재로그인 loop 없이 권한 없음으로, `5xx`·네트워크 오류는 입력·현재 화면을 보존한 재시도 상태로 표현되는지 확인한다.
- 헤더와 관리자 내비게이션이 키보드로 동작하고 현재 항목을 `aria-current="page"`로 알리며, 로딩·오류 상태가 적절한 ARIA 상태 메시지를 제공하는지 확인한다.
- 공개 맛집·유튜버 화면이 인증 장애 중에도 유지되고, Spring Security가 UI와 독립적으로 역할을 재검증하는지 확인한다.
- 화면 deep link, `/api/**` 전달, `/internal/**` 외부 차단과 세 상태 확인을 검증한다.

## 13. 재검토 조건

역할 계층이 추가되어 단일 `MEMBER`·`ADMIN` 비교로 표현할 수 없을 때, API를 별도 Origin으로 분리할 때, 서버 주도 세션으로 전환할 때 재검토한다.

## 14. 관련 문서

- [통합 계정 RBAC 세션](../security/auth-007-unified-account-rbac-session.md)
- [프론트엔드 데이터와 상태](web-002-data-state.md)
- [회원 인증 API](../../05-specs/api/account/member-authentication-api.md)
- [보안 경계](../../06-architecture/security-boundary.md)
