---
id: API-ADMIN-AUTH-001
title: 관리자 인증·인가 API 호환 계약
status: superseded
related_prd:
  - PRD-ADMIN-001
  - PRD-ACCOUNT-001
related_requirements:
  - FR-ADMIN-001
  - FR-AUTH-001
  - FR-AUTH-002
  - FR-AUTH-003
  - FR-AUTH-004
related_business_rules:
  - BR-ADMIN-001
  - BR-AUTH-009
  - BR-AUTH-010
related_documents:
  - ../account/member-authentication-api.md
  - ../common/authentication-contract.md
  - ../../../06-architecture/security-boundary.md
  - ../../../07-adr/security/auth-007-unified-account-rbac-session.md
---

# 관리자 인증·인가 API 호환 계약

## 1. 변경 결정

관리자 전용 로그인 API와 별도 계정은 폐지한다. 관리자도 `member_account`의 `ADMIN` 역할 계정으로 일반 회원과 같은 로그인 화면 및 다음 통합 API를 사용한다.

| 기존 ID | 기존 경로 | 대체 API | 대체 경로 |
|---|---|---|---|
| `API-ADMIN-AUTH-001` 관리자 로그인 | `POST /api/admin/auth/tokens` | `API-MEMBER-AUTH-006` 통합 로그인 | `POST /api/auth/tokens` |
| `API-ADMIN-AUTH-002` 관리자 재발급 | `POST /api/admin/auth/tokens/refresh` | `API-MEMBER-AUTH-007` 재발급 | `POST /api/auth/tokens/refresh` |
| `API-ADMIN-AUTH-003` 관리자 로그아웃 | `DELETE /api/admin/auth/tokens` | `API-MEMBER-AUTH-008` 로그아웃 | `DELETE /api/auth/tokens` |

기존 `/api/admin/auth/tokens*` 경로는 redirect·alias로 유지하지 않고 정의되지 않은 관리자 경로로 거부한다. 요청·응답, `role`, 단일 audience·쿠키·Redis session과 오류 계약은 [회원 계정·인증 API](../account/member-authentication-api.md)와 [공통 인증 계약](../common/authentication-contract.md)이 권위 문서다.

## 2. 관리자 인가

- 통합 로그인·재발급 응답은 DB에서 확인한 `role: ADMIN`을 반환한다.
- `/api/admin/**`는 유효한 Bearer JWT뿐 아니라 현재 `member_account.status=ACTIVE`와 `role=ADMIN`을 서버에서 확인한다.
- 인증 누락·무효 Token은 `401 AUTHENTICATION_REQUIRED`, 인증됐으나 현재 역할이 `MEMBER`이면 `403 FORBIDDEN`, 현재 상태·역할을 안전하게 확인할 수 없으면 `503 AUTHENTICATION_SERVICE_UNAVAILABLE`이다.
- 메인 페이지의 관리자 링크와 프론트 Route Guard는 서버 인가를 대신하지 않는다.
- `ADMIN` 역할 부여·변경 공개 API와 UI는 제공하지 않는다. 승인·감사 가능한 운영 절차만 허용하며 역할·상태·비밀번호 변경 시 모든 세션을 폐기한다.

## 3. 클라이언트 계약

클라이언트는 로그인·재발급과 `GET /api/me`의 `role`을 TanStack Query 현재 사용자 상태에 반영한다. `ADMIN`일 때만 메인 페이지 관리자 링크를 표시하고 `/admin/**` Route Guard를 통과시킨다. `401`은 재인증, `403`은 권한 없음, `503`은 인증 서비스 일시 장애, 네트워크 실패는 전송 실패로 구분한다.

`returnTo`는 동일 Origin의 허용된 내부 경로만 사용한다. 외부 URL, `//host` 형태, 권한 없는 관리자 경로는 로그인 후 이동 대상으로 사용하지 않는다.

## 4. 보안 회귀 검증

- 공개 가입의 `role` mass assignment가 `400`으로 거부되고 생성 역할은 항상 `MEMBER`인지 검증한다.
- `MEMBER`가 관리자 링크를 직접 구성하거나 `/admin/**`를 호출해도 서버가 `403`으로 거부하는지 검증한다.
- 로그인 오류가 계정 존재·상태·역할을 열거하지 않고, 형식 오류를 포함한 모든 시도가 자격 증명 검증 전에 요청 출처 제한을 통과하는지 검증한다.
- Refresh 쿠키 요청은 Origin 검사가 Token 처리보다 먼저 수행되고, Access Token은 메모리에만 남으며 비밀번호·Token·헤더 원문이 로그에 없는지 검증한다.
- 인증 저장소 장애가 공개 API 가용성을 제한하지 않는지 검증한다.
