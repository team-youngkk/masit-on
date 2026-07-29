---
related_documents:
  - ../README.md
  - member-authentication-api.md
  - ../../../04-product/prd/account/member-authentication.md
  - ../../../02-analysis/first-expansion-workstreams.md
  - ../../../06-architecture/security-boundary.md
  - ../../../07-adr/platform/web-003-routing-boundary.md
---

# 일반 회원 계정·인증 API

[WS-05](../../../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증)의 일반 회원 계정·인증 흐름은 [일반 회원 계정·인증 API](member-authentication-api.md)에서 정의한다.

- `/api/auth/**`: 회원가입, 이메일 인증, 비밀번호 재설정, 로그인·재발급·로그아웃
- `/api/me`: 인증된 현재 회원의 최소 정보 조회와 본인 계정 탈퇴

일반 회원 Access Token은 회원 전용 JWT audience를 사용하고, Refresh Token은 회원 전용 보안 쿠키와 Redis namespace에서 관리한다. 관리자 `/api/admin/**` 인증과 principal, audience, 쿠키 이름·path 및 Redis 상태를 공유하지 않는다.

현재 회원 자원에는 경로 식별자를 받지 않는다. 서버가 검증한 `MemberPrincipal`의 식별자만 사용하며 다른 회원의 계정 정보 조회·탈퇴 API는 제공하지 않는다.
