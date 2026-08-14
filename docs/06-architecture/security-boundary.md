---
related_documents:
  - architecture-overview.md
  - package-structure.md
  - application-flow.md
  - ../05-specs/api/admin/authentication-api.md
  - ../05-specs/api/admin/reference-data-api.md
  - ../05-specs/api/admin/visit-registration-api.md
  - ../07-adr/security/auth-001-spring-security-jwt.md
  - ../07-adr/security/auth-006-cookie-origin-defense.md
  - ../07-adr/security/auth-003-confirmation-token.md
  - ../07-adr/data/data-005-redis-refresh-token.md
  - ../07-adr/security/sec-001-secrets-workload-identity.md
  - ../07-adr/platform/web-003-routing-boundary.md
  - ../07-adr/platform/deploy-004-public-api-validation-gate-boundary.md
  - ../05-specs/api/common/validation-access-contract.md
---

# 보안 경계

## 1. 인증과 인가 분리

- **인증**: 요청의 JWT가 유효한 관리자 신원을 나타내는지 확인한다.
- **역할 인가**: 인증된 주체가 `ADMIN` 권한을 가지는지 확인한다.
- **업무 인가·규칙**: 해당 요청이 공개 참조, 근거 확인, 중복 등 유스케이스 조건을 만족하는지 Application/Domain이 확인한다.

Spring Security Filter Chain은 인증과 역할 인가를 담당한다. Controller와 Domain이 JWT를 직접 파싱하지 않는다.

## 2. 요청 유형별 경계

검증 참여자 제한 공개는 서비스 권한보다 앞선 임시 운영 진입 경계다. `__Host-masiton-verification` HttpOnly 쿠키를 Nginx `auth_request`와 내부 Spring Adapter가 확인하며, 회원·관리자 Principal을 만들지 않는다. 검증 쿠키가 유효해도 각 보호 API는 기존 회원·관리자 Bearer JWT를 다시 검증한다. Basic Auth와 `WWW-Authenticate: Basic`은 사용하지 않는다.

| 요청 | 인증 | 역할 | Application 추가 검증 |
|---|---|---|---|
| 공개 맛집·Creator 조회 | 없음 | 없음 | 공개·유효 상태 |
| 관리자 로그인 | 자격 증명 | 사전 발급 활성 계정 | 실패 제한, Token 발급 |
| Token 재발급 | Refresh Token 쿠키 | 활성 관리자 계정 | Redis 대조·회전·재사용 탐지 |
| 로그아웃 | Bearer JWT + Refresh Token 쿠키 | `ADMIN` | 현재 Refresh Token 폐기 |
| 관리자 등록 미리보기 | Bearer JWT | `ADMIN` | 입력, 외부 확인, 중복 판정 |
| 관리자 생성 확정 | Bearer JWT | `ADMIN` | 확인 Token 관리자·후보·만료 |
| Visit 등록 | Bearer JWT | `ADMIN` | 세 참조, 공개, 채널 일치, 근거·중복 |

## 3. 계층별 책임

### Security Infrastructure

- Security Filter Chain URL 정책
- Bearer JWT 추출과 서명·issuer·audience·만료 검증
- `ADMIN` authority 변환
- 인증 실패·권한 실패 Handler
- Password Encoder, JWT 서명과 Redis Token Adapter
- Refresh Token 보안 쿠키 속성

URL matcher는 다음 순서로 평가한다.

1. `POST /api/admin/auth/tokens`: 로그인 자격 증명
2. `POST /api/admin/auth/tokens/refresh`: Refresh Token 쿠키
3. `DELETE /api/admin/auth/tokens`: Bearer JWT + Refresh Token 쿠키
4. 공개 `GET /api/restaurants`, `GET /api/restaurants/{restaurantId}`, `GET /api/restaurants/popular`, `GET /api/creators`, `GET /api/creators/{creatorId}`, `GET /api/creators/{creatorId}/restaurants`, `GET /api/creators/{creatorId}/videos`, `GET /api/curations`, `GET /api/curations/{curationId}`: 무인증. 단, 맛집 상세는 유효한 회원 Bearer Token이 있을 때 최근 기록용 문맥만 선택적으로 사용한다. 인기 맛집·유튜버 상세 세 조회·큐레이션 두 조회는 회원 문맥을 쓰지 않으므로 Bearer Token을 해석하지 않는다.
5. 나머지 `/api/admin/**`: Bearer JWT + `ADMIN`
6. 정의되지 않은 `/api/**`: 기본 거부

`/internal/health/live`, `/internal/health/ready`, `/internal/health/dependencies`는 애플리케이션 인증 없이 호출할 수 있지만, 인증 예외보다 앞선 네트워크 경계에서 인터넷 Nginx 전달을 차단하고 EC2 내부 Agent·컨테이너에서만 호출한다. 그 밖의 `/internal/**`은 허용하지 않는다.

회원 인증은 관리자와 별도 JWT audience(`masit-on-member-api`), `MEMBER` authority, `MemberPrincipal(memberId, sessionId)`를 사용한다. 공개 회원 인증 메서드를 제외한 `/api/auth/**`와 `/api/me/**`에는 회원 decoder만, `/api/admin/**`에는 관리자 decoder만 적용해 교차 audience를 인증 단계에서 거부한다. `GET /api/restaurants/{restaurantId}`는 `permitAll`을 유지하며, 선택적 회원 Token 해석기는 유효한 회원 Token에서만 최근 기록용 문맥을 제공한다. 인기 맛집·유튜버 상세 세 조회·큐레이션 두 조회는 회원 부수효과가 없어 Token 해석 대상에서 제외한다. 만료·변조·다른 audience Token이 섞여 들어와도 401 없이 공개 응답을 반환해야 공개 계약이 인증 상태에 종속되지 않는다. 누락·만료·변조·폐기·교차 audience Token, 회원 인증 상태 조회 실패와 최근 기록 저장 실패는 principal·오류 응답을 만들지 않고 익명 공개 조회와 최근 기록 생략으로 끝낸다.

### Presentation

- 로그인 입력의 형식 검증
- 인증된 `AdminPrincipal`을 Application 입력에 전달
- `401`, `403`과 안전한 오류 응답 연결
- JWT claim이나 SecurityContext 세부 타입을 업무 DTO에 노출하지 않음

### Application

- 사전 발급 계정의 활성 상태
- 로그인 실패 제한, Refresh Token 회전·폐기
- 관리자 식별자와 확인 Token의 소유자 일치
- 관리자만 시작할 수 있는 등록 순서 조정
- 공개 참조·중복·업무 규칙 오류 구분

### Domain

- JWT, Role과 Spring Security를 알지 못함
- 인증된 요청이라는 전제 아래 자기 도메인의 불변 조건만 판단
- Restaurant·Creator·Video·Visit 공개 상태와 생성 규칙 소유

## 4. 관리자 유스케이스 경계

Admin을 최상위 비즈니스 패키지로 만들지 않는다.

| 기능 | 위치 |
|---|---|
| Restaurant 미리보기·생성 | `restaurant.presentation.admin` → `restaurant.application` |
| Creator 미리보기·생성 | `creator.presentation.admin` → `creator.application` |
| Video 미리보기·생성 | `video.presentation.admin` → `video.application` |
| Visit 등록 | `orchestration.presentation.visit` → `orchestration.application.command` |
| 로그인·재발급·로그아웃 | `security.presentation` → `security.application` |

모든 관리자는 동일한 등록 권한을 가진다. 현재 MVP에는 데이터별 소유 관리자나 세분 권한이 없으므로 “본인이 만든 Restaurant만 수정” 같은 리소스 소유권 검증을 추가하지 않는다. 수정·삭제·계정 관리도 MVP 제외다.

## 5. Principal 전달

Application에는 Spring Security의 `Authentication` 전체 대신 최소 Principal을 전달한다.

```java
// common.security
public record AdminPrincipal(
        AdminId adminId,
        Set<AdminRole> roles
) {}
```

- 비밀번호, Access/Refresh Token 원문과 JWT 서명 정보는 포함하지 않는다.
- Domain 객체에 Principal을 저장하지 않는다.
- 확인 Token은 관리자 식별자에 묶되 Visit 데이터 모델에 검증자를 추가하지 않는다.
- `AdminPrincipal`은 허용된 공통 인증 컨텍스트로 `common.security`가 소유한다.
- `common.security`는 Spring Security 타입을 import하지 않는다. `security.infrastructure`가 `Authentication`을 `AdminPrincipal`로 변환한다.

## 6. 외부 입력 검증

검증은 중복하더라도 목적을 분리한다.

1. Security: 보호 URL의 인증·역할
2. Presentation: JSON 파싱, 필수값, 길이·형식·허용값
3. Application: 참조 존재, 공개 상태, 확인 Token, 호출 순서
4. Domain: 불변 조건
5. Persistence: NOT NULL, FK, UNIQUE, CHECK

URL은 HTTPS와 허용 호스트를 검증하고 리디렉션 최종 호스트도 확인한다. 제공자 응답 문자열을 로그·오류에 그대로 출력하지 않는다. 식별자는 공통 계약의 불투명 문자열로 다룬다.

## 7. 민감정보와 오류 노출

다음 원문을 로그, API 응답, 예외 메시지와 메트릭 Label에 남기지 않는다.

- 관리자 비밀번호
- JWT Access Token과 Refresh Token
- Kakao·YouTube API Key
- Redis Token 검증 값
- Authorization·Cookie 헤더
- 검증 참여자 비밀번호와 검증 세션 ID·Redis key
- 외부 제공자 원문 오류 본문

오류 응답에는 안정된 코드, 일반화된 메시지, 안전한 필드 오류와 `traceId`만 제공한다. 로그인 실패 시 계정 존재 여부를 구분하지 않는다.

## 8. Refresh Token과 Redis 장애

[ADR-AUTH-001](../07-adr/security/auth-001-spring-security-jwt.md) 및 [ADR-AUTH-006](../07-adr/security/auth-006-cookie-origin-defense.md)에 따라:

- Access Token은 30분 만료, 프론트엔드 메모리에만 둔다.
- Refresh Token은 14일 TTL, HttpOnly·Secure·SameSite=Strict·`Path=/api/admin/auth` 쿠키로 전달한다. Refresh·Logout 요청은 단일 `Origin` 헤더가 `ADMIN_PUBLIC_BASE_URL`로 설정한 관리자 화면 Origin과 canonical form으로 정확히 일치해야 하며, 누락·다중·불일치는 `403 FORBIDDEN`으로 Bearer·Refresh Token 처리보다 먼저 차단한다. Origin 검사는 SameSite 쿠키 정책과 함께 쓰는 보조 CSRF 방어선이고 로그인·Bearer 전용 관리자 API에는 적용하지 않는다. Origin 없는 비브라우저·운영 점검 요청도 허용하지 않는다.
- 계정당 활성 Refresh Token 하나만 허용한다.
- 재발급마다 회전하고 재사용을 탐지해 Token 계열을 폐기한다.
- Redis 장애 시 재발급은 fail-closed이고 재로그인을 요구한다.

관리자 화면은 Access Token이 메모리에 없을 때 재발급을 한 번 시도한다. 성공하면 현재 화면을 유지하고 실패하면 `/admin/login`으로 이동한다. 관리자 API `401` 뒤 재발급과 원래 요청 재실행도 각각 한 번으로 제한한다. 프론트엔드 라우트 가드는 화면 노출만 제어하며 최종 권한 판정은 이 보안 경계가 수행한다.

## 9. 확정 보안 세부와 추가 ADR

### 확정 보안 세부

- JWT는 RS256, `iss=masit-on`, `aud=masit-on-admin-api`를 사용하고 `kid`로 검증 키를 선택한다. 키는 90일마다 교체하며 새 검증 키 선배포 → 새 키 발급 전환 → 30분 뒤 이전 개인 키 폐기 순서를 지킨다.
- 로그인 실패는 Redis `auth:login-failure:login-id:{loginIdHash}`와 `auth:login-failure:source:{sourceHash}` 카운터에 첫 실패부터 15분 TTL을 적용한다. 두 카운터 중 하나의 원자 증가 결과가 5 이상이면 남은 TTL 동안 차단하고 로그인 성공 시 함께 삭제한다. `source`는 다음 규칙으로 결정한다. `reverseProxyEnabled=true`이고 요청 peer가 설정된 trusted proxy peer 목록에 포함된 경우에만 Spring이 단일 IP 리터럴 `X-Forwarded-For` 값을 해석한다. Reverse proxy가 활성화된 환경에서는 Nginx가 외부 요청의 `X-Forwarded-For`를 `$remote_addr`로 덮어써 단일 값으로 전달한다. 요청 peer가 목록에 없거나 전달 헤더가 없거나 형식이 잘못되었거나 다중 값이면 요청 peer의 원격 주소(peer remote address)로 fallback한다. `reverseProxyEnabled=true`인데 trusted proxy peer 목록이 비어 있으면 애플리케이션은 시작 시 fail-fast한다. 운영 환경은 `ADMIN_LOGIN_REVERSE_PROXY_ENABLED=true`와 `ADMIN_LOGIN_TRUSTED_PROXY_ADDRESSES`에 Nginx와 Backend 사이의 peer 주소를 주입한다.
- Refresh Token은 Redis `auth:refresh:{adminId}`에 SHA-256 해시·Token 계열 ID·발급 및 만료 시각을 JSON으로 저장하고 14일 TTL로 정리한다. 회전과 재사용 탐지는 원자 연산으로 수행한다.
- Java Principal 타입은 `com.masiton.security.application.AdminPrincipal`이 소유하고 presentation·domain 패키지에는 두지 않는다.

### 추가 ADR 필요

- 관리자 권한을 자원·기능별로 세분화할 때
- 외부 IdP 또는 다중 관리자 조직을 도입할 때
- Access Token 즉시 폐기 목록을 도입할 때

확인 Token의 저장·단일 사용·결과 재현은 [ADR-AUTH-003](../07-adr/security/auth-003-confirmation-token.md)으로 확정했다. PostgreSQL에는 Token 해시와 관리자·자원 종류·후보 Snapshot만 저장하고 원문을 저장하거나 로그에 남기지 않는다.

## 10. 보안 테스트

- 공개 GET 무인증 성공, 나머지 `/api/admin/**` 무인증 401
- 공개 맛집 상세는 유효 회원 Token일 때만 최근 기록을 시도하고, 누락·만료·변조·교차 audience Token 및 회원 인증·개인화 저장소 장애에서는 익명 `200`과 기록 생략을 검증
- 유튜버 상세 세 조회는 Token 없음과 검증할 수 없는 Token 모두에서 401이 아니며, 정의되지 않은 하위 경로는 기본 거부를 검증
- 로그인·재발급 matcher가 포괄 관리자 matcher보다 먼저 적용
- 로그아웃은 JWT와 Refresh Token 쿠키를 모두 검증
- 메모리 Access Token 소실 뒤 재발급 성공·실패와 단일 재시도 제한
- 정상 JWT이나 `ADMIN` 없음 403
- 만료·변조·잘못된 issuer/audience JWT 401
- 다른 관리자의 확인 Token 사용 거부
- 확인 Token 최초 생성 `201`, 완료 재시도 `200`, 동시 중복 최초·재시도 동일 `409`
- 확인 Token 원문·후보 Snapshot 로그 미노출
- Refresh Token 회전·재사용·로그아웃·Redis 장애
- 비밀번호·Token·API Key 로그 미노출
- 검증 쿠키와 회원·관리자 Bearer 동시 사용, 7일 만료·폐기·Redis 장애·반복 인증창 0회
- 비공개 자원의 공개 조회 404와 관리자 Visit 참조 422
