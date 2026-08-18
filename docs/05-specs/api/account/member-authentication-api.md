---
id: API-MEMBER-AUTH-001
title: 일반 회원 계정·인증 API
status: approved
related_prd:
  - PRD-ACCOUNT-001
workstream: WS-05
owner: 김인안
reviewers:
  - 이우람
  - 박진영
related_requirements:
  - FR-MEMBER-001
  - FR-MEMBER-002
  - FR-MEMBER-003
  - FR-MEMBER-004
  - FR-MEMBER-005
  - FR-AUTH-001
  - FR-AUTH-002
  - FR-AUTH-003
  - FR-AUTH-004
related_business_rules:
  - BR-MEMBER-001
  - BR-MEMBER-002
  - BR-MEMBER-003
  - BR-MEMBER-004
  - BR-AUTH-001
  - BR-AUTH-002
  - BR-AUTH-003
  - BR-AUTH-004
  - BR-AUTH-005
  - BR-AUTH-006
  - BR-AUTH-007
  - BR-AUTH-008
  - BR-AUTH-009
  - BR-AUTH-010
related_nfr:
  - NFR-SECURITY-003
  - NFR-SECURITY-004
  - NFR-SECURITY-005
  - NFR-RELIABILITY-001
  - NFR-RELIABILITY-002
  - NFR-RELIABILITY-003
  - NFR-TEST-004
  - NFR-PRIVACY-003
related_documents:
  - ../../../04-product/prd/account/member-authentication.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../01-requirements/non-functional-requirements.md
  - ../common/identifier-contract.md
  - ../common/response-contract.md
  - ../common/error-contract.md
  - ../common/authentication-contract.md
  - ../../../06-architecture/security-boundary.md
  - ../../../07-adr/platform/web-006-unified-login-rbac-route.md
  - ../../../07-adr/security/auth-006-cookie-origin-defense.md
  - ../../../07-adr/security/auth-007-unified-account-rbac-session.md
---

# 통합 계정·인증 API

## 1. 결정

`member_account`는 일반 회원과 관리자의 단일 계정 원장이다. 공개 가입은 역할을 입력받지 않고 항상 `MEMBER`를 만들며, 활성 계정은 역할 구분 없는 같은 이메일·비밀번호 로그인과 `/api/auth/tokens`를 사용한다. 현재 계정은 `/api/me`에서 역할을 함께 조회하고 `/api/admin/**`는 현재 역할이 `ADMIN`인 경우에만 허용한다.

Access Token은 응답 본문으로 발급하고 브라우저 메모리에만 유지한다. 인증이 필요한 API에는 `Authorization: Bearer <access-token>`으로 전달한다. Refresh Token은 통합 `HttpOnly` 보안 쿠키로만 전달하며 JavaScript, 응답 본문, URL과 로그에 노출하지 않는다.

## 2. 인증과 Token 전달 계약

### 2.1 Access Token

- JWT 서명 알고리즘은 RS256, issuer는 `masit-on`, audience는 모든 역할에 공통인 `masit-on-api`다.
- 유효 시간은 발급 시점부터 30분이다.
- 최소 claim은 계정 식별자 `sub`, `iss`, `aud`, 세션 식별자 `sid`, `roles`, `iat`, `exp`, Access Token 식별자 `jti`다. `roles`는 발급 시 DB의 현재 단일 역할(`MEMBER` 또는 `ADMIN`)에서 만든다. 로그인으로 만든 같은 세션의 Access Token과 회전 전후 Refresh 상태는 동일한 불투명 `sid`를 공유한다. `jti`는 Access Token마다 새로 발급하며 세션 폐기 식별자로 사용하지 않는다. 이메일, 비밀번호, Refresh Token, 계정 상태와 개인화 데이터는 claim에 넣지 않는다.
- 프론트엔드는 Access Token을 브라우저 메모리에만 보관한다. Local Storage, Session Storage, IndexedDB와 일반 쿠키에 저장하지 않는다.
- Token 발급·재발급과 현재 회원 응답에는 `Cache-Control: no-store`를 적용한다.

### 2.2 Refresh Token 쿠키

통합 Refresh Token 쿠키 계약은 다음과 같이 확정한다.

```http
Set-Cookie: __Secure-masiton-refresh=<opaque-token>; Path=/api/auth/tokens; Max-Age=1209600; HttpOnly; Secure; SameSite=Strict
```

| 속성 | 값 | 규칙 |
|---|---|---|
| 이름 | `__Secure-masiton-refresh` | 모든 역할이 공유하는 통합 Refresh 쿠키다. |
| `Path` | `/api/auth/tokens` | 통합 로그인·재발급·로그아웃 경계에서만 전송한다. |
| `Max-Age` | `1209600` | 14일이다. 회전할 때 다시 14일을 부여한다. |
| `HttpOnly` | 활성 | JavaScript 접근을 금지한다. |
| `Secure` | 활성 | HTTPS에서만 전송한다. |
| `SameSite` | `Strict` | 교차 사이트 요청에 전송하지 않는다. |
| `Domain` | 생략 | 현재 Host에만 한정한다. |

Refresh 쿠키를 사용하는 재발급·로그아웃은 HTTPS 동일 Origin 요청만 허용하고 `Origin` 헤더를 배포 Origin allowlist와 일치시킨다. 불일치하거나 브라우저 요청에서 누락되면 `403 FORBIDDEN`이며 Token을 회전·폐기하지 않는다. CORS로 임의 Origin과 자격 증명 요청을 함께 허용하지 않는다.

로그아웃 성공, 서버가 Refresh Token을 사용할 수 없다고 확정한 인증 실패와 Redis 장애에는 같은 이름·Path·보안 속성과 `Max-Age=0`으로 쿠키를 만료한다. Redis 장애에서는 프론트엔드의 메모리 Access Token도 제거하고 서버가 요청에서 확인한 세션 식별자의 폐기를 복구 후 재시도한다. 서버 폐기가 확인되기 전에는 로그아웃 성공으로 표시하지 않는다.

### 2.3 세션과 Redis

- `MEMBER`는 최대 3개, `ADMIN`은 최대 1개의 활성 Refresh 세션을 가진다. 새 로그인으로 상한을 넘으면 생성 시각이 가장 오래된 활성 세션을 원자적으로 폐기한다. Redis 세션과 회전된 모든 Refresh 상태는 Access Token과 같은 `sid`를 보존한다.
- Refresh Token은 재발급할 때마다 회전한다. 같은 Token의 동시 재발급은 하나만 성공하며 재사용이 탐지되면 해당 Token 계열을 폐기한다.
- 통합 Refresh 세션은 Redis `auth:session:` namespace를 사용한다. 로그인 제한, 이메일 인증과 비밀번호 재설정의 물리 key 형식은 데이터 계약에서 정의한다.
- Redis에서 세션 생성·검증·회전·폐기를 안전하게 확인할 수 없으면 로그인·재발급·로그아웃은 fail-closed로 실패한다. 이 장애는 공개 맛집 조회를 차단하지 않는다.
- 요청 출처 제한은 신뢰된 Nginx가 외부 입력을 덮어써 전달한 단일 클라이언트 주소를 사용한다. Spring Boot는 지정된 Nginx 네트워크 peer의 전달 헤더만 해석하고 직접 전달된 `X-Forwarded-For`를 신뢰하지 않는다.
- 비밀번호 재설정 완료와 회원 탈퇴는 해당 회원의 모든 활성 세션을 폐기한다. 현재 활성 `sid`를 모두 식별하고 PostgreSQL 폐기 표식을 저장한 뒤에만 비밀번호 변경을 완료하며, 폐기를 보장할 수 없으면 `503 AUTHENTICATION_SERVICE_UNAVAILABLE`로 실패하고 비밀번호를 변경하지 않는다.
- 모든 보호 Bearer 인증은 현재 계정 상태와 역할 및 PostgreSQL `sid` 폐기 표식을 확인하거나 동등한 전 세션 폐기 보장을 적용한다. 역할·상태·비밀번호 변경은 모든 세션을 폐기한다. 표식이 있거나 현재 역할이 경계에 부족하면 각각 `401 AUTHENTICATION_REQUIRED`, `403 FORBIDDEN`으로 처리하고, 안전하게 확인할 수 없으면 `503 AUTHENTICATION_SERVICE_UNAVAILABLE`로 fail-closed 처리한다.

## 3. API 요약과 접근

| API ID | Method | Path | 인증 | 설명 |
|---|---|---|---|---|
| [API-MEMBER-AUTH-001](#api-member-auth-001-회원가입) | POST | `/api/auth/registrations` | 없음 | 이메일 회원가입 접수 |
| [API-MEMBER-AUTH-002](#api-member-auth-002-가입-이메일-인증) | POST | `/api/auth/email-verifications` | 일회용 Token | 가입 이메일 인증 완료 |
| [API-MEMBER-AUTH-003](#api-member-auth-003-인증-메일-재발송) | POST | `/api/auth/email-verifications/resend` | 없음 | 가입 인증 메일 재발송 접수 |
| [API-MEMBER-AUTH-004](#api-member-auth-004-비밀번호-재설정-요청) | POST | `/api/auth/password-resets/requests` | 없음 | 재설정 메일 발송 접수 |
| [API-MEMBER-AUTH-005](#api-member-auth-005-비밀번호-재설정-완료) | POST | `/api/auth/password-resets/confirmations` | 일회용 Token | 비밀번호 변경과 모든 세션 폐기 |
| [API-MEMBER-AUTH-006](#api-member-auth-006-통합-로그인) | POST | `/api/auth/tokens` | 자격 증명 | 역할이 포함된 Access·Refresh Token 발급 |
| [API-MEMBER-AUTH-007](#api-member-auth-007-access-token-재발급) | POST | `/api/auth/tokens/refresh` | Refresh 쿠키 | Token 회전과 새 Access Token 발급 |
| [API-MEMBER-AUTH-008](#api-member-auth-008-로그아웃) | DELETE | `/api/auth/tokens` | Bearer + Refresh 쿠키 | 현재 세션 폐기 |
| [API-MEMBER-AUTH-009](#api-member-auth-009-현재-사용자-정보) | GET | `/api/me` | Bearer | 현재 계정의 최소 정보와 역할 조회 |
| [API-MEMBER-AUTH-010](#api-member-auth-010-회원-탈퇴) | DELETE | `/api/me` | Bearer | 본인 계정 탈퇴 접수 |

`/api/auth/**`의 표에 정의된 요청만 인증 예외 matcher로 허용한다. `/api/me`와 개인화 API는 통합 Bearer JWT를 요구하고 `/api/admin/**`는 추가로 현재 `ADMIN` 역할을 요구한다. 경로에 `memberId`를 받지 않고 검증된 현재 계정 principal만 사용한다. 정의되지 않은 `/api/auth/**`와 `/api/me/**` 경로는 기본 거부한다.

## 4. 계정 상태 비노출 공통 응답

회원가입, 인증 메일 재발송과 비밀번호 재설정 요청은 성공 여부, 대상 이메일의 존재·미존재·미인증·탈퇴·활성 상태, 요청 제한 충족 여부와 메일 발송 결과에 관계없이 다음과 같은 `202 Accepted`를 반환한다.

```json
{
  "accepted": true
}
```

| 필드 | 타입 | 필수 | 의미 |
|---|---|---:|---|
| `accepted` | boolean | 예 | 요청을 외부에 동일하게 접수했음을 뜻한다. 계정·Token 생성 또는 메일 발송 성공을 뜻하지 않는다. |

입력의 이메일 형식, 필수 필드와 비밀번호 정책은 계정 조회 전에 검증하며 잘못된 입력은 `400`으로 반환한다. 입력이 유효해 계정 상태를 조회한 뒤에는 상태별 HTTP 코드, 본문, 헤더와 `Retry-After`를 다르게 제공하지 않는다. 동일 부하의 상태별 서버 처리 시간 p95 차이는 100ms 이하여야 한다.

## 5. 회원가입과 이메일 인증

### API-MEMBER-AUTH-001 회원가입

- Method: `POST`
- Path: `/api/auth/registrations`
- 인증: 없음

```json
{
  "email": "member@example.com",
  "password": "correct horse battery staple"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `email` | string | 예 | 앞뒤 공백 제거와 영문 소문자 변환 뒤 유효한 비국제화 이메일이어야 한다. 제공자별 `+` 태그·점 별칭은 제거하지 않는다. |
| `password` | string | 예 | 12~64자이며 정규화한 이메일과 같을 수 없다. 앞뒤 공백을 자동 제거하지 않는다. |

요청 Schema에는 `role`이 없다. `role`을 포함한 알 수 없는 필드는 `400 INVALID_REQUEST`로 거부하고, 정상 가입은 서버가 무조건 `MEMBER`를 부여한다. 공개 요청으로 `ADMIN`을 선택·추론·승격할 수 없다.

입력이 유효하면 [공통 접수 응답](#4-계정-상태-비노출-공통-응답)을 반환한다. 신규 가입 가능한 경우 미인증 회원과 24시간 유효한 최신 일회용 인증 코드를 만들고 메일을 발송한다. 코드는 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`에서 CSPRNG로 8자를 독립 추출한 40-bit 난수다. 정규화 이메일당 직전 요청 60초 뒤부터 하루 최대 5회, 회원가입·재설정 합산 요청 출처당 시간당 최대 20회를 원자적으로 적용한다. 중복·활성·탈퇴 정리 중 계정이나 제한 초과 요청은 외부에 구분하지 않으며 새 계정·Token·메일을 만들지 않는다.

### API-MEMBER-AUTH-002 가입 이메일 인증

- Method: `POST`
- Path: `/api/auth/email-verifications`
- 인증: 요청 본문의 가입 이메일 인증 코드

```json
{
  "token": "AB7K9M2Q"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `token` | string | 예 | 메일로 전달한 8자 인증 코드. 앞뒤 ASCII 공백을 제거하고 영문을 대문자로 바꾼 뒤 `^[A-HJ-NP-Z2-9]{8}$`를 만족해야 한다. URL과 로그에 기록하지 않는다. |

발급 후 24시간 안의 아직 사용하지 않은 최신 코드이면 일회성으로 소비하고 회원을 활성 상태로 전환한 뒤 `204 No Content`를 반환한다. 누락은 `400 MISSING_REQUIRED_FIELD`, 형식 오류·변조·만료·재사용·이전 발급·탈퇴 회원 코드는 모두 `400 INVALID_EMAIL_VERIFICATION_TOKEN`으로 처리한다. 같은 코드의 동시 요청은 하나만 `204`이며 나머지는 동일한 `400`이다. 제출은 신뢰된 요청 출처당 10분에 최대 10회이며 초과 시 `Retry-After`를 포함한 `429 RATE_LIMIT_EXCEEDED`를 반환한다.

클라이언트는 `204` 인증 성공 또는 확정된 `400` 응답에서 코드 입력을 정리한다. `429 RATE_LIMIT_EXCEEDED`, `503 AUTHENTICATION_SERVICE_UNAVAILABLE`, 그 밖의 일시적 HTTP 오류와 네트워크 오류에서는 서버가 코드의 무효를 확정하지 않았으므로 입력 코드를 보존하고 재시도를 안내한다. 이 경우 인증 메일 재발송을 기본 다음 행동으로 노출하지 않는다. 회원가입 접수 성공 시에는 브라우저 폼의 비밀번호와 비밀번호 확인 값을 정리하되 접수 이메일은 재발송 대상으로 별도 보존한다.

### API-MEMBER-AUTH-003 인증 메일 재발송

- Method: `POST`
- Path: `/api/auth/email-verifications/resend`
- 인증: 없음

```json
{
  "email": "member@example.com"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `email` | string | 예 | 회원가입과 같은 정규화·형식 규칙 |

입력이 유효하면 [공통 접수 응답](#4-계정-상태-비노출-공통-응답)을 반환한다. 미인증 회원이면서 직전 발송 후 60초가 지났고 이메일당 하루 5회 이하인 경우에만 새 24시간 8자 코드를 발급하고, 메일 발송이 접수된 뒤 이전 코드를 무효화한다. 계정 상태, 실제 발송과 제한 초과 여부는 응답에서 구분하지 않는다.

## 6. 비밀번호 재설정

### API-MEMBER-AUTH-004 비밀번호 재설정 요청

- Method: `POST`
- Path: `/api/auth/password-resets/requests`
- 인증: 없음

```json
{
  "email": "member@example.com"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `email` | string | 예 | 회원가입과 같은 정규화·형식 규칙 |

입력이 유효하면 [공통 접수 응답](#4-계정-상태-비노출-공통-응답)을 반환한다. 인증된 활성 회원이고 이메일·출처 제한 안에 있을 때만 30분 유효한 최신 일회용 Token을 발급해 메일로 보낸다. 정규화 이메일당 직전 요청 60초 뒤부터 하루 최대 5회, 회원가입·재설정 합산 요청 출처당 시간당 최대 20회를 적용한다.

### API-MEMBER-AUTH-005 비밀번호 재설정 완료

- Method: `POST`
- Path: `/api/auth/password-resets/confirmations`
- 인증: 요청 본문의 비밀번호 재설정 Token

```json
{
  "token": "opaque-password-reset-token",
  "newPassword": "new correct horse battery staple"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `token` | string | 예 | 메일로 전달한 비어 있지 않은 고엔트로피 불투명 값. URL과 로그에 기록하지 않는다. |
| `newPassword` | string | 예 | 12~64자이며 Token 소유자의 정규화 이메일과 같을 수 없다. 공백을 자동 제거하지 않는다. |

유효한 최신 Token이면 비밀번호를 변경하고 해당 Token과 남은 재설정 Token 및 모든 활성 회원 세션을 폐기한 뒤 `204 No Content`를 반환한다. 어느 단계도 안전하게 완료할 수 없으면 비밀번호와 Token 상태를 부분 변경하지 않는다.

- 누락: `400 MISSING_REQUIRED_FIELD`
- 비밀번호 정책 위반: `400 INVALID_FIELD_VALUE`
- 변조·만료·재사용·이전 발급 Token: `400 INVALID_PASSWORD_RESET_TOKEN`
- Redis 장애로 전체 세션 폐기를 보장할 수 없음: `503 AUTHENTICATION_SERVICE_UNAVAILABLE`

같은 Token의 동시 요청은 하나만 성공한다. 완료 뒤 기존 Access Token으로 보호 API에 접근하거나 기존 Refresh Token으로 재발급할 수 없다.

## 7. 로그인·재발급·로그아웃

### API-MEMBER-AUTH-006 통합 로그인

- Method: `POST`
- Path: `/api/auth/tokens`
- 인증: 없음

```json
{
  "email": "member@example.com",
  "password": "correct horse battery staple"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `email` | string | 예 | 회원가입과 같은 정규화·형식 규칙 |
| `password` | string | 예 | 12~64자. 공백을 자동 제거하지 않고 응답과 로그 기록을 금지한다. |

성공 시 `200 OK`, 통합 Refresh Token 보안 쿠키와 다음 본문을 반환한다.

```json
{
  "accessToken": "member-jwt-access-token",
  "tokenType": "Bearer",
  "expiresInSeconds": 1800,
  "role": "ADMIN"
}
```

| 필드 | 타입 | 필수 | 의미 |
|---|---|---:|---|
| `accessToken` | string | 예 | 통합 `aud=masit-on-api` JWT Access Token |
| `tokenType` | string | 예 | 항상 `Bearer` |
| `expiresInSeconds` | integer | 예 | 응답 시점의 Access Token 잔여 만료 초 |
| `role` | string | 예 | DB에서 확인한 현재 역할. `MEMBER` 또는 `ADMIN` |

미존재·미인증·탈퇴·비활성 계정, 역할과 잘못된 비밀번호 및 세 로그인 실패 제한 중 하나에 해당하는 요청은 모두 `401 INVALID_CREDENTIALS`와 같은 본문으로 처리한다. JSON·이메일·비밀번호 형식이 잘못된 시도도 자격 증명 검증 전에 요청 출처 제한을 적용한다. 유효한 형식에는 계정·요청 출처 조합 5회, 계정 전체 10회, 요청 출처 전체 50회를 첫 실패부터 15분 동안 적용하며 `Retry-After`로 제한 범위를 노출하지 않는다.

Redis 장애로 새 세션과 역할별 세션 상한을 안전하게 반영할 수 없으면 Token과 쿠키를 발급하지 않고 `503 AUTHENTICATION_SERVICE_UNAVAILABLE`를 반환한다. 새 로그인은 `MEMBER` 3개, `ADMIN` 1개 상한을 유지한다.

### API-MEMBER-AUTH-007 Access Token 재발급

- Method: `POST`
- Path: `/api/auth/tokens/refresh`
- 인증: `__Secure-masiton-refresh` 쿠키
- Bearer Token: 요구하지 않음

유효한 활성 세션과 `ACTIVE` 계정의 아직 사용하지 않은 Refresh Token이면 현재 DB 역할을 다시 확인해 기존 Token을 원자적으로 폐기·회전하고 로그인과 같은 `role` 포함 `200 OK` 본문과 새 보안 쿠키를 반환한다. 재발급은 활성 세션 수를 늘리지 않는다.

- 쿠키 누락·변조·만료·폐기·회원 비활성: `401 INVALID_REFRESH_TOKEN`
- 회전 전 Token 재사용: `401 INVALID_REFRESH_TOKEN`, 해당 Token 계열 폐기
- Redis 장애: `503 AUTHENTICATION_SERVICE_UNAVAILABLE`, 새 Token과 쿠키 발급 금지

무효 Refresh Token이 서버에서 사용 불가능하다고 확정되면 응답에서 통합 Refresh 쿠키를 만료한다. 같은 Refresh Token의 동시 요청은 하나만 성공하고 나머지는 재사용 탐지 규칙을 적용한다.

### API-MEMBER-AUTH-008 로그아웃

- Method: `DELETE`
- Path: `/api/auth/tokens`
- 인증: 통합 Bearer JWT와 `__Secure-masiton-refresh` 쿠키

현재 Bearer JWT의 회원과 Refresh 세션 소유자가 일치하면 현재 세션만 폐기하고 쿠키를 만료한 뒤 `204 No Content`를 반환한다. 같은 회원의 다른 활성 세션은 유지한다. 이미 만료·폐기된 현재 Refresh Token은 다른 세션에 영향을 주지 않고 쿠키를 만료해 `204`를 반환한다. PostgreSQL 폐기 표식이 저장된 직후부터 현재 세션의 기존 Access Token도 `401 AUTHENTICATION_REQUIRED`로 거부한다.

Bearer JWT가 없거나 유효하지 않음, Refresh 쿠키 누락 또는 두 소유자 불일치는 `401 AUTHENTICATION_REQUIRED`다. Redis 장애로 현재 서버 세션 폐기를 확인할 수 없으면 `503 AUTHENTICATION_SERVICE_UNAVAILABLE`를 반환한다. 이때 메모리 Access Token을 제거하고 Refresh 쿠키를 만료한다. 서버는 Bearer JWT의 세션 식별자로 PostgreSQL에 멱등 폐기 표식을 먼저 저장하고 14일간 유지한다. Redis 복구 뒤 Refresh 세션을 조회하더라도 같은 세션 표식이 있으면 재발급을 거부하고 Redis 세션을 정리한다. 표식 저장에도 실패하면 쿠키는 만료하되 `500 INTERNAL_SERVER_ERROR`를 반환하고 운영 경보를 발생시킨다. 서버 폐기 확인 전에는 성공으로 응답하지 않는다.

## 8. 현재 회원 정보와 탈퇴

### API-MEMBER-AUTH-009 현재 사용자 정보

- Method: `GET`
- Path: `/api/me`
- 인증: 회원 Bearer JWT

활성 회원이면 `200 OK`, `Cache-Control: private, no-store`와 현재 Principal에 해당하는 최소 정보만 반환한다.

```json
{
  "id": "member-id",
  "email": "member@example.com",
  "role": "MEMBER"
}
```

| 필드 | 타입 | 필수 | 의미 |
|---|---|---:|---|
| `id` | string | 예 | 회원 자원 안에서 안정적인 불투명 식별자. 클라이언트가 생성 규칙을 해석하지 않는다. |
| `email` | string | 예 | 회원의 정규화된 가입 이메일 |
| `role` | string | 예 | 서버에서 확인한 현재 역할. `MEMBER` 또는 `ADMIN` |

경로·쿼리로 회원 식별자를 받지 않는다. 인증이 없거나 Token이 무효하거나 Token 발급 뒤 계정이 비활성·탈퇴 처리 상태가 됐으면 `401 AUTHENTICATION_REQUIRED`다. 다른 회원의 존재 여부를 `403`이나 `404`로 구분하는 경로는 제공하지 않는다.

### API-MEMBER-AUTH-010 회원 탈퇴

- Method: `DELETE`
- Path: `/api/me`
- 인증: 회원 Bearer JWT
- 요청 본문: 없음

클라이언트는 삭제되는 개인정보·찜·최근 기록과 재가입 조건을 보여주고 사용자의 명시적 확인 뒤에만 호출한다. 서버는 Principal의 본인 계정만 대상으로 즉시 로그인 불가 상태로 전환하고 정리 작업을 접수한 뒤 `202 Accepted`와 빈 본문을 반환한다.

정리 대상은 이메일, 비밀번호 해시, 회원 식별 정보, 이메일 인증·비밀번호 재설정 Token, 모든 활성 세션, 찜과 최근 본 맛집이다. 정리 완료 전에는 같은 이메일 재가입을 허용하지 않는다. 실패 단계는 15분마다 재시도하고 1시간 미완료 시 운영 알림을 발생시키며 요청 후 24시간 안에 완료하거나 운영자가 수동 복구한다.

동시에 도착한 중복 탈퇴 요청은 모두 같은 정리 작업으로 수렴하며 새 작업이나 데이터 잔존을 만들지 않는다. 계정이 로그인 불가 상태로 바뀐 뒤 기존 Bearer Token으로 다시 요청하면 `401 AUTHENTICATION_REQUIRED`다. 정리 일부 실패가 공개 조회를 실패시키지 않는다.

## 9. 오류 계약

모든 오류는 [공통 오류 계약](../common/error-contract.md)의 `code`, `message`, `errors`, 선택 `resource`·`details`, 서버 생성 `traceId` 구조를 사용한다. 비밀번호, 이메일 인증·재설정·Access·Refresh Token 원문, Cookie·Authorization 헤더, 계정 상태와 내부 제한 key를 오류·로그에 넣지 않는다.

| HTTP | 코드 | 적용 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | JSON 구조를 해석할 수 없음 |
| 400 | `MISSING_REQUIRED_FIELD` | 필수 요청 필드 누락 |
| 400 | `INVALID_FIELD_VALUE` | 이메일 형식·비밀번호 정책 위반 |
| 400 | `INVALID_EMAIL_VERIFICATION_TOKEN` | 이메일 인증 Token이 변조·만료·사용·교체됐거나 대상 상태가 유효하지 않음 |
| 400 | `INVALID_PASSWORD_RESET_TOKEN` | 재설정 Token이 변조·만료·사용·교체됐거나 대상 상태가 유효하지 않음 |
| 401 | `INVALID_CREDENTIALS` | 로그인 자격 증명·계정 상태·로그인 제한 실패를 구분하지 않음 |
| 401 | `INVALID_REFRESH_TOKEN` | Refresh 쿠키 누락·무효·만료·폐기·재사용 또는 회원 비활성 |
| 401 | `AUTHENTICATION_REQUIRED` | 보호 API의 통합 Bearer JWT가 없거나 무효, principal 불일치 |
| 403 | `FORBIDDEN` | 유효한 통합 인증이 있지만 현재 역할이 관리자 경계에 부족한 경우 |
| 429 | `RATE_LIMIT_EXCEEDED` | 이메일 인증 코드 제출이 요청 출처당 10분 10회를 초과함 |
| 503 | `AUTHENTICATION_SERVICE_UNAVAILABLE` | Redis 또는 PostgreSQL 인증 상태 저장소 장애로 계정 인증 상태의 생성·검증·폐기를 보장할 수 없음. Bearer의 `sid` 폐기 표식·계정 상태 조회 실패도 포함 |
| 500 | `INTERNAL_SERVER_ERROR` | 예상하지 못한 내부 실패 |

탈퇴·비활성·미인증 상태를 알려주는 별도 공개 오류 코드는 두지 않는다. 로그인에서는 `401 INVALID_CREDENTIALS`, Refresh·보호 API에서는 각각 `401 INVALID_REFRESH_TOKEN` 또는 `401 AUTHENTICATION_REQUIRED`, 계정 상태 비노출 접수 API에서는 `202`로 일반화한다.

## 10. 예외·경계 검증

- 정규화 결과가 같은 이메일의 동시 가입에서 회원은 최대 한 건이고 모든 외부 응답은 동일한 `202`다.
- 12자·64자 비밀번호는 허용하고 11자·65자 및 정규화 이메일과 같은 값은 `400`이다.
- 이메일 인증 코드는 정확히 8자·허용 문자만 생성되고 소문자 입력은 대문자로 정규화되며 금지 문자·길이 오류는 거부되는지 검증한다.
- 이메일 인증 24시간, 제출 10분 10회 제한, 비밀번호 재설정 30분, Access Token 30분과 Refresh Token 14일 경계를 검증한다.
- 인증·재설정 Token 원문은 저장소·로그·URL에 남지 않고 최신 Token 한 번만 성공한다.
- 가입·인증 재발송·재설정 요청의 미존재·미인증·탈퇴·활성·제한·메일 실패 응답은 상태·본문·헤더가 같다.
- 로그인 상태별 동일 오류와 p95 100ms 기준, 세 종류 실패 제한을 검증한다.
- `MEMBER` 네 번째 로그인 뒤 활성 세션은 3개, `ADMIN` 재로그인 뒤 활성 세션은 1개이며 가장 오래된 초과 세션은 재발급할 수 없다.
- Refresh Token 회전·동시 요청·재사용 탐지와 Token 계열 폐기를 검증한다.
- 동일 Token·쿠키·Redis 세션이 역할에 따라 본인 경계에서는 동작하고 관리자 경계에서는 현재 `ADMIN`만 허용되는지 검증한다.
- Redis 장애에서 로그인·재발급·로그아웃이 fail-closed이고 공개 조회는 정상인지 검증한다.
- 현재 회원 조회·탈퇴가 Principal 본인만 사용하며 경로 식별자로 다른 회원을 지정할 수 없는지 검증한다.
- 비밀번호 재설정과 탈퇴 뒤 기존 모든 Access·Refresh Token 및 남은 일회용 Token을 사용할 수 없는지 검증한다.
- 모든 오류 본문에 서버 생성 `traceId`가 있고 비밀번호·Token·Cookie·Authorization 원문이 없는지 검증한다.

## 11. 요구사항 추적

| API | FR | BR | 주요 NFR |
|---|---|---|---|
| API-MEMBER-AUTH-001 | FR-MEMBER-001 | BR-MEMBER-001, BR-MEMBER-002, BR-AUTH-007, BR-AUTH-008 | NFR-SECURITY-005, NFR-PRIVACY-003 |
| API-MEMBER-AUTH-002 | FR-MEMBER-002 | BR-MEMBER-003, BR-AUTH-008 | NFR-SECURITY-004, NFR-SECURITY-005, NFR-TEST-004 |
| API-MEMBER-AUTH-003 | FR-MEMBER-002 | BR-AUTH-005, BR-AUTH-007 | NFR-SECURITY-005 |
| API-MEMBER-AUTH-004 | FR-MEMBER-003 | BR-AUTH-007, BR-AUTH-008 | NFR-SECURITY-005 |
| API-MEMBER-AUTH-005 | FR-MEMBER-003 | BR-MEMBER-002, BR-AUTH-006 | NFR-SECURITY-004, NFR-PRIVACY-003 |
| API-MEMBER-AUTH-006 | FR-AUTH-001 | BR-MEMBER-003, BR-AUTH-001, BR-AUTH-003, BR-AUTH-004, BR-AUTH-007 | NFR-SECURITY-004, NFR-SECURITY-005 |
| API-MEMBER-AUTH-007 | FR-AUTH-002 | BR-AUTH-001, BR-AUTH-002, BR-AUTH-003 | NFR-SECURITY-004, NFR-RELIABILITY-001 |
| API-MEMBER-AUTH-008 | FR-AUTH-003 | BR-AUTH-001, BR-AUTH-002 | NFR-SECURITY-004, NFR-RELIABILITY-003 |
| API-MEMBER-AUTH-009 | FR-MEMBER-005 | BR-AUTH-007 | NFR-SECURITY-004, NFR-PRIVACY-003 |
| API-MEMBER-AUTH-010 | FR-MEMBER-004 | BR-MEMBER-004 | NFR-SECURITY-004, NFR-RELIABILITY-002, NFR-PRIVACY-003 |

## 12. 완료 기준

- 회원가입 → 이메일 인증 → 로그인 → 현재 사용자 조회 → 재발급 → 로그아웃 흐름이 브라우저·통합 테스트를 통과한다.
- 비밀번호 재설정과 탈퇴 뒤 인증·개인화 데이터 폐기 및 동일 이메일 재가입 조건이 검증된다.
- 계정 상태 비노출, 요청·로그인 제한, 역할별 세션 상한, Refresh 회전·재사용과 Redis 장애가 정상·예외·동시성 테스트를 통과한다.
- 통합 matcher, principal, audience, 쿠키와 Redis namespace 및 서버 RBAC가 Security 통합 테스트로 검증된다.
- API 추적표, 데이터 계약, 보안 경계와 ADR-WEB-006이 이 경로·Token 전달 계약에 맞게 갱신된다.
