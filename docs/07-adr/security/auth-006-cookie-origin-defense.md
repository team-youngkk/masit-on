---
id: ADR-AUTH-006
title: 쿠키 기반 Refresh·Logout Origin 방어
status: Accepted
decision_date: 2026-08-14
owners:
  - 김인안
related_requirements:
  - NFR-SECURITY-001
  - NFR-SECURITY-003
  - NFR-SECURITY-004
  - NFR-SECURITY-005
related_documents:
  - ../../05-specs/api/common/authentication-contract.md
  - ../../05-specs/api/admin/authentication-api.md
  - ../../05-specs/api/account/member-authentication-api.md
  - ../../06-architecture/security-boundary.md
  - auth-001-spring-security-jwt.md
  - auth-002-member-jwt-refresh-token.md
  - ../platform/web-003-routing-boundary.md
supersedes:
  - auth-001-spring-security-jwt.md
superseded_by: null
---

# ADR-AUTH-006 쿠키 기반 Refresh·Logout Origin 방어

## 1. 상태

Accepted

## 2. 결정 요약

회원과 관리자 Refresh·Logout처럼 보안 쿠키를 사용하는 요청은 단일 `Origin` 헤더를 요구한다. 값은 각 경계의 배포 Origin allowlist와 canonical form으로 비교하며, 헤더가 없거나 여러 개이거나 허용되지 않으면 Token을 읽거나 회전·폐기하기 전에 `403 FORBIDDEN`으로 거부한다.

## 3. 배경

`SameSite=Strict`, `Secure`, `HttpOnly`, 제한된 cookie path는 쿠키 노출을 줄이지만 쿠키가 전송된 요청의 의도된 화면 출처를 애플리케이션에서 확인하지는 않는다. 회원과 관리자 구현이 서로 다른 헤더 API를 사용하면 다중 `Origin` 헤더 해석이 달라져 동일한 쿠키 보안 계약이 깨질 수 있다.

## 4. 결정

- 회원 `/api/auth/tokens/refresh`, `/api/auth/tokens`와 관리자 `/api/admin/auth/tokens/refresh`, `/api/admin/auth/tokens`에만 적용한다.
- `HttpServletRequest#getHeaders("Origin")`로 모든 값을 읽어 정확히 하나일 때만 후보로 인정한다.
- 회원은 `MemberCookieSettings.publicBaseUrl`, 관리자는 canonicalized `ADMIN_PUBLIC_BASE_URL` allowlist로 후보를 비교한다.
- 검증은 회원 서비스와 관리자 Bearer·Refresh Token 처리보다 먼저 수행하며, 실패 시 `403 FORBIDDEN`과 공통 오류 계약을 반환한다.
- 로그인, Bearer 전용 관리자 API와 공개 조회에는 이 검사를 확장하지 않는다.

## 5. 선택 근거와 트레이드오프

공통 resolver를 사용하면 회원·관리자 경계의 다중 헤더 해석을 동일하게 유지하면서 allowlist 정책은 각 설정 객체가 소유할 수 있다. 브라우저가 아닌 운영 점검 요청도 Origin을 명시해야 하며, 기존 쿠키 속성과 함께 방어 계층을 하나 더 운영하는 비용을 부담한다.

## 6. 구현 및 운영 영향

회원 Controller와 관리자 Origin Filter가 공통 resolver를 사용한다. 운영 환경은 회원·관리자 공개 Origin 설정을 실제 웹 Origin으로 주입해야 하며, URL path·query·fragment가 포함된 값은 허용하지 않는다.

## 7. 검증 방법

회원·관리자 Refresh·Logout에 대해 허용된 단일 Origin, 누락, 허용되지 않은 값, 서로 다른 다중 Origin을 검증한다. 실패한 경우 서비스 호출, Token 회전·폐기와 쿠키 변경이 시작되지 않는지 확인한다.

## 8. 관련 문서

- [인증 공통 계약](../../05-specs/api/common/authentication-contract.md)
- [관리자 인증 API](../../05-specs/api/admin/authentication-api.md)
- [회원 계정·인증 API](../../05-specs/api/account/member-authentication-api.md)
- [보안 경계](../../06-architecture/security-boundary.md)
- [관리자 JWT 인증 ADR](auth-001-spring-security-jwt.md)
- [회원 JWT·Refresh Token ADR](auth-002-member-jwt-refresh-token.md)
