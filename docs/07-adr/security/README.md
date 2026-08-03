---
related_documents:
  - ../README.md
  - ../../01-requirements/non-functional-requirements.md
  - auth-001-spring-security-jwt.md
  - auth-002-member-jwt-refresh-token.md
  - auth-003-confirmation-token.md
  - sec-001-secrets-workload-identity.md
---

# 보안 ADR

인증·인가, Token, 비밀정보와 AWS 워크로드 신원 결정을 관리한다.

| ADR | 제목 |
|---|---|
| [ADR-AUTH-001](auth-001-spring-security-jwt.md) | 관리자 Spring Security JWT 인증·인가 |
| [ADR-AUTH-002](auth-002-member-jwt-refresh-token.md) | 회원 JWT와 Refresh Token |
| [ADR-AUTH-003](auth-003-confirmation-token.md) | 관리자 등록 확인 Token의 저장·소비·재시도 |
| [ADR-SEC-001](sec-001-secrets-workload-identity.md) | 비밀정보와 AWS 워크로드 인증 |
