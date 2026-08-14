---
id: API-ADMIN-AUTH-001
title: 관리자 인증 API
status: draft
related_prd:
  - PRD-ADMIN-001
workstream: WS-04
owner: 김인안
reviewers:
  - 이우람
related_requirements:
  - FR-ADMIN-001
related_business_rules:
  - BR-ADMIN-001
related_nfr:
  - NFR-SECURITY-001
  - NFR-SECURITY-002
  - NFR-SECURITY-003
  - NFR-OBSERVABILITY-001
  - NFR-OBSERVABILITY-003
  - NFR-PRIVACY-002
related_documents:
  - ../../../04-product/prd/admin/admin-data-management.md
  - ../common/response-contract.md
  - ../common/error-contract.md
  - ../../data/entity-definitions.md
  - ../../../07-adr/security/auth-001-spring-security-jwt.md
  - ../../../07-adr/data/data-005-redis-refresh-token.md
  - ../../../07-adr/platform/web-003-routing-boundary.md
---

# 관리자 인증 API

## 1. 결정

관리자 인증·인가는 Spring Security 7.1.0과 JWT Access Token을 사용한다. Refresh Token은 Redis 8.8에 저장하고 브라우저에는 `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/admin/auth` 쿠키로만 전달한다. Access Token은 응답 본문으로 발급하고 프론트엔드는 메모리에만 유지하며 로그인·재발급을 제외한 보호 관리자 API에 `Authorization: Bearer` 헤더로 전달한다.

관리자 계정은 사전 발급하며 회원가입, 계정 관리, 비밀번호 복구 API는 MVP에 포함하지 않는다. 일반 사용자 공개 조회에는 인증을 요구하지 않는다.

## 2. 공통 전달 계약

- Access Token은 서명된 JWT이며 관리자 식별자, `ADMIN` 권한, 발급·만료 시각과 토큰 식별에 필요한 최소 claim만 포함한다.
- 비밀번호, 외부 API 키, Refresh Token 원문과 내부 시스템 구조를 JWT claim에 넣지 않는다.
- `/api/admin` 등록 API는 `Authorization: Bearer <access-token>`을 요구한다.
- Refresh Token은 JavaScript에 노출하지 않고 인증 API 전용 보안 쿠키로만 전송한다.
- Access Token이 없거나 만료·서명·issuer·audience 검증에 실패하면 `401 AUTHENTICATION_REQUIRED`다.
- 인증됐지만 `ADMIN` 권한이 없으면 `403 FORBIDDEN`이다.
- Refresh Token은 Redis의 계정·토큰 식별 정보, 만료와 폐기 상태에 대조하고 재발급 때 회전한다.
- Access Token 만료는 30분, Refresh Token TTL은 14일로 하며 설정·테스트·운영 문서에 같은 값을 사용한다 (2026-07-24 결정). Redis 장애로 Refresh Token 조회가 불가능하면 재발급을 차단하는 fail-closed로 처리하여 Access Token 만료 후 재로그인을 요구한다.

## 3. API 요약

| API ID | Method | Path | 설명 |
|---|---|---|---|
| [API-ADMIN-AUTH-001](authentication-api.md#api-admin-auth-001-관리자-로그인) | POST | `/api/admin/auth/tokens` | 사전 발급 계정으로 Access·Refresh Token 발급 |
| [API-ADMIN-AUTH-002](authentication-api.md#api-admin-auth-002-관리자-토큰-재발급) | POST | `/api/admin/auth/tokens/refresh` | Refresh Token 회전과 새 Access Token 발급 |
| [API-ADMIN-AUTH-003](authentication-api.md#api-admin-auth-003-관리자-로그아웃) | DELETE | `/api/admin/auth/tokens` | 현재 Refresh Token 폐기와 쿠키 만료 |

## 4. 토큰 발급

### API-ADMIN-AUTH-001 관리자 로그인

- Method: `POST`
- Path: `/api/admin/auth/tokens`
- 인증: 없음
- 권한: 사전 발급된 활성 관리자 계정

```json
{
  "loginId": "admin-login-id",
  "password": "admin-password"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `loginId` | string | 예 | 앞뒤 공백 제거 후 1~100자. 존재 여부를 오류로 구분하지 않음 |
| `password` | string | 예 | 12~128자. 공백 자동 제거와 응답·로그 기록 금지 |

성공 시 `200 OK`, Refresh Token 보안 쿠키와 다음 필드를 반환한다.

| 필드 | 타입 | 필수 | 의미 |
|---|---|---:|---|
| `accessToken` | string | 예 | Spring Security가 검증할 JWT Access Token |
| `tokenType` | string | 예 | `Bearer` |
| `expiresInSeconds` | integer | 예 | 확정된 Access Token 잔여 만료 시간 |

자격 증명이 틀리거나 계정이 유효하지 않으면 원인을 구분하지 않고 `401`을 반환한다. `login-id` 또는 `source` 버킷의 반복 실패 제한은 기존 15분 내 5회 기준을 유지하며, `source` 결정은 아래 확정 사항을 따른다.

## 5. 토큰 재발급

### API-ADMIN-AUTH-002 관리자 토큰 재발급

- Method: `POST`
- Path: `/api/admin/auth/tokens/refresh`
- 인증: Refresh Token 보안 쿠키 필수

Redis에 저장된 활성 Token과 일치하고 계정이 활성 상태면 기존 Refresh Token을 폐기·회전하고 로그인과 같은 Access Token 응답과 새 보안 쿠키를 반환한다. 누락·만료·불일치·재사용·폐기 Token은 `401`이며 해당 토큰 계열을 폐기한다.

## 6. 로그아웃

### API-ADMIN-AUTH-003 관리자 로그아웃

- Method: `DELETE`
- Path: `/api/admin/auth/tokens`
- 인증: JWT Access Token과 Refresh Token 보안 쿠키

성공 시 Redis의 현재 Refresh Token을 폐기하고 쿠키를 만료시키며 `204 No Content`를 반환한다. 이미 만료된 Access Token은 자체 만료 전까지 별도 차단 목록 없이 유효할 수 있으므로 만료 시간은 보안 요구에 맞춰 짧게 결정한다.

## 7. 관리자 등록 API 적용

모든 관리자 등록·검증 미리보기 요청은 다음 헤더를 요구한다.

```http
Authorization: Bearer <access-token>
```

Spring Security Filter Chain은 HTTP Method와 세부 경로를 먼저 매칭한다. `POST /api/admin/auth/tokens`는 로그인 자격 증명, `POST /api/admin/auth/tokens/refresh`는 Refresh Token 쿠키만 검증한다. `DELETE /api/admin/auth/tokens`는 JWT와 Refresh Token 쿠키를 모두 요구하며, 나머지 `/api/admin/**`는 JWT와 `ADMIN` 권한을 확인한다. 공개 GET API에는 Authorization 헤더를 요구하지 않고 정의되지 않은 API 경로는 기본 거부한다.

## 8. 확정 사항

### 확정 사항

- Spring Security 7.1.0과 JWT Access Token을 사용한다.
- Refresh Token은 Redis 8.8에 저장하고 보안 쿠키로만 전달하며 재발급 때 회전한다.
- Access Token은 브라우저 영구 저장소나 쿠키에 저장하지 않는다.
- 계정당 활성 Refresh Token은 하나만 허용하고 새 로그인 성공 시 기존 Token을 폐기한다.
- 계정 발급·회수·복구는 API가 아닌 관리자 운영 절차로 처리한다.
- JWT는 RS256으로 서명하고 `iss=masit-on`, `aud=masit-on-admin-api`를 검증한다. 모든 서명 키에는 `kid`를 부여한다.
- 새 공개 키를 검증 키 목록에 먼저 배포한 뒤 새 `kid`로 발급하고, 기존 Access Token 최대 수명 30분이 지난 후 이전 개인 키를 폐기한다. 정기 교체 주기는 90일이다.
- Refresh Token은 `auth:refresh:{adminId}`에 SHA-256 Token 해시, Token 계열 ID, 발급·만료 시각을 JSON으로 저장하며 Redis TTL 14일을 적용한다.
- 회전·재사용 탐지와 계정당 단일 활성 Token 보장은 Redis 원자 연산으로 처리한다. 만료 데이터는 TTL로 정리하고 별도 주기 삭제 작업을 두지 않는다.
- 로그인 실패는 `auth:login-failure:login-id:{loginIdHash}`와 `auth:login-failure:source:{sourceHash}` 카운터에 첫 실패부터 15분 TTL을 적용한다. 둘 중 하나가 5회 이상이면 남은 TTL 동안 로그인을 차단하고 성공 시 두 카운터를 삭제한다. `source`는 다음 규칙으로 결정한다. Reverse proxy가 활성화된 환경에서는 Nginx가 외부 요청의 `X-Forwarded-For`를 `$remote_addr`로 덮어써 단일 값으로 전달한다. Spring은 요청 peer가 설정된 trusted proxy peer 목록에 포함될 때만 단일 `X-Forwarded-For` 값을 해석한다. 요청 peer가 목록에 없거나 전달 헤더가 없거나 형식이 잘못되었거나 단일 값이 아니면 요청 피어의 원격 주소(peer remote address)로 fallback한다. Reverse proxy가 활성화되었는데 trusted proxy peer 목록이 비어 있으면 애플리케이션은 시작 시 fail-fast한다.

## 9. 관리자 계정 운영 절차

- 계정 발급: 승인된 운영자가 인증 저장소에 계정을 생성하는 별도 운영 명령을 실행하고 임시 비밀번호는 기존 협업 채널과 분리된 일회성 비밀 전달 수단으로 전달한다.
- 비활성화·회수: 계정을 비활성화하고 해당 관리자의 `auth:refresh:{adminId}`를 즉시 삭제한다. 이미 발급된 Access Token은 최대 30분 뒤 만료된다.
- 비밀번호 재설정: 본인 확인 뒤 운영 명령으로 임시 비밀번호를 발급하고 기존 Refresh Token을 폐기한다.
- 모든 명령은 작업자, 대상 관리자 ID, 작업 종류, 성공 여부와 traceId를 감사 로그에 남기되 비밀번호·Token 원문은 기록하지 않는다.
- 서비스 회원가입, 계정 관리와 비밀번호 복구 API·화면은 MVP에 포함하지 않는다.
