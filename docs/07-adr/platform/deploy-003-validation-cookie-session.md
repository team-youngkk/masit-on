---
id: ADR-DEPLOY-003
title: 검증 참여자 제한 공개 쿠키 세션
status: Accepted
decision_date: 2026-08-03
owners:
  - 이우람
related_requirements:
  - NFR-SECURITY-001
  - NFR-SECURITY-003
  - NFR-DEPLOYMENT-002
  - NFR-DEPLOYMENT-004
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../05-specs/api/common/validation-access-contract.md
  - ../../06-architecture/security-boundary.md
  - ../../08-planning/m2-deployment-plan.md
  - ../../08-planning/expansion-1-task-breakdown.md
  - web-003-routing-boundary.md
  - ../security/sec-001-secrets-workload-identity.md
supersedes: []
supersedes_decision: M2-11 Nginx Basic Auth 제한 공개
superseded_by: null
---

# ADR-DEPLOY-003 검증 참여자 제한 공개 쿠키 세션

## 1. 상태

Accepted. 2026-08-03 제한 공개의 반복 인증창 문제를 해결하기 위해 기존 Nginx Basic Auth 결정을 변경했다. 2026-08-14 Issue #197 후속 결정으로 비관리자 공개 제품 API는 검증 세션 gate보다 먼저 허용하되 관리자·미정의 API는 gate를 유지하도록 범위를 명확히 했다. [OPS-VALIDATION 공통 운영·배포 트랙](../../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙)이 결정 적용을 소유하고, 구현과 운영 전환은 [E1-T13](../../08-planning/expansion-1-task-breakdown.md#e1-t13-검증-참여자-제한-공개-쿠키-세션-전환)에서 추적한다.

## 2. 결정 요약

검증 참여자 제한 공개는 전용 로그인 화면과 서버 측 쿠키 세션으로 처리한다. 성공 시 128-bit 이상의 불투명 세션 ID를 `__Host-masiton-verification` HttpOnly 쿠키로 발급하고 Redis에는 SHA-256 해시와 만료만 저장한다. Nginx는 보호 화면·API에서 `auth_request`로 내부 검증 Endpoint를 호출한다. 비관리자 공개 제품 API는 Spring Security의 `permitAll` 경로·Method 계약과 일치하는 요청만 gate 전에 백엔드로 전달하고, 관리자·미정의 API와 공개 경로의 비허용 Method는 계속 검증 세션을 요구한다.

검증 세션은 회원·관리자 인증과 별개다. 회원·관리자 Access Token은 계속 `Authorization: Bearer`를 사용하고 Refresh Token 정책도 바꾸지 않는다. 정식 공개 시 검증 로그인·세션·쿠키·Nginx 검증 경계와 전용 비밀정보를 함께 제거한다.

## 3. 배경

M2-11은 전체 사이트에 Nginx Basic Auth를 적용했다. Basic과 회원·관리자 Bearer JWT가 모두 `Authorization` 헤더를 사용하므로 동시에 전달할 수 없다. 관리자 Bearer 경로만 부분 면제했지만 회원 `/api/me/**`, 선택적 회원 인증과 세션 복구 요청에서 Nginx가 다시 `401`과 `WWW-Authenticate: Basic`을 반환해 한 화면에서 인증창이 여러 번 표시된다.

브라우저의 자격 증명 기억 문제가 아니라 동일 헤더를 두 인증 체계가 공유한 구조적 충돌이므로 Basic Auth 캐시 조정으로 해결하지 않는다.

## 4. 계약

### 4.1 로그인과 쿠키

- 화면: `GET /verification/login`
- 세션 생성: `POST /api/verification/sessions`
- 세션 종료: `DELETE /api/verification/sessions`
- 쿠키: `__Host-masiton-verification`; `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, `Domain` 미지정. CSRF는 세션 생성·종료 API의 Origin 검사(4.2절)로 막으므로, `SameSite=Strict` 대신 `Lax`를 써서 이메일 링크 등 외부에서 시작하는 top-level 이동에서도 세션 쿠키가 전달돼 재로그인을 유발하지 않는다([PR #129 리뷰](../../troubleshooting/pr-129-deploy-cutover-and-rate-limit-review.md)).
- 수명: 발급 시점부터 7일 고정 만료. 활동에 따른 연장과 Refresh Token은 두지 않는다.
- 세션 원문: CSPRNG 128-bit 이상. 응답 본문·로그·URL·Redis에 남기지 않는다.
- Redis: `auth:verification:session:{sha256(sessionId)}`에 최소 상태와 만료만 저장한다.

### 4.2 자격 증명과 제한

- 검증 참여자 로그인 ID와 비밀번호 해시는 Parameter Store SecureString에서 주입한다.
- 비밀번호 원문, 쿠키 원문과 해시는 로그·응답·메트릭 label에 남기지 않는다.
- 로그인 실패는 신뢰된 요청 출처와 로그인 ID별 15분 5회로 제한하고 성공 시 해당 실패 카운터를 정리한다.
- 성공·실패 응답은 등록된 검증 참여자 여부를 구분하지 않는다.
- 세션 생성·종료는 HTTPS 동일 Origin과 `Origin` 검증을 요구한다.

### 4.3 Nginx 경계

- `/verification/login`, 세션 생성·종료 Endpoint와 로그인 화면에 필요한 정적 자산은 세션 검사 전에 허용한다. 세션 종료는 Backend가 쿠키를 직접 검증하고 누락·만료도 멱등 성공으로 정리한다.
- **비관리자 공개 제품 API는 Spring Security가 `permitAll`로 공개한 경로와 Method만 세션 검사 전에 허용한다.** Nginx의 exact·anchored 단일 segment 규칙으로 범위를 고정하고 `Authorization`과 `Cookie`를 보존해 선택적 회원 인증과 Refresh Token을 Backend가 계속 검증하게 한다. 같은 경로의 비허용 Method, `/api/admin/**`와 정의되지 않은 `/api/**`는 gate를 유지한다.
- **외부 시스템이 호출하는 Callback 경로는 세션 검사 전에 허용한다.** 브라우저가 아닌 호출자는 검증 쿠키를 가질 수 없으므로 gate 안에 두면 기능이 성립하지 않는다. 허용 조건은 두 가지다. 경로가 자체 인증 수단(공유 비밀 서명 또는 서버가 발급한 검증 Token)으로 호출자를 확인해야 하고, 무인증 요청이 백엔드 자원에 도달하므로 Nginx에서 메서드·본문 크기·호출률을 제한해야 한다. 이 예외는 제한 공개 범위를 넓히지 않는다.
- `/internal/**` 외부 `404` 경계는 유지한다.
- 위 조건의 목록에 없는 화면과 `/api/**`는 Nginx `auth_request`로 세션을 확인한다. 제외 경로의 **목록과 각 경로의 인증 수단은 [검증 참여자 제한 공개 API 계약](../../05-specs/api/common/validation-access-contract.md)의 세션 gate 제외 경로 절이 단일 목록으로 소유한다.** 이 ADR은 제외를 허용하는 조건을, 그 계약은 실제 목록을 정한다.
- 화면 요청의 무효 세션은 로그인 화면으로 이동시키고, API 요청은 `401 VALIDATION_ACCESS_REQUIRED` JSON을 반환한다. `WWW-Authenticate: Basic`은 사용하지 않는다.
- 내부 검증 subrequest 경로는 `internal` Nginx location으로 두어 인터넷에서 직접 호출할 수 없게 한다.

## 5. 회원·관리자 인증과 분리

검증 세션은 제한 공개 진입 허용만 뜻하며 회원 ID, 관리자 ID, 역할과 서비스 권한을 포함하지 않는다. 검증 쿠키가 있어도 `/api/me/**`는 회원 Bearer JWT, `/api/admin/**`는 관리자 Bearer JWT와 `ADMIN` 권한을 별도로 검증한다. gate에서 제외한 비관리자 공개 API도 Nginx가 보존한 Bearer·쿠키를 Spring Security와 각 API가 계약대로 해석한다. 회원·관리자 로그인이나 로그아웃은 검증 세션을 생성·삭제하지 않는다.

## 6. Redis 장애와 복구

Redis에서 검증 세션을 확인할 수 없으면 gate가 필요한 제한 공개 요청은 fail-closed한다. gate에서 제외한 비관리자 공개 API와 자체 인증 Callback은 검증 세션 저장소 장애와 독립적으로 동작한다. 운영 로그와 CloudWatch에서 검증 세션 저장소 장애를 회원·관리자 인증 장애와 구분하고, 장애 복구 뒤 유효 세션이 보존되지 않았다면 검증 참여자 로그인만 다시 수행한다.

## 7. 검토한 대안

| 대안 | 판단 | 이유 |
|---|---|---|
| Basic Auth 유지 | 기각 | Bearer와 동일한 `Authorization` 헤더 충돌을 제거하지 못한다. |
| 모든 Bearer 요청의 Basic 면제 | 기각 | 가짜 Bearer로 공개·선택적 인증 API의 제한 공개를 우회할 수 있다. |
| 회원 보호 API만 Basic 면제 | 임시 대안 | 일부 반복창은 줄지만 선택적 인증 API와 신규 경로마다 예외 관리가 필요하다. |
| 비관리자 공개 API도 모두 검증 세션 요구 | 기각 | Spring Security의 공개 계약과 Nginx의 운영 gate가 상충하고, 검증 세션 장애가 공개 제품 API까지 불필요하게 차단한다. |
| 회원 Access Token을 쿠키로 전환 | 기각 | 제한 공개 문제 때문에 회원 인증·CSRF 계약 전체를 바꾸는 범위 확대다. |
| IP allowlist | 기각 | 유동 IP와 모바일 검증 참여자 운영에 맞지 않는다. |

### 7.1 선택한 결정의 트레이드오프

비관리자 공개 API를 gate에서 제외하면 공개 API 자체는 검증 참여자에게만 한정되지 않는다. 대신 이는 제품 API가 이미 정한 공개 범위와 일치하며, Nginx가 별도 권한 체계로 그 범위를 뒤집지 않게 한다. 반대로 공개 API가 추가·변경될 때 Spring Security, API 계약과 Nginx allowlist를 함께 갱신해야 하므로 정적 계약 테스트와 운영 smoke로 목록·Method 드리프트를 차단한다.

## 8. 정식 공개 전환

정식 공개 Task는 다음을 한 변경 단위로 제거한다.

1. Nginx `auth_request`와 로그인 예외 경로
2. 검증 로그인 화면과 세션 API·내부 검증 Adapter
3. Redis `auth:verification:*` key와 쿠키
4. Parameter Store 검증 참여자 비밀정보와 배포 주입
5. 제한 공개 전용 테스트·알람

회원·관리자 인증은 이 제거의 영향을 받지 않아야 한다.

## 9. 검증

- 최초 로그인 뒤 서로 다른 화면과 API 이동에서 추가 로그인창이 0회인지 브라우저로 검증한다.
- 회원 로그인·`/api/me`·찜·최근 기록과 관리자 등록 Bearer 요청이 검증 쿠키와 동시에 동작하는지 확인한다.
- 쿠키 누락·변조·만료·Redis 장애, 로그인 실패 제한과 로그 비밀정보 미노출을 검증한다.
- 비관리자 공개 API 전체가 검증 쿠키 없이 Backend 응답에 도달하고 3xx 로그인 redirect를 반환하지 않는지 확인한다.
- 공개 경로의 비허용 Method, `/api/admin/**`와 정의되지 않은 `/api/**`는 `401 VALIDATION_ACCESS_REQUIRED`를 반환하는지 확인한다.
- API 실패에 `WWW-Authenticate: Basic`이 없고 화면만 로그인 경로로 이동하는지 확인한다.
- 정식 공개 제거 리허설에서 검증 쿠키 없이 공개 화면·API가 동작하고 회원·관리자 인증 회귀가 통과해야 한다.
