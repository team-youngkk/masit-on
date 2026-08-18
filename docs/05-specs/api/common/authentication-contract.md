---
related_documents:
  - ../README.md
  - error-contract.md
  - ../account/member-authentication-api.md
  - ../admin/authentication-api.md
  - ../../../07-adr/security/auth-007-unified-account-rbac-session.md
  - ../../../07-adr/security/auth-006-cookie-origin-defense.md
  - ../../../07-adr/platform/web-006-unified-login-rbac-route.md
---

# 인증 공통 계약

## 1. 통합 계정과 경계

`member_account`가 일반 회원과 관리자의 유일한 인증 계정 원장이다. 계정은 `MEMBER` 또는 `ADMIN` 역할 하나를 가지며 이메일·비밀번호 로그인 화면과 Token API를 공유한다.

| 경계 | 경로 | 인증·인가 |
|---|---|---|
| 공개 조회·가입·복구 | 계약에 명시된 공개 API | 인증 없음 |
| 통합 Token | `POST /api/auth/tokens`, `POST /api/auth/tokens/refresh`, `DELETE /api/auth/tokens` | 각각 자격 증명, Refresh 쿠키, Bearer + Refresh 쿠키 |
| 현재 계정·본인 자원 | `/api/me`, `/api/me/**` | 유효한 통합 Bearer |
| 관리자 | `/api/admin/**` | 유효한 통합 Bearer + 현재 `ADMIN` 역할 |

공개 회원가입은 `role`을 입력받지 않고 항상 `MEMBER`를 만든다. 알 수 없는 필드로 전달된 `role`도 허용하지 않고 `400 INVALID_REQUEST`로 거부한다. `ADMIN` 프로비저닝과 역할 변경은 승인·감사 가능한 운영 절차만 허용하며 공개 UI·API를 제공하지 않는다.

## 2. Token과 세션

- Access Token은 RS256 JWT이고 `iss=masit-on`, `aud=masit-on-api`, 유효 시간 30분이다.
- 최소 claim은 `sub`, `iss`, `aud`, `sid`, `jti`, `roles`, `iat`, `exp`다. `roles`는 발급 시 DB의 현재 단일 역할로 만들며 이메일·상태·비밀번호·개인화 데이터를 넣지 않는다.
- Access Token은 응답 본문으로만 전달하고 브라우저 메모리에만 둔다. localStorage, sessionStorage, IndexedDB와 일반 쿠키에 저장하지 않는다.
- Refresh Token은 14일의 고엔트로피 불투명 값이며 Redis `auth:session:` namespace에 원문이 아닌 SHA-256 해시와 회전 상태만 저장한다.
- 쿠키는 `__Secure-masiton-refresh`; `Path=/api/auth/tokens`; `HttpOnly`; `Secure`; `SameSite=Strict`; `Domain` 생략이다.
- `MEMBER`는 최대 3세션, `ADMIN`은 최대 1세션이다. 새 로그인으로 상한을 넘으면 가장 오래된 세션을 원자적으로 폐기한다.
- Refresh는 매번 원자적으로 회전한다. 동시 사용은 하나만 성공하며 재사용을 탐지하면 해당 Token family 전체를 폐기한다.

역할·상태·비밀번호 변경은 모든 활성 세션을 폐기한다. 모든 보호 경계는 현재 `member_account.status=ACTIVE`와 역할을 조회하거나 변경과 전 세션 폐기가 원자적으로 보장되는 동등한 통제로 변경 직후 이전 Access Token을 거부한다. 인증 상태 저장소를 확인할 수 없으면 `503 AUTHENTICATION_SERVICE_UNAVAILABLE`로 fail-closed 처리한다.

## 3. 응답 역할과 클라이언트 RBAC

로그인·재발급 응답과 `GET /api/me`는 `role: MEMBER|ADMIN`을 반환한다. 클라이언트는 TanStack Query의 현재 사용자 Query를 단일 서버 상태 원천으로 사용해 메인 페이지 관리자 링크와 `/admin/**` Route Guard를 갱신한다.

클라이언트 가드는 사용자 경험을 위한 보조 장치다. 링크 숨김, 캐시된 역할 또는 직접 URL 접근 여부와 관계없이 `/api/admin/**`의 최종 인가는 서버가 현재 역할로 수행한다. 안전한 `returnTo`는 동일 Origin의 허용된 내부 경로만 수락하며 외부 URL, 프로토콜 상대 URL과 관리자 권한이 없는 대상은 사용하지 않는다.

## 4. 오류와 장애

- 인증 누락·만료·변조·폐기 Token: `401 AUTHENTICATION_REQUIRED`
- 유효하게 인증됐지만 현재 역할 부족: `403 FORBIDDEN`
- Redis·계정 상태 저장소·폐기 저장소 장애로 안전한 판정 불가: `503 AUTHENTICATION_SERVICE_UNAVAILABLE`
- 네트워크 실패: HTTP 응답으로 가장하지 않고 클라이언트가 재시도 가능한 전송 실패로 구분한다.

공개 맛집 API는 인증 저장소 장애에도 계속 공개 동작한다. 선택적 인증을 사용하는 공개 상세는 인증 실패나 상태 조회 장애를 익명 조회로 격리하고 개인화 부수효과만 생략한다.

## 5. Origin·제한·비밀정보

Refresh 쿠키가 사용되는 재발급·로그아웃은 Token 조회·회전·폐기 전에 정확히 하나의 `Origin`이 배포 allowlist와 canonical form으로 일치하는지 검사한다. 누락·다중·불일치는 `403 FORBIDDEN`이고 Token 상태를 변경하지 않는다.

모든 로그인 요청은 JSON 구조·이메일·비밀번호 형식의 정상 여부와 관계없이 자격 증명 검증 전에 신뢰된 요청 출처 제한을 적용한다. 전달 주소는 구성된 trusted proxy peer에서 온 단일 값만 신뢰하고, reverse proxy 활성화 시 trusted proxy 설정이 비어 있으면 시작을 실패시킨다.

로그인 실패는 계정 존재·상태·역할·비밀번호 오류를 구분하지 않는다. Token 원문, 비밀번호, Authorization·Cookie 헤더, 원문 이메일·클라이언트 주소와 제한용 HMAC 비밀을 저장소·로그·오류·분석 이벤트에 남기지 않는다.
