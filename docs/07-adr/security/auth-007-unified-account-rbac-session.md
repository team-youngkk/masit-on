---
id: ADR-AUTH-007
title: 통합 계정 RBAC와 세션
status: Accepted
decision_date: 2026-08-18
owners:
  - 김인안
related_requirements:
  - FR-ADMIN-001
  - FR-AUTH-001
  - FR-AUTH-002
  - FR-AUTH-003
  - FR-AUTH-004
  - FR-MEMBER-001
  - FR-MEMBER-005
  - NFR-SECURITY-001
  - NFR-SECURITY-003
  - NFR-SECURITY-004
  - NFR-SECURITY-005
related_documents:
  - ../../01-requirements/functional-requirements.md
  - ../../01-requirements/business-rules.md
  - ../../05-specs/api/common/authentication-contract.md
  - ../../05-specs/api/account/member-authentication-api.md
  - ../../05-specs/api/admin/authentication-api.md
  - ../../06-architecture/security-boundary.md
  - ../platform/web-006-unified-login-rbac-route.md
  - auth-006-cookie-origin-defense.md
  - ../data/data-005-redis-refresh-token.md
supersedes:
  - ADR-AUTH-001
  - ADR-AUTH-002
  - ADR-DATA-005
superseded_by: null
---

# ADR-AUTH-007 통합 계정 RBAC와 세션

## 1. 상태

Accepted

이 ADR은 [ADR-AUTH-001](auth-001-spring-security-jwt.md)과 [ADR-AUTH-002](auth-002-member-jwt-refresh-token.md)를 대체한다. 두 문서에서 유지할 보안 통제를 아래에 완전히 다시 기술하며, 서로 다른 관리자·회원 계정·audience·쿠키·Redis namespace 결정은 폐기한다.

## 2. 배경과 문제

일반 회원과 관리자가 서로 다른 로그인 화면과 Token 체계를 사용하면 사용자가 역할에 따라 진입점을 선택해야 하고, 프론트와 백엔드에 인증 흐름이 중복된다. 팀은 `member_account`에 역할을 두고 로그인은 하나로 통합하되, 메인 페이지에서 현재 역할에 따라 관리자 진입을 제공하고 서버가 RBAC를 최종 강제하기로 합의했다.

결정은 다음을 함께 만족해야 한다.

- 공개 가입으로 관리자 권한을 획득할 수 없다.
- 역할·상태 변경이 이미 발급된 Access Token에 남아 권한을 지연 반영하지 않는다.
- 공개 API 가용성이 인증 저장소 장애에 종속되지 않는다.
- 기존 RS256 키 회전, Refresh 회전·재사용 탐지, `sid` 폐기와 Origin 방어를 잃지 않는다.

## 3. 결정

### 3.1 단일 계정과 역할

- `member_account`가 유일한 인증 계정 원장이다. 역할은 `MEMBER` 또는 `ADMIN` 하나다.
- 공개 회원가입 요청 Schema에는 `role`이 없다. 알 수 없는 `role` 입력은 `400`으로 거부하고 정상 가입은 서버가 항상 `MEMBER`를 부여한다.
- `ADMIN` 프로비저닝과 역할 변경은 승인·감사 가능한 운영 절차만 허용한다. 공개 UI·API는 두지 않는다.
- 역할·계정 상태·비밀번호 변경은 해당 계정의 모든 활성 세션을 폐기한다.

### 3.2 통합 로그인과 경로

- 이메일·비밀번호 로그인 화면은 하나이며 Token API는 `POST /api/auth/tokens`, `POST /api/auth/tokens/refresh`, `DELETE /api/auth/tokens`다.
- 기존 `/api/admin/auth/tokens*`는 redirect나 alias 없이 제거하고 정의되지 않은 경로로 거부한다.
- `/api/me`와 `/api/me/**`는 인증된 현재 계정의 본인 경계다.
- `/api/admin/**`는 유효한 인증과 현재 `member_account.status=ACTIVE`, `role=ADMIN`을 모두 요구한다.
- 인증 누락·무효·폐기 Token은 `401`, 인증됐지만 현재 역할이 부족하면 `403`, 상태·역할·폐기 여부를 안전하게 확인할 수 없으면 `503`으로 fail-closed 처리한다.

### 3.3 Access Token

- Access Token은 RS256 JWT, `iss=masit-on`, `aud=masit-on-api`, 최대 수명 30분이다.
- 최소 claim은 `sub`, `iss`, `aud`, `sid`, `jti`, `roles`, `iat`, `exp`다. `roles`는 로그인·재발급 시 DB의 현재 역할에서 만든 단일 값이다.
- 모든 서명 키는 `kid`를 가진다. 90일마다 새 검증 키 선배포 → 새 개인키 발급 전환 → Access Token 최대 수명 30분 경과 뒤 이전 개인키 폐기 순서로 교체한다.
- Access Token은 응답 본문으로 전달하고 브라우저 메모리에만 둔다. localStorage, sessionStorage, IndexedDB와 일반 쿠키에 저장하지 않는다.
- 이메일·상태·비밀번호·개인화 데이터와 Refresh Token은 claim에 넣지 않는다. `jti`는 Token 식별자이고 세션 폐기는 `sid`로 한다.

### 3.4 현재 역할과 즉시 거부

보호 경계는 JWT의 `roles`만 믿지 않는다. 요청마다 현재 DB 상태·역할과 `sid` 폐기 표식을 확인하거나, 역할·상태 변경과 모든 세션 폐기가 원자적으로 완료됨을 보장하는 동등한 통제를 사용한다. 역할 강등·비활성·비밀번호 변경 직후 이전 Access Token은 거부되어야 한다.

폐기 표식은 최초 폐기 시각을 늦추거나 보호 만료를 줄이지 않는 멱등 upsert로 저장하고 Access Token 최대 수명 이상 유지한다. Redis 세션이 남아 있어도 폐기 표식이 있으면 Refresh를 거부하고 정리한다. 상태·폐기 저장소 장애에서는 보호 API를 `503`으로 거부한다.

### 3.5 Refresh 세션

- Refresh Token은 고엔트로피 불투명 값이고 회전 시점부터 14일간 유효하다.
- 쿠키는 `__Secure-masiton-refresh`, `Path=/api/auth/tokens`, `HttpOnly`, `Secure`, `SameSite=Strict`, `Domain` 생략이다.
- Redis `auth:session:` namespace에는 원문이 아닌 SHA-256 해시와 `sid`, family, 현재 Token, 생성·만료·회전 상태만 둔다.
- 로그인 생성, 회전, 재사용 탐지, 세션 폐기와 상한 퇴출은 Lua script 또는 동등한 단일 원자 연산으로 수행한다. 같은 Refresh Token의 동시 재발급은 하나만 성공한다.
- 이미 사용·폐기됐거나 현재 Token이 아닌 값이 제출되면 재사용으로 판정하고 family 전체를 폐기한다.
- `MEMBER`는 최대 3세션, `ADMIN`은 최대 1세션이다. 상한을 넘는 로그인은 최초 생성 시각이 가장 오래된 세션을 폐기하며 Refresh 회전은 순서를 바꾸지 않는다.
- Redis 장애 중 로그인·재발급은 fail-closed다. 로그아웃은 서버 폐기가 확인된 경우만 성공하며 실패를 성공으로 가장하지 않는다.

### 3.6 Origin과 요청 제한

Refresh 쿠키가 사용되는 재발급·로그아웃은 Bearer·Refresh Token 조회, 회전과 폐기보다 먼저 `Origin`을 검사한다. 정확히 하나의 HTTPS Origin이 배포 allowlist와 canonical form으로 일치해야 하며 누락·다중·불일치는 Token 상태를 바꾸지 않고 `403`이다.

모든 로그인 시도는 JSON·이메일·비밀번호 형식이 잘못됐더라도 자격 증명·계정 검증 전에 요청 출처 제한을 원자 적용한다. 형식이 유효하면 정규화 이메일 기반 제한도 함께 적용한다. 계정 존재·상태·역할·비밀번호 오류와 제한 종류는 동일한 `401 INVALID_CREDENTIALS`로 일반화한다.

클라이언트 주소는 trusted proxy peer에서 온 단일 전달 값만 신뢰한다. 외부 요청의 전달 헤더는 reverse proxy가 원격 주소로 덮어쓰고, peer가 신뢰 목록에 없거나 값이 누락·다중·잘못된 경우 요청 peer 주소로 fallback한다. reverse proxy가 활성화됐는데 trusted proxy 설정이 비어 있으면 애플리케이션 시작을 실패시킨다.

제한 key에는 원문 이메일·주소를 넣지 않고 용도 분리된 비밀을 사용한 HMAC 결과만 둔다. 비밀은 JWT 서명키·Token 해시 용도와 분리한다.

### 3.7 클라이언트 RBAC

로그인·재발급과 `GET /api/me`는 현재 `role`을 반환한다. 프론트는 TanStack Query 현재 사용자 Query를 인증 서버 상태의 단일 원천으로 삼아 메인 페이지 관리자 링크와 `/admin/**` Route Guard를 갱신한다.

링크와 Route Guard는 권위 있는 인가가 아니다. 캐시 변조·직접 URL 접근·링크 구성과 관계없이 서버 `/api/admin/**`가 현재 역할을 판정한다. 안전한 `returnTo`는 동일 Origin의 허용된 내부 경로만 수락하며 외부 URL·프로토콜 상대 URL·권한 없는 관리자 경로를 거부한다.

### 3.8 공개 경계와 비밀정보

공개 맛집·Creator API는 인증 없이 유지한다. 선택적 인증을 사용하는 공개 상세는 누락·만료·변조·폐기 Token과 인증·개인화 저장소 장애를 익명 `200`으로 격리하고 부수효과만 생략한다.

비밀번호, Access·Refresh·Action Token 원문, Authorization·Cookie 헤더, JWT 개인키, 제한 HMAC 비밀, 원문 이메일·클라이언트 주소를 저장소·로그·오류·메트릭 label·분석 이벤트에 남기지 않는다. 모든 오류는 일반화된 메시지와 서버 생성 `traceId`만 제공한다.

## 4. 결과와 트레이드오프

사용자는 역할을 선택하지 않고 로그인하며 현재 역할에 맞는 화면으로 이동할 수 있고 인증 구현의 중복이 줄어든다. 반면 관리자와 회원이 같은 인증 기반을 공유하므로 역할 mass assignment와 서버 RBAC 회귀가 더 큰 위험이 된다. 이를 공개 역할 입력 거부, 현재 DB 역할 확인, 변경 시 전 세션 폐기와 독립 보안 테스트로 통제한다.

## 5. 기각한 선택지

| 선택지 | 판단 | 이유 |
|---|---|---|
| 관리자·회원 로그인과 audience 유지 | 기각 | 사용자 진입과 인증 상태가 이중화되고 통합 로그인 목표를 만족하지 않는다. |
| JWT `roles`만으로 관리자 인가 | 기각 | 강등·비활성 변경이 Access 만료까지 지연된다. |
| 프론트 Route Guard만 사용 | 기각 | 직접 API 호출과 캐시 변조를 막지 못한다. |
| 공개 가입에서 역할 입력 허용 | 기각 | mass assignment로 관리자 권한 상승이 가능하다. |
| Access Token 영구 저장 | 기각 | 브라우저 저장소 탈취 시 노출 기간과 범위가 커진다. |

## 6. 검증

- 공개 가입에 `role`을 넣은 정상·변형 요청이 거부되고 생성 계정은 항상 `MEMBER`인지 검증한다.
- 통합 로그인·재발급·`GET /api/me`가 DB의 현재 역할을 반환하는지 검증한다.
- `MEMBER`의 직접 `/api/admin/**` 호출은 `403`, 무인증·무효 Token은 `401`, 상태 저장소 장애는 `503`인지 검증한다.
- 역할·상태·비밀번호 변경 직후 기존 Access·Refresh Token 전체가 거부되는지 검증한다.
- `MEMBER` 3세션, `ADMIN` 1세션 상한과 동시 로그인 퇴출 원자성을 검증한다.
- Refresh 회전, 동시 재발급 한 건 성공, 재사용 family 폐기와 `sid` 폐기 표식을 검증한다.
- Origin 누락·다중·불일치가 Token 조회·변경보다 먼저 거부되는지 검증한다.
- 형식 오류를 포함한 모든 자격 증명 시도가 검증 전에 요청 출처 제한을 거치고 응답으로 계정·상태·역할을 열거할 수 없는지 검증한다.
- trusted proxy 오구성 시작 실패와 전달 헤더 spoofing 거부를 검증한다.
- Access Token이 메모리에만 있고 비밀번호·Token·헤더·비밀 원문이 로그와 분석에 없는지 검증한다.
- 인증 저장소 장애 중 공개 API와 선택적 인증 공개 상세가 계속 사용 가능한지 검증한다.

## 7. 재검토 조건

한 계정에 복수 역할, 기능별 관리자 권한, 외부 IdP, MFA, 세션 원격 관리 또는 별도 인증 서비스를 도입할 때 재검토한다.
