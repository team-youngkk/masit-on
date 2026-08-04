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

Accepted. 2026-08-03 제한 공개의 반복 인증창 문제를 해결하기 위해 기존 Nginx Basic Auth 결정을 변경했다. [OPS-VALIDATION 공통 운영·배포 트랙](../../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙)이 결정 적용을 소유하고, 구현과 운영 전환은 [E1-T13](../../08-planning/expansion-1-task-breakdown.md#e1-t13-검증-참여자-제한-공개-쿠키-세션-전환)에서 추적한다.

## 2. 결정 요약

검증 참여자 제한 공개는 전용 로그인 화면과 서버 측 쿠키 세션으로 처리한다. 성공 시 128-bit 이상의 불투명 세션 ID를 `__Host-masiton-verification` HttpOnly 쿠키로 발급하고 Redis에는 SHA-256 해시와 만료만 저장한다. Nginx는 `auth_request`로 내부 검증 Endpoint를 호출해 화면과 API 진입을 허용한다.

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
- `/internal/**` 외부 `404` 경계는 유지한다.
- 그 밖의 화면과 `/api/**`는 Nginx `auth_request`로 세션을 확인한다.
- 화면 요청의 무효 세션은 로그인 화면으로 이동시키고, API 요청은 `401 VALIDATION_ACCESS_REQUIRED` JSON을 반환한다. `WWW-Authenticate: Basic`은 사용하지 않는다.
- 내부 검증 subrequest 경로는 `internal` Nginx location으로 두어 인터넷에서 직접 호출할 수 없게 한다.

## 5. 회원·관리자 인증과 분리

검증 세션은 제한 공개 진입 허용만 뜻하며 회원 ID, 관리자 ID, 역할과 서비스 권한을 포함하지 않는다. 검증 쿠키가 있어도 `/api/me/**`는 회원 Bearer JWT, `/api/admin/**`는 관리자 Bearer JWT와 `ADMIN` 권한을 별도로 검증한다. 회원·관리자 로그인이나 로그아웃은 검증 세션을 생성·삭제하지 않는다.

## 6. Redis 장애와 복구

Redis에서 검증 세션을 확인할 수 없으면 제한 공개 요청은 fail-closed한다. 공개 조회까지 일시적으로 차단될 수 있으므로 운영 로그와 CloudWatch에서 검증 세션 저장소 장애를 회원·관리자 인증 장애와 구분한다. 장애 복구 뒤 유효 세션이 보존되지 않았다면 검증 참여자 로그인만 다시 수행한다.

## 7. 검토한 대안

| 대안 | 판단 | 이유 |
|---|---|---|
| Basic Auth 유지 | 기각 | Bearer와 동일한 `Authorization` 헤더 충돌을 제거하지 못한다. |
| 모든 Bearer 요청의 Basic 면제 | 기각 | 가짜 Bearer로 공개·선택적 인증 API의 제한 공개를 우회할 수 있다. |
| 회원 보호 API만 Basic 면제 | 임시 대안 | 일부 반복창은 줄지만 선택적 인증 API와 신규 경로마다 예외 관리가 필요하다. |
| 회원 Access Token을 쿠키로 전환 | 기각 | 제한 공개 문제 때문에 회원 인증·CSRF 계약 전체를 바꾸는 범위 확대다. |
| IP allowlist | 기각 | 유동 IP와 모바일 검증 참여자 운영에 맞지 않는다. |

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
- API 실패에 `WWW-Authenticate: Basic`이 없고 화면만 로그인 경로로 이동하는지 확인한다.
- 정식 공개 제거 리허설에서 검증 쿠키 없이 공개 화면·API가 동작하고 회원·관리자 인증 회귀가 통과해야 한다.
