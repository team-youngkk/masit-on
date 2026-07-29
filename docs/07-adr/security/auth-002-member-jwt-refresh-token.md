---
id: ADR-AUTH-002
title: 회원 JWT와 Refresh Token
status: Accepted
decision_date: 2026-07-29
owners:
  - 김인안
related_requirements:
  - FR-MEMBER-001
  - FR-MEMBER-002
  - FR-MEMBER-003
  - FR-MEMBER-004
  - FR-MEMBER-005
  - FR-AUTH-001
  - FR-AUTH-002
  - FR-AUTH-003
related_documents:
  - ../../05-specs/data/table-definitions.md
  - ../../06-architecture/security-boundary.md
  - auth-001-spring-security-jwt.md
supersedes: []
superseded_by: null
---

# ADR-AUTH-002 회원 JWT와 Refresh Token

## 1. 상태

Accepted

## 2. 결정

회원 인증은 관리자 인증과 독립된 JWT audience `masit-on-member-api`, `MEMBER` authority, `sid` claim과 매 발급마다 새로 만드는 `jti`를 사용한다. `/api/admin/**`와 공개 회원 인증 메서드를 제외한 `/api/auth/**`, `/api/me/**`는 각각 해당 audience만 검증하므로 토큰을 서로 교차 사용할 수 없다.

회원 Refresh Token은 이름 `__Secure-masiton-member-refresh`, 경로 `/api/auth/tokens`의 cookie와 Redis `auth:member:` namespace에 저장한다. Token 원문은 저장하지 않고 SHA-256 해시만 사용한다. 회원별 활성 세션은 최대 세 개이며, 새 세션은 최초 생성 시각을 기준으로 가장 오래된 세션을 폐기한다. 회전은 그 정렬 순서를 바꾸지 않는다.

회전과 재사용 탐지는 Redis Lua script 한 번으로 처리한다. 회전된 Token 재사용을 발견하면 해당 세션의 최신 Refresh Token까지 폐기한다. Redis 장애 또는 상태 불일치는 fail-closed로 처리한다.

탈퇴 후 남은 Access Token은 `member_session_revocation`의 `sid`로 만료 시각까지 폐기한다. 회원 계정, 일회성 이메일·비밀번호 재설정 Token은 각각 `member_account`, `member_action_token`에 저장한다.

## 3. 결과

회원 가입·로그인·재발급·탈퇴 API는 이 경계를 소비한다. 관리자 계정·쿠키·Redis key와 회원 계정·쿠키·Redis key를 공유하거나, Access/Refresh Token을 브라우저 저장소에 저장하지 않는다.

## 4. 검증

- V1에서 V2로 전진 적용되는 Flyway Testcontainers 검증
- 관리자/회원 audience 교차 거부
- 회원 최대 세 세션, 원자 회전, 재사용 시 현재 세션 폐기
- Redis 장애 시 발급·재발급 fail-closed
