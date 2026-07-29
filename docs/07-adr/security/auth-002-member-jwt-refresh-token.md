---
id: ADR-AUTH-002
title: 일반 회원 JWT와 Refresh Token 세션
status: Accepted
decision_date: 2026-07-29
last_reviewed: 2026-07-29
owners:
  - 김인안
related_requirements:
  - FR-AUTH-001
  - FR-AUTH-002
  - FR-AUTH-003
  - FR-MEMBER-001
  - FR-MEMBER-002
  - FR-MEMBER-003
  - FR-MEMBER-004
  - FR-MEMBER-005
  - NFR-SECURITY-004
  - NFR-SECURITY-005
  - NFR-PRIVACY-003
related_documents:
  - ../../00-overview/scope.md
  - ../../05-specs/api/account/member-authentication-api.md
  - ../../05-specs/api/common/authentication-contract.md
  - ../../05-specs/api/personal/personal-restaurant-api.md
  - ../../05-specs/data/README.md
  - ../../05-specs/data/table-definitions.md
  - ../../05-specs/data/constraint-mapping.md
  - ../../05-specs/data/lifecycle-rules.md
  - ../../06-architecture/security-boundary.md
  - ../platform/web-003-routing-boundary.md
  - auth-001-spring-security-jwt.md
  - ../data/data-005-redis-refresh-token.md
supersedes: []
superseded_by: null
---

# ADR-AUTH-002 일반 회원 JWT와 Refresh Token 세션

## 1. 상태

Accepted

2026-07-29 요구사항·API·데이터 계약 확정 결과를 동기화했다. 기존의 관리자·회원 인증 분리, RS256 JWT, Redis Refresh 세션과 최대 3세션이라는 결론은 변경하지 않았다.

현재 `develop`에는 V2 회원 보안 데이터, 회원 JWT 발급과 Redis 세션 기반만 구현돼 있다. 이 문서의 계정 상태·`sid` 폐기 표식 조회, 회원 인증 API, 요청 제한 HMAC, 신뢰 프록시와 Origin 검증은 후속 구현이 완료되기 전까지 현재 적용된 보안 통제로 간주하지 않는다.

## 2. 결정 요약

일반 회원은 `MEMBER` authority를 가진 RS256 JWT Access Token과 Redis Refresh Token 세션을 사용한다. 관리자 인증과 암호화 기반·서명 검증 구현은 재사용하되 경로, principal, audience, 쿠키와 Redis namespace는 분리한다. Access Token은 30분, Refresh Token은 회전 시점부터 14일간 유효하며 회원 한 명당 활성 세션은 최대 3개다.

Redis `auth:member:` namespace에는 Refresh Token 원문이 아니라 SHA-256 해시와 세션·회전 상태만 저장한다. 회전과 재사용 탐지는 원자적으로 처리한다. 로그아웃·비밀번호 재설정·탈퇴의 폐기 효과는 회원 계정 상태 확인과 PostgreSQL `sid` 폐기 표식으로 보강하여 Redis 장애나 지연 중에도 이미 폐기한 세션이 복구되지 않게 한다.

## 3. 배경

1차 확장에 회원가입·로그인과 본인 찜·최근 본 맛집이 포함됐다. 기존 관리자 인증은 사전 발급된 단일 `ADMIN` 계정과 활성 세션 1개를 전제로 하므로 그대로 회원 신원과 개인화 자원에 적용할 수 없다.

## 4. 결정

### 4.1. 관리자 인증과의 공통·분리 경계

- 관리자와 회원은 Spring Security, RS256 서명·검증 기반, 공통 오류·비밀정보 보호 원칙을 재사용할 수 있다.
- 회원 인증 경로는 `/api/auth/**`와 `/api/me/**`, 관리자 인증 경로는 `/api/admin/**`다. 정의되지 않은 인증 경로는 기본 거부한다.
- `MemberPrincipal`과 `AdminPrincipal`, Security matcher, JWT audience, Refresh 쿠키 이름·Path, Redis namespace와 세션 상한은 분리한다.
- 관리자 `aud=masit-on-admin-api` Token은 회원 경계에서, 회원 `aud=masit-on-member-api` Token은 관리자 경계에서 `401`로 거부한다. 한쪽의 로그아웃·회전·재사용 탐지가 다른 쪽 Redis 상태를 읽거나 폐기해서는 안 된다.
- 회원은 `MEMBER` authority, 다수 계정과 계정당 최대 3세션을 전제로 한다. 사전 발급 `ADMIN` 단일 역할과 계정당 1세션인 관리자 정책을 회원에게 상속하지 않는다.

### 4.2. Access Token

- Access Token은 `iss=masit-on`, `aud=masit-on-member-api`, 만료 30분인 RS256 JWT다.
- 최소 claim은 회원 식별자 `sub`, `iss`, `aud`, 로그인 세션의 불투명 식별자 `sid`, `iat`, `exp`, Token별 식별자 `jti`다. 같은 로그인 세션에서 발급한 모든 Access Token과 Redis Refresh 상태, PostgreSQL 폐기 표식은 동일한 `sid`를 공유한다.
- `jti`는 Access Token마다 새로 발급하며 세션 식별·폐기에 사용하지 않는다. 이메일, 비밀번호, 계정 상태와 개인화 데이터는 claim에 넣지 않는다.
- Access Token은 응답 본문으로 전달하고 브라우저 메모리에만 보관해 `Authorization: Bearer`로 전송한다.

### 4.3. Refresh Token과 Redis 세션

- Refresh Token은 만료 14일의 고엔트로피 불투명 값이며 Redis `auth:member:` namespace에 SHA-256 해시와 세션 상태를 저장한다.
- 회원 Refresh 쿠키 이름은 `__Secure-masiton-member-refresh`다. `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/auth/tokens`를 사용하고 `Domain`은 지정하지 않는다.
- Redis 세션은 `sid`, `memberId`, `familyId`, `currentTokenId`, `createdAt`, `expiresAt`을 가진다. 회전 Token 상태는 `tokenId`, `sid`, `familyId`, 32-byte Refresh 해시, `ISSUED/USED/REVOKED`, `expiresAt`을 가진다. 회원별 세션 순서에는 `sid`와 최초 `createdAt`을 보관한다.
- Refresh 원문과 Access JWT 원문은 저장하지 않는다. 회전할 때 사용한 `ISSUED` Token을 `USED`로 바꾸고 새 Token과 세션의 `currentTokenId`·`expiresAt`을 함께 갱신한다. 회전된 쿠키와 새 Refresh Token은 그 시점부터 14일간 유효하다.
- 이미 `USED/REVOKED`인 Token 해시가 다시 제출되거나 제출 Token이 `currentTokenId`와 다르면 재사용으로 탐지하고 해당 `familyId` 전체를 폐기한다.
- 로그인 생성, 회전, 재사용 탐지, 세션 전체 폐기와 회원별 세션 상한 적용은 Lua script 또는 동등한 Redis 단일 원자 연산으로 수행한다. 같은 Refresh Token의 동시 재발급은 한 요청만 성공한다.
- 회원 한 명당 활성 세션은 최대 3개다. 네 번째 로그인이 성공하면 최초 생성 시각이 가장 오래된 활성 세션을 폐기하고 나머지 세션은 유지한다. Refresh Token 회전은 이 정렬 순서를 바꾸지 않는다.

회원 인증 요청 제한 key에는 원문 이메일이나 클라이언트 주소를 넣지 않는다. 정규화 이메일과 신뢰된 클라이언트 주소는 각각 `HMAC-SHA-256(rateLimitSecret, value)`의 고정 길이 소문자 hex로 변환한다. `rateLimitSecret`은 JWT 서명키와 Token 해시 용도에서 분리하고 로그에 남기지 않는다. 요청 출처는 신뢰된 Nginx가 외부 전달 헤더를 덮어써 제공한 단일 주소만 사용하며 Spring Boot는 지정된 Nginx peer의 전달 헤더만 신뢰한다.

### 4.4. 폐기와 계정 상태

- 회원용 보호 API와 Refresh 검증은 Token의 암호학적 유효성만으로 허용하지 않고 현재 `member_account.status`가 `ACTIVE`인지 확인한다. `PENDING_VERIFICATION`, `DISABLED`, `DELETION_PENDING`과 삭제된 계정은 거부한다. 특히 탈퇴 요청으로 `DELETION_PENDING`이 저장된 직후부터 기존 Access Token도 사용할 수 없다.
- 모든 회원 Bearer 인증은 JWT의 `sid`로 PostgreSQL `member_session_revocation`을 조회한다. 같은 `sid`의 폐기 표식이 있으면 Access Token의 서명과 만료가 유효해도 `401 AUTHENTICATION_REQUIRED`로 거부한다. 이 조회나 계정 상태 확인을 수행할 수 없으면 보호 API는 `503 AUTHENTICATION_SERVICE_UNAVAILABLE`로 fail-closed 처리한다.
- 로그아웃은 현재 `sid`만 폐기하고 다른 활성 세션은 유지한다. 비밀번호 재설정 완료와 회원 탈퇴는 해당 회원의 모든 활성 세션과 남은 인증·재설정 Token을 폐기한다.
- 서버는 세션 폐기 시 PostgreSQL `member_session_revocation`에 `sid`, 최초 폐기 시각과 보호 만료 시각을 저장한다. 같은 `sid`의 재시도는 `revoked_at=LEAST(기존값, 입력값)`, `expires_at=GREATEST(기존값, 입력값)`인 멱등 upsert로 처리하여 폐기 시작 시점을 늦추거나 보호 기간을 줄이지 않는다.
- 폐기 표식에는 회원 FK·이메일을 저장하지 않는다. 탈퇴로 회원을 물리 삭제한 뒤에도 필요한 최대 14일 동안 유지하고 `expires_at` 이후 물리 삭제한다.
- Refresh 검증은 Redis 세션이 존재해도 PostgreSQL에 같은 `sid` 표식이 있으면 거부하고 해당 Redis 세션을 정리한다. 회원 Bearer 인증과 Refresh 검증이 같은 세션 폐기 근거를 사용하므로 로그아웃·세션 상한 퇴출·재사용 탐지·비밀번호 재설정 뒤 남은 Access·Refresh Token이 함께 차단된다.

### 4.5. 장애 처리

- Redis 장애 중 로그인·재발급은 fail-closed다. 로그아웃은 서버 폐기가 확인된 경우에만 성공이며, 확인하지 못하면 성공으로 가장하지 않는다.
- 로그아웃은 Redis 폐기 전에 Bearer JWT의 `sid`를 PostgreSQL 폐기 표식으로 저장한다. 표식 저장 실패는 `500`, Redis 폐기 확인 실패는 `503`이며 둘 다 성공으로 응답하지 않는다. 클라이언트는 메모리 Access Token을 제거하고 회원 Refresh 쿠키를 만료하며 서버 폐기는 복구 뒤 재시도한다.
- 비밀번호 재설정은 현재 활성 `sid`를 모두 식별하고 각각의 PostgreSQL 폐기 표식을 저장한 뒤에만 비밀번호 변경을 완료한다. 세션 열거·표식 저장·Redis 정리를 포함한 전체 폐기를 보장할 수 없으면 완료하지 않고 `503`으로 실패한다.
- 탈퇴는 먼저 계정을 `DELETION_PENDING`으로 바꿔 인증을 차단한 뒤 세션·Action Token·개인화 데이터를 정리한다. 일부 정리가 실패하면 15분마다 재시도하고 1시간 미완료 시 알림을 발생시키며 요청 뒤 24시간 안에 완료하거나 수동 복구한다. 완료 전에는 동일 이메일 재가입을 막는다.
- Redis 장애는 회원 로그인·재발급과 인증 상태 변경을 차단하지만 무인증 공개 맛집 조회를 차단하지 않는다.
- 회원 Refresh 쿠키를 사용하는 재발급·로그아웃은 `SameSite=Strict`와 HTTPS 동일 Origin 검증을 함께 적용하고 credentialed CORS를 임의 Origin에 허용하지 않는다.

## 5. 경로와 인가 경계

회원가입·이메일 인증·비밀번호 재설정·로그인·재발급은 기능별로 정의된 `/api/auth/**` 경로만 허용한다. 로그아웃은 회원 Bearer JWT와 회원 Refresh 쿠키를 요구한다. 현재 회원 정보·탈퇴와 개인화 자원은 `/api/me` 및 `/api/me/**` 아래에 두고 `MemberPrincipal`로만 본인을 결정한다.

공개 조회는 Authorization 헤더가 없으면 계속 무인증으로 동작한다. 헤더를 보냈지만 JWT가 만료·변조됐거나 audience가 다르면 인증된 회원으로 취급하지 않고 `401`을 반환한다.

## 6. 선택 근거

짧은 Access Token과 회전 가능한 Refresh Token은 공개 조회의 무상태 특성을 유지하면서 세션 수·재사용·탈퇴를 서버에서 통제한다. 관리자와 기술 구현을 공유하되 인증 주체의 namespace를 분리하면 한쪽 장애·폐기가 다른 쪽 세션을 손상시키는 것을 막는다.

Redis는 만료와 원자 회전에 적합하지만 장애 중 폐기 증거까지 Redis에만 의존하면 복구 뒤 로그아웃된 세션이 살아날 수 있다. 회원 식별정보가 없는 PostgreSQL `sid` 표식을 보조 저장소로 사용하면 이 위험을 줄이면서 탈퇴 후 개인정보 최소화도 유지할 수 있다.

## 7. 검토한 선택지와 트레이드오프

| 선택지 | 판단 | 이유와 비용 |
|---|---|---|
| 관리자 인증을 그대로 회원에게 사용 | 기각 | 단일 관리자 역할·1세션 정책이 회원 신원과 개인화 자원에 맞지 않고 Token·쿠키 오인 위험이 있다. |
| 회원·관리자 인증 서비스를 완전히 별도 구현 | 기각 | principal과 상태 저장은 분리해야 하지만 RS256 검증·비밀정보 보호까지 중복하면 유지보수와 설정 불일치 위험이 커진다. |
| Access Token만 사용하는 완전 무상태 인증 | 기각 | 로그아웃, 최대 3세션, 비밀번호 재설정·탈퇴와 재사용 탐지를 통제하기 어렵다. |
| Refresh 상태를 PostgreSQL에만 저장 | 기각 | 가능하지만 짧은 TTL, 빈번한 회전, 제한 카운터와 원자 세션 퇴출에 Redis보다 운영 부담이 크다. |
| Refresh 원문을 Redis에 저장 | 기각 | 저장소·로그 유출 시 즉시 자격 증명이 노출된다. 해시와 최소 상태만 저장한다. |
| Redis만으로 폐기 처리 | 기각 | Redis 장애·지연 중 폐기 확인과 복구 뒤 재사용 차단을 보장하지 못한다. |
| 모든 Access `jti`를 폐기 목록에 저장 | 기각 | 세션 단위 동작에 불필요한 저장량과 조회 비용을 만든다. 로그인 세션 `sid` 하나로 폐기한다. |
| 회원 RS256 Access + Redis Refresh + PostgreSQL `sid` 표식 | 채택 | 무상태 공개 조회와 서버 통제 세션을 결합하고 장애 중 폐기 증거를 보존한다. |

세션 3개와 Token 계열 추적 때문에 관리자 단일 세션보다 Redis 자료 구조, 원자 연산과 동시성 테스트가 복잡하다. 보호 API의 계정 상태 조회와 폐기 표식 조회도 완전 무상태 JWT보다 비용이 든다. 그 대신 탈퇴·비활성 전환과 Redis 장애에서 최대 30분 동안 Access Token 권한이 남는 문제를 방지한다.

## 8. 강제 규칙

- Access·Refresh Token을 localStorage·sessionStorage에 저장하지 않는다.
- Token 원문, Authorization·Cookie 헤더, 이메일과 비밀번호를 로그에 남기지 않는다.
- 관리자와 회원 JWT audience, principal, 쿠키 이름·path, Redis namespace를 교차 사용하지 않는다.
- `jti`를 회원 세션 식별자로 재사용하지 않고 `sid` 회전 없이 Refresh Token만 회전한다.
- `/api/me/**`는 URL이나 요청 본문으로 다른 회원 식별자를 받지 않는다.
- Refresh 회전, 재사용 탐지와 세션 3개 제한은 Redis 원자 연산으로 검증한다.
- 제한 저장소 key와 로그에 원문 이메일·클라이언트 주소를 남기지 않고 용도 분리된 HMAC 결과만 사용한다.
- Refresh 쿠키 요청은 배포 Origin allowlist 불일치·브라우저 Origin 누락을 `403`으로 거부한다.
- `/api/me`와 `/api/me/**` 응답은 성공·오류 모두 `Cache-Control: private, no-store`다.
- 보호 API·Refresh 검증 시 `DELETION_PENDING`을 포함한 비활성 계정 상태를 거부한다.
- 모든 회원 Bearer 인증은 `sid` 폐기 표식을 확인하고 표식 조회 실패 또는 일치 시 fail-closed로 거부한다.
- PostgreSQL 세션 폐기 표식 저장 실패나 Redis 폐기 미확인을 로그아웃 성공으로 처리하지 않는다.
- 폐기 표식의 재저장은 `LEAST(revoked_at)`와 `GREATEST(expires_at)`로 기존 보호 범위를 축소하지 않는다.

## 9. 구현 및 운영 영향

`MemberPrincipal`, 회원용 JWT 검증기·쿠키 처리·계정 상태 조회·Redis 세션 저장 Adapter와 matcher가 추가된다. 공통 암호화 유틸리티는 재사용할 수 있지만 관리자 key 구조를 변경하거나 기존 관리자 세션을 마이그레이션하지 않는다.

운영에서는 회원 Redis namespace별 세션 생성·회전·재사용 탐지·상한 퇴출·전체 폐기 실패, PostgreSQL 폐기 표식 저장 실패와 만료 정리 지연을 구분해 관측한다. 로그에는 `traceId`와 비식별 결과만 남기고 Token·Cookie·Authorization, 이메일, HMAC 입력·비밀은 남기지 않는다. `rateLimitSecret`과 JWT 개인키는 별도 비밀로 배포·회전한다.

탈퇴 정리 작업은 15분 재시도, 1시간 알림과 24시간 복구 목표를 가진다. 폐기 표식 만료 정리가 지연돼도 인증 허용으로 이어지지 않지만 불필요한 보안 데이터가 남으므로 정리 지연을 운영 지표로 감시한다.

## 10. 검증 방법

- V1에서 V2로 전진 적용되는 Flyway migration을 PostgreSQL Testcontainers로 검증한다.
- 정상·만료·변조·잘못된 issuer/audience JWT와 `sid`·`jti` 구분, 회원·관리자 Token·쿠키·Redis 상태의 양방향 교차 사용 거부를 검증한다.
- Access 30분과 Refresh 14일의 직전·정확한 경계·직후, 회전 뒤 다시 부여한 14일 TTL을 검증한다.
- 같은 Refresh Token 동시 재발급에서 한 요청만 성공하고 이전 Token은 `USED`, 세션 `currentTokenId`는 새 Token을 가리키며 재사용 시 family 전체가 폐기되는지 검증한다.
- 동시 네 번째 로그인 뒤 가장 오래된 `sid`만 폐기되고 활성 세션은 3개인지 검증한다.
- 로그인·회원가입·재설정 제한 key가 원문 이메일·주소를 포함하지 않고 동일 입력은 같은 HMAC key, 다른 용도의 비밀은 다른 결과를 만드는지 검증한다.
- 로그아웃 표식의 동일 `sid` 재시도와 역순 입력에서 `LEAST(revoked_at)`, `GREATEST(expires_at)`이 유지되고 표식 저장 직후 기존 Access Token이 `401`, Redis 복구 뒤 Refresh 세션이 거부·정리되는지 검증한다.
- 네 번째 로그인에 따른 세션 퇴출, Refresh 재사용 탐지, 비밀번호 재설정과 탈퇴가 대상 `sid` 표식을 남기고 기존 Access Token을 즉시 거부하는지 검증한다. 비밀번호 재설정은 모든 세션의 표식을 보장할 수 없으면 비밀번호도 변경되지 않아야 한다.
- PostgreSQL 표식 실패, Redis 장애, 탈퇴 부분 실패를 주입해 상태 코드, fail-closed, 재시도·알림·수동 복구와 공개 조회 격리를 검증한다.
- Token·Cookie·Authorization, 이메일, 클라이언트 주소와 HMAC 비밀이 저장소·응답·로그에 남지 않는지 검사한다.

## 11. 재검토 조건

소셜 로그인, 다중 기기 이름 관리, Token별 `jti` 폐기 목록, 세션 원격 조회·개별 종료 또는 별도 인증 서비스가 범위에 들어올 때 재검토한다. 현재 회원 세션 단위 즉시 폐기는 PostgreSQL `sid` 표식으로 이미 적용한다.

## 12. 관련 문서

- [회원 계정·인증 API](../../05-specs/api/account/member-authentication-api.md)
- [공통 인증 계약](../../05-specs/api/common/authentication-contract.md)
- [개인 맛집 관리 API](../../05-specs/api/personal/personal-restaurant-api.md)
- [데이터 명세](../../05-specs/data/README.md)
- [테이블 및 Redis 구조](../../05-specs/data/table-definitions.md)
- [제약 매핑](../../05-specs/data/constraint-mapping.md)
- [데이터 생명주기](../../05-specs/data/lifecycle-rules.md)
- [보안 경계](../../06-architecture/security-boundary.md)
- [웹·API 라우팅 경계](../platform/web-003-routing-boundary.md)
