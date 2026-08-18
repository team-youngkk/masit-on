---
related_documents:
  - ../README.md
  - member-authentication-api.md
  - ../../../04-product/prd/account/member-authentication.md
  - ../../../02-analysis/first-expansion-workstreams.md
  - ../../../06-architecture/security-boundary.md
  - ../../../07-adr/security/auth-007-unified-account-rbac-session.md
  - ../../../07-adr/platform/web-006-unified-login-rbac-route.md
---

# 통합 계정·인증 API

[WS-05](../../../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증)의 계정·인증 흐름과 관리자 로그인 진입은 [통합 계정·인증 API](member-authentication-api.md)에서 정의한다.

- `/api/auth/**`: 회원가입, 이메일 인증, 비밀번호 재설정, 회원·관리자 통합 로그인·재발급·로그아웃
- `/api/me`: 인증된 현재 계정의 최소 정보와 `role` 조회, 본인 계정 탈퇴

회원과 관리자는 하나의 issuer·audience, Refresh 쿠키, Redis session namespace를 공유한다. 서버는 `member_account.role`을 Access Token 권한으로 발급하며 `/api/admin/**`를 `ADMIN`에게만 허용한다. 공개 회원가입 요청은 `role`을 받지 않고 항상 `MEMBER`로 생성한다.

현재 계정 자원에는 경로 식별자를 받지 않는다. 서버가 검증한 principal의 식별자만 사용하며 다른 계정의 정보 조회·탈퇴 API는 제공하지 않는다.
