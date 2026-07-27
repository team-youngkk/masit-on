---
id: ADR-WEB-003
title: 웹 화면·API·운영 경로 경계
status: Accepted
decision_date: 2026-07-27
owners:
  - 양성훈
  - 김인안
  - 이우람
related_requirements:
  - FR-RESTAURANT-001
  - FR-RESTAURANT-008
  - FR-ADMIN-001
  - NFR-SECURITY-001
  - NFR-AVAILABILITY-001
  - NFR-DEPLOYMENT-002
related_documents:
  - ../../05-specs/api/README.md
  - ../../05-specs/api/admin/authentication-api.md
  - ../../06-architecture/application-flow.md
  - ../../06-architecture/security-boundary.md
  - runtime-001-docker.md
  - web-001-frontend-platform.md
  - web-002-data-state.md
  - ../security/auth-001-spring-security-jwt.md
  - ../data/data-005-redis-refresh-token.md
supersedes: []
superseded_by: null
---

# ADR-WEB-003 웹 화면·API·운영 경로 경계

## 1. 상태

Accepted

## 2. 결정 요약

외부 백엔드 API는 버전 없는 `/api` 접두사 아래에 두고 Nginx는 `/api/**`만 Spring Boot로 전달한다. 나머지 공개 경로는 Next.js가 소유하며 `/internal/**` 상태 확인 경로는 인터넷에 공개하지 않는다. 관리자 화면은 기능별 경로를 사용하고, 메모리 Access Token이 없는 경우 Refresh Token 재발급을 한 번 시도해 인증 상태를 복구한다.

## 3. 배경

단일 EC2에서 Nginx, Next.js와 Spring Boot를 함께 운영하지만 기존 API 계약은 `/restaurants`, `/creators`, `/admin/**`처럼 루트 경로를 사용했다. Next.js도 맛집 목록·상세와 관리자 화면을 같은 경로 계층에 배치해야 하므로 요청 경로만으로 프론트엔드와 백엔드 목적지를 안정적으로 구분할 수 없었다.

또한 `/admin/**` 전체를 JWT 보호 대상으로 표현하면 JWT 발급 전 호출해야 하는 로그인과 Refresh Token 쿠키만 사용하는 재발급까지 차단된다. 상태 확인은 애플리케이션 생존, PostgreSQL 준비 상태와 Redis 부분 장애를 구분해야 하지만 실제 경로와 외부 공개 여부가 정해져 있지 않았다.

## 4. 결정 문제

같은 Origin에서 화면, 공개·관리자 API와 운영 상태 확인을 충돌 없이 라우팅하고 각 경로에 맞는 인증 경계를 어떻게 적용할 것인가.

## 5. 고려한 선택지

- API 접두사 분리: `/api/**`는 Spring Boot, 나머지는 Next.js, `/internal/**`은 내부 전용
- 루트 경로 공유: Nginx가 개별 API 경로 목록이나 요청 헤더를 보고 목적지를 선택
- 별도 API 서브도메인: 웹과 API를 서로 다른 Origin으로 분리

루트 경로 공유는 화면과 API가 추가될 때마다 Nginx allowlist를 동시에 수정해야 하고 `Accept` 헤더 같은 간접 신호에 의존하면 오동작 가능성이 있다. 별도 서브도메인은 경계가 명확하지만 CORS, 쿠키, 인증과 인증서 운영 범위를 늘려 단일 EC2 MVP에 불필요한 부담을 준다.

## 6. 결정

### 6.1 외부 경로 소유권

- `/api/**`: Spring Boot 백엔드 API
- `/internal/**`: Spring Boot 운영 상태 확인, Nginx 인터넷 진입점에서는 차단
- 그 외 경로: Next.js 화면·정적 자산
- API 버전 접두사 `/v1`은 도입하지 않는다.

Nginx는 경로 접두사만으로 목적지를 정하며 요청 헤더, 사용자 에이전트나 응답 형식으로 프론트엔드와 백엔드를 구분하지 않는다.

### 6.2 화면 경로

| 경로 | 동작 |
|---|---|
| `/` | `/restaurants`로 이동 |
| `/restaurants` | 맛집 검색·필터·목록 |
| `/restaurants/{restaurantId}` | 맛집 상세 |
| `/admin/login` | 관리자 로그인 |
| `/admin` | `/admin/restaurants/new`로 이동 |
| `/admin/restaurants/new` | 맛집 등록 |
| `/admin/creators/new` | 유튜버 등록 |
| `/admin/videos/new` | 영상 등록 |
| `/admin/visits/new` | 방문 관계 등록 |

관리자 화면은 공통 레이아웃과 메뉴를 공유하지만 기능별 URL을 유지한다.

### 6.3 관리자 API 인증 순서

1. `POST /api/admin/auth/tokens`: 로그인 자격 증명만 검증하고 JWT를 요구하지 않는다.
2. `POST /api/admin/auth/tokens/refresh`: Refresh Token 보안 쿠키만 검증하고 Bearer JWT를 요구하지 않는다.
3. `DELETE /api/admin/auth/tokens`: Bearer JWT와 Refresh Token 보안 쿠키를 모두 요구한다.
4. 나머지 `/api/admin/**`: Bearer JWT와 `ADMIN` 권한을 요구한다.
5. `GET /api/restaurants`, `GET /api/restaurants/{restaurantId}`, `GET /api/creators`: 인증 없이 허용한다.
6. 정의되지 않은 API 요청: 기본 거부한다.

구체적인 matcher는 더 구체적인 경로와 HTTP Method를 먼저 선언하고 포괄적인 `/api/admin/**` 규칙을 뒤에 둔다.

### 6.4 관리자 인증 상태 복구

Access Token은 브라우저 메모리에만 둔다. `/admin/**` 화면 진입이나 새로고침 시 메모리에 Access Token이 없으면 `POST /api/admin/auth/tokens/refresh`를 한 번 호출한다. 성공하면 새 Access Token을 메모리에 저장하고 현재 화면을 유지하며, 실패하면 `/admin/login`으로 이동한다.

관리자 API의 `401`에도 재발급과 원래 요청 재실행은 각각 한 번만 허용한다. 다시 실패하면 로그인 화면으로 이동하고 무한 재시도하지 않는다. 프론트엔드 라우트 가드는 화면 노출을 제어할 뿐이며 최종 권한 판정은 항상 Spring Security가 수행한다.

Refresh Token 쿠키는 `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/admin/auth`를 사용한다.

### 6.5 상태 확인 경로

| 경로 | 목적 | 의존성 |
|---|---|---|
| `/internal/health/live` | 프로세스 생존 확인 | 없음 |
| `/internal/health/ready` | 핵심 서비스 준비 확인 | PostgreSQL |
| `/internal/health/dependencies` | 장애 원인 구분 | PostgreSQL과 Redis를 개별 표시 |

Docker Healthcheck는 `live`, 배포 후 Smoke Test는 `ready`, CloudWatch Agent와 운영자 점검은 `dependencies`를 사용한다. Kakao와 YouTube 상태는 포함하지 않는다. 응답은 상태 구분에 필요한 최소 정보만 제공하고 접속 정보, 예외 메시지와 비밀값을 노출하지 않는다.

세 상태 확인 경로는 애플리케이션 인증 없이 호출할 수 있지만 EC2 내부 Agent·컨테이너 네트워크에서만 접근할 수 있다. 그 밖의 `/internal/**`은 허용하지 않는다.

## 7. 선택 근거

단일 `/api` 경계는 새 API가 추가돼도 Nginx 규칙을 바꾸지 않고 화면과 API의 이름 충돌을 제거한다. 같은 Origin을 유지하므로 MVP에서 CORS와 별도 도메인 쿠키 운영도 피할 수 있다. `/internal`을 외부 진입점에서 차단하면 운영 상태를 세분화하면서 내부 의존성 정보를 공개하지 않을 수 있다.

인증 matcher를 Method와 세부 경로 단위로 먼저 선언하면 로그인·재발급 예외와 관리자 보호 API가 동시에 성립한다. 메모리 Access Token을 재발급으로 한 번 복구하는 흐름은 영구 브라우저 저장소를 사용하지 않으면서 새로고침 후 관리자 작업을 이어갈 수 있게 한다.

## 8. 트레이드오프

기존 API 계약의 모든 경로와 클라이언트 호출부를 `/api` 기준으로 함께 변경해야 한다. 상태 확인은 Nginx 외부 경로에서 직접 호출할 수 없으므로 EC2 내부 Agent나 컨테이너 네트워크를 통해 점검해야 한다. 메모리 Access Token은 새로고침마다 Redis 재발급 호출을 발생시키며 Redis 장애 시 관리자 화면 복구가 실패한다.

## 9. 적용 범위

Next.js App Router, Spring MVC Controller, Spring Security Filter Chain, Refresh Token 쿠키, Nginx 리버스 프록시, Docker Healthcheck, 배포 Smoke Test와 CloudWatch 운영 점검에 적용한다.

## 10. 강제 규칙

- 외부 API 계약은 `/api`로 시작한다.
- 화면 경로와 API 경로를 요청 헤더로 구분하지 않는다.
- 인증 예외 matcher는 포괄적인 관리자 matcher보다 먼저 평가한다.
- `/internal/**`은 인터넷 Nginx 경로로 전달하지 않는다.
- Refresh Token 재발급과 원래 요청 재실행은 한 번으로 제한한다.
- 백엔드는 프론트엔드 라우트 가드와 무관하게 모든 보호 API를 검증한다.

## 11. 금지 사항

루트 화면 경로와 루트 API 경로 공유, `Accept`·User-Agent 기반 목적지 선택, Access Token의 Local Storage·Session Storage 저장, 재발급 무한 반복, 외부 제공자 상태를 liveness/readiness에 포함하는 것을 금지한다.

## 12. 구현 및 운영 영향

API 계약, 프론트엔드 API Client Base URL, Spring Security matcher, Refresh Token Cookie Path와 Nginx 설정을 동시에 변경한다. Nginx 설정 검증에는 화면 deep link, `/api` 전달, `/internal` 외부 차단을 포함한다. 상태 확인은 공개 요청 오류율과 별도 운영 지표로 수집한다.

## 13. 검증 방법

- 화면 deep link와 새로고침이 Next.js로 전달되는지 확인한다.
- 13개 API가 `/api` 아래에서만 백엔드로 전달되는지 확인한다.
- 로그인·재발급 예외와 나머지 관리자 API의 `401`·`403`을 검증한다.
- 메모리 Token 소실 뒤 재발급 성공·실패와 단일 재시도 제한을 검증한다.
- `/internal/**` 외부 요청이 차단되고 컨테이너 내부에서는 세 상태 확인이 가능한지 검증한다.
- PostgreSQL 장애는 `ready`와 `dependencies`, Redis 장애는 `dependencies`에서 구분되는지 확인한다.

## 14. 재검토 조건

API를 별도 서브도메인이나 독립 배포로 분리할 때, 외부 Load Balancer가 상태 확인을 직접 호출해야 할 때, API 버전 병행 운영이 필요할 때 재검토한다.

## 15. 관련 문서

- [API 공통 규칙](../../05-specs/api/README.md)
- [관리자 인증 API](../../05-specs/api/admin/authentication-api.md)
- [애플리케이션 흐름](../../06-architecture/application-flow.md)
- [보안 경계](../../06-architecture/security-boundary.md)
- [Docker 실행 환경](runtime-001-docker.md)
- [프론트엔드 플랫폼](web-001-frontend-platform.md)
- [프론트엔드 데이터와 상태](web-002-data-state.md)
