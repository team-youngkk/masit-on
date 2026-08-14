---
related_documents:
  - ../README.md
  - error-contract.md
  - ../account/member-authentication-api.md
  - ../admin/authentication-api.md
  - ../../../07-adr/security/auth-001-spring-security-jwt.md
  - ../../../07-adr/security/auth-002-member-jwt-refresh-token.md
  - ../../../07-adr/platform/web-003-routing-boundary.md
---

# 인증 공통 계약

## 1. 경계

| 경계 | 경로 | Access Token | Refresh Token |
|---|---|---|---|
| 일반 회원 공개 인증 동작 | 기능별 `/api/auth/**` | 로그인 응답 본문, 이후 Bearer | 회원 전용 보안 쿠키 |
| 일반 회원 본인 자원 | `/api/me`, `/api/me/**` | `aud=masit-on-member-api` Bearer 필수 | 직접 사용하지 않음 |
| 관리자 | `/api/admin/**` | `aud=masit-on-admin-api` Bearer 필수. 로그인·재발급은 예외 | 관리자 전용 보안 쿠키 |

회원과 관리자 principal, JWT audience, Refresh 쿠키와 Redis namespace는 교차 사용하지 않는다. **보호 경계**에서 반대 audience, 만료·변조 Token과 인증 누락은 `401 AUTHENTICATION_REQUIRED`다. 올바른 경계의 신원으로 인증됐지만 별도 권한이 부족할 때만 `403 FORBIDDEN`을 사용한다.

## 2. 일반 회원 Token 전달

- Access Token: RS256 JWT, 30분, 응답 본문 전달, 브라우저 메모리 보관, Bearer 전송
- `sid`: 로그인으로 생성된 회원 세션의 불투명 식별자. 같은 세션의 Access Token, 회전 전후 Refresh 상태와 PostgreSQL 폐기 표식이 공유
- `jti`: Access Token 한 개의 고유 식별자이며 `sid` 대신 세션 폐기에 사용하지 않음
- Refresh Token: 불투명 값, 14일, 회전·재사용 탐지
- 쿠키: `__Secure-masiton-member-refresh`; `Path=/api/auth/tokens`; `HttpOnly`; `Secure`; `SameSite=Strict`; `Domain` 생략
- 활성 세션: 회원당 최대 3개. 네 번째 로그인 성공 시 가장 오래된 세션 폐기
- 회원 Bearer 검증: 서명·만료·issuer·audience·계정 `ACTIVE`와 함께 PostgreSQL `sid` 폐기 표식을 확인한다. 표식이 있으면 `401 AUTHENTICATION_REQUIRED`, 표식 또는 계정 상태 저장소를 조회할 수 없으면 `503 AUTHENTICATION_SERVICE_UNAVAILABLE`로 fail-closed 처리한다.

### 2.1 공개 맛집 상세의 선택적 회원 인증

`GET /api/restaurants/{restaurantId}`는 `permitAll` 공개 조회가 기본이다. 이 경로에서만 유효한 회원 Bearer Token이 있을 때 최근 본 맛집 기록을 위한 회원 문맥을 선택적으로 사용한다.

- Authorization 헤더가 없거나, 회원 Token이 만료·변조·폐기·잘못된 audience인 경우에는 회원 principal을 만들지 않고 익명 공개 조회로 계속한다.
- 회원 계정 상태·폐기 표식 조회 또는 최근 기록 저장소에 장애가 나도 공개 상세의 정상 `200` 응답을 인증·개인화 오류로 바꾸지 않으며 최근 기록만 생략한다.
- 이 예외는 위 한 공개 상세 경로에만 적용한다. `/api/me/**`, 회원 로그아웃과 그 밖의 회원 보호 경로는 기존 `401`/`503` fail-closed 규칙을 그대로 적용한다.
- 회원·관리자 Refresh 쿠키를 사용하는 재발급·로그아웃: HTTPS 동일 Origin의 `Origin` 검증과 배포 Origin allowlist 적용. Origin 누락·불일치는 `403 FORBIDDEN`이며 Token 회전·폐기를 시작하지 않는다.

Redis 장애 중 로그인·재발급은 fail-closed다. 로그아웃은 서버 폐기를 확인한 경우에만 성공한다. 세부 요청·응답과 오류는 [회원 계정·인증 API](../account/member-authentication-api.md)를 따른다.

요청 출처 기반 제한은 신뢰된 Nginx가 외부 입력을 덮어써 전달한 단일 클라이언트 주소를 사용한다. 애플리케이션은 지정된 Nginx 네트워크 peer에서 온 전달 헤더만 해석하고 외부 클라이언트가 보낸 전달 헤더는 신뢰하지 않는다.

## 3. 클라이언트 저장 금지

Access·Refresh Token을 localStorage·sessionStorage에 저장하지 않는다. Token 원문과 Authorization·Cookie 헤더를 로그·오류·분석 이벤트에 남기지 않는다.
