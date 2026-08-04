---
related_documents:
  - README.md
  - ../08-planning/expansion-1-task-breakdown.md
  - ../07-adr/platform/deploy-003-validation-cookie-session.md
  - ../05-specs/api/common/validation-access-contract.md
  - ../05-specs/api/discovery/map-discovery-api.md
  - ../01-requirements/business-rules.md
  - ../../CLAUDE.md
---

# PR #129 리뷰 트러블슈팅: Basic Auth 전환 안전장치와 이메일 인증 rate limit 우회

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#129 검증 참여자 쿠키 세션과 지도·이메일 인증 개선을 운영 배포한다](https://github.com/team-youngkk/masit-on/pull/129) |
| 작성자 | tjdgns0618 |
| 처리 일자 | 2026-08-04 |
| 범위 | 2절 표에 나열된 인라인 리뷰 스레드 |
| 주 문제 유형 | 애플리케이션(회원 인증 rate limit), 배포(Basic Auth 전환), 인프라(Nginx), 문서(API 계약·Javadoc) |
| 기존 기록 | 없음. 이 PR이 승격하는 개별 PR(#122~#124)의 리뷰는 각각 [pr-122](pr-122-map-viewport-independent-query-review.md)·[pr-123](pr-123-verification-session-review.md)·[pr-124](pr-124-email-verification-code-review.md)에 이미 있고, 이번 지적은 그 PR들이 이미 `develop`에 병합된 뒤 `main` 승격 시점에 새로 발견된 것이라 별도 기록으로 남긴다 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [새 세션 경계 검증 후에만 Basic Auth를 제거](https://github.com/team-youngkk/masit-on/pull/129#discussion_r3709174205) (P1, 이우람) | `nginx-install.sh`가 새 백엔드의 `/internal/verification/session` 경계 상태와 무관하게 Basic Auth drop-in을 즉시 삭제하고 새 gate로 전환해, 백엔드가 준비되지 않은 채 실행하면 제한 공개 전체가 중단될 수 있다는 지적 | 배포 | 수정 필요 | Basic Auth 제거 전에 `curl`로 `/internal/verification/session`이 401을 응답하는지 확인하고, 아니면 기존 구성을 그대로 두고 중단하도록 스크립트를 수정 | `bash -n`로 구문 확인. `app-deploy.sh`의 기존 헬스체크 패턴(`curl -sS -w '%{http_code}'`)과 동일한 방식임을 대조 확인 |
| [token 필드 누락은 이메일 인증 제출 제한을 소모하지 않음](https://github.com/team-youngkk/masit-on/pull/129#discussion_r3709202984) (should-fix, 박진영) | 컨트롤러가 `token` 필드 부재를 서비스 호출(rate limiter 포함) 전에 걸러내, 필드를 생략한 반복 요청이 10분/10회 제한을 전혀 소모하지 않는다는 지적 | 애플리케이션 | 수정 필요 | 컨트롤러의 사전 검증을 제거하고 `null`을 서비스로 그대로 전달. 서비스가 rate limiter를 통과시킨 뒤 `token == null`을 확인해 같은 `MISSING_REQUIRED_FIELD`를 던지도록 이동 | `MemberAuthenticationServiceTest`·`MemberAuthenticationControllerTest`에 회귀 테스트 추가, `./gradlew test` 통과 |
| [isBlocked==true(429) 분기 테스트 없음](https://github.com/team-youngkk/masit-on/pull/129#discussion_r3709202967) (should-fix, 박진영) | fail-closed가 핵심 속성인데 차단 임계치 도달 시나리오를 검증하는 테스트가 없다는 지적 | 애플리케이션 | 수정 필요 | `VerificationSessionServiceTest`에 `isBlocked=true` 시 자격 증명 확인 없이 429를 반환하는 테스트 추가 | 새 테스트 실행 통과 |
| [revoke 실제 경로·Testcontainers 통합 테스트 없음](https://github.com/team-youngkk/masit-on/pull/129#discussion_r3709202972) (should-fix, 박진영) | 쿠키가 있는 로그아웃(성공/Redis 장애) 경로가 테스트되지 않고, `RedisVerificationAccessStore`가 Member 쪽과 달리 Testcontainers 통합 테스트 없이 Mock으로만 검증된다는 지적 | 애플리케이션·테스트 | 수정 필요 | `VerificationSessionServiceTest`·`VerificationSessionControllerTest`에 실제 세션 ID revoke·Redis 장애 케이스 추가. `RedisMemberRateLimitStoreIntegrationTest`와 같은 형태로 `RedisVerificationAccessStoreIntegrationTest`(Testcontainers) 신설 | 신설 테스트 4건 포함 전체 실행 통과 |
| [로그인·출처 키 INCR+EXPIRE가 원자적이지 않음](https://github.com/team-youngkk/masit-on/pull/129#discussion_r3709202976) (minor, 박진영) | 두 키에 대한 증가·만료 설정이 별도 Redis 호출 두 번이라, 그 사이 장애가 끼면 두 카운터가 어긋날 수 있다는 지적 | 애플리케이션 | 수정 필요 | `RedisMemberRateLimitStore.RECORD_LOGIN_FAILURE`와 같은 다중 키 Lua 스크립트로 통합해 두 키를 한 번에 원자적으로 처리 | 통합 테스트로 두 키 모두 정상 임계치에서 차단됨을 확인 |
| [`/api/verification/sessions`에 Authorization 헤더 미제거](https://github.com/team-youngkk/masit-on/pull/129#discussion_r3709202981) (minor, 박진영) | 다른 `/_verification/*` 내부 위치와 달리 이 클라이언트 대상 위치는 `Authorization` 헤더를 비우지 않아, 헤더 분리 목표와 서술이 일치하지 않는다는 지적 | 인프라 | 수정 필요 | 이 위치에도 `proxy_set_header Authorization "";` 추가 | 다른 세 위치와 동일한 지시어임을 대조 확인. 로컬에 `nginx` 바이너리가 없어 `nginx -t` 실행은 못했다(8절 참고) |
| [SameSite=Strict가 크로스사이트 진입에서 재로그인 유발](https://github.com/team-youngkk/masit-on/pull/129#discussion_r3709202982) (minor, 박진영) | 이메일 링크 등 외부에서 최초 진입하는 top-level navigation에서 `SameSite=Strict` 쿠키가 탈락해 재로그인을 유발하며, CSRF는 Origin 검사로 이미 커버되므로 `Lax`가 안전하다는 제안 | 애플리케이션·계약 | 결정 필요 | 코드는 시도 후 되돌렸다. `SameSite=Strict`는 [ADR-DEPLOY-003](../07-adr/platform/deploy-003-validation-cookie-session.md) 51행과 [검증 참여자 API 계약](../05-specs/api/common/validation-access-contract.md) 33행에 명시된 Accepted 계약값이라, CLAUDE.md 7절에 따라 소유자(OPS-VALIDATION, 이우람) 합의 없이 변경하지 않음 | ADR·API 계약 문서에서 `SameSite=Strict` 문구 직접 확인 |
| [CLAUDE.md 병합 방식 정책 전환의 근거 불명확](https://github.com/team-youngkk/masit-on/pull/129#discussion_r3709202987) (should-fix, 박진영) | 병합 방식이 기존 정책에서 뒤바뀐 형태로 바뀌었는데 트러블슈팅 기록·ADR 어디에도 이 전환 자체의 근거가 없고, 실제 GitHub ruleset이 문서와 일치하도록 재설정됐는지 확인이 필요하다는 요청 | 문서·거버넌스 | 결정 필요(일부는 이미 해결) | 실제 ruleset(`Protect develop`=squash, `Protect main`=merge)이 현재 문서와 일치함을 확인(이미 해결). 다만 이 정책 전환 자체의 근거는 `implementation-conventions.md`의 자기 인용과 PR #121의 포괄적 "2026-08-03 승인" 외에 별도 ADR이 없음을 확인 — ADR 작성 여부는 이 PR 범위를 벗어나는 팀 결정으로 남김 | `gh api repos/.../rulesets/19533356`·`19533400`으로 `allowed_merge_methods` 재조회, `docs/07-adr/` 전체에서 병합 방식 관련 ADR 부재 확인 |
| [bounds 검증 제거 후 Javadoc이 옛 순서를 서술](https://github.com/team-youngkk/masit-on/pull/129#discussion_r3709202990) (minor, 박진영) | bounds 검증 단계를 제거했는데 Javadoc은 여전히 "BR-MAP-002~004 순서(호출 제한 -> 기존 필터 AND)"로 남아 있어, BR-MAP-003(결과 상한)이 실제로는 Query Port 호출 이후 결과 건수로 판정된다는 사실과 어긋난다는 지적 | 문서 | 수정 필요 | Javadoc을 실제 순서(호출 제한 확인 → BR-MAP-002 조건으로 Query Port 호출 → BR-MAP-003 초과 여부를 결과 건수로 판정)로 재작성 | `business-rules.md`의 BR-MAP-002~004 서술과 실제 코드 순서를 대조해 일치 확인 |
| [addressSummary 필드 설명이 무관하게 변경됨](https://github.com/team-youngkk/masit-on/pull/129#discussion_r3709202992) (minor, 박진영) | 이번 PR 목적(지도 뷰포트 비종속 조회)과 무관해 보이는 응답 필드 설명·예시 변경("저장된 전체 도로명주소" → "주소 요약")이 실제 매핑 코드에서 검증되지 않았다는 지적 | 문서 | 수정 필요 | `RestaurantMapQueryAdapter.java:60`이 `road_address`를 그대로 `address_summary`로 select함을 확인 — 코드는 바뀌지 않았고 문서만 잘못 바뀐 드리프트였다. 설명·예시를 원래 값("저장된 전체 도로명주소", "서울특별시 마포구 월드컵로 1")으로 되돌림 | `grep`으로 SQL의 `road_address AS address_summary` 확인. 프론트엔드 테스트 픽스처(`map-points-response.test.ts:9`)가 이미 전체 주소 예시를 쓰고 있어 원복이 실제 동작과 일치함을 재확인 |

## 3. 문제 현상과 발생 조건

### 3.1 Basic Auth 제거가 새 백엔드 상태를 확인하지 않음

- 오류 메시지: 없음(발생 전 발견)
- 발생 환경: 운영 EC2, `deploy/scripts/nginx-install.sh` 실행 시점
- 재현 조건: 새 백엔드(`/internal/verification/session` 경계 포함)를 아직 배포·검증하지 않은 상태에서 `nginx-install.sh`를 실행한다.
- 실제 결과: 스크립트가 무조건 Basic Auth systemd drop-in을 삭제하고 새 `auth_request` gate 설정으로 Nginx를 재기동한다. 백엔드가 준비되지 않았으면 모든 보호된 요청이 `error_page 500 503 = /_verification/unavailable`로 막혀 제한 공개 서비스 전체가 중단된다.
- 기대 결과: [E1-T13 배포 전환](../08-planning/expansion-1-task-breakdown.md) 계약("새 세션 경계를 먼저 배포·검증한 뒤에만 Basic Auth를 제거한다. 실패 시 직전 Basic 구성으로 복구")을 스크립트 자체가 강제해야 한다.
- 영향 범위: 운영 배포 절차. 순서를 지키지 않는 실수 한 번으로 제한 공개 전체가 중단될 수 있다.

### 3.2 이메일 인증 rate limiter가 필드 누락 요청을 세지 않음

- 오류 메시지: 없음
- 발생 환경: `POST /api/auth/email-verifications`, `token` 필드가 없는 요청(`{}` 등)
- 재현 조건: `token` 필드를 아예 생략하거나 `null`로 보낸 요청을 반복한다.
- 실제 결과: 컨트롤러가 `service.verifyEmail(...)` 호출 전에 `MISSING_REQUIRED_FIELD` 400을 던져, rate limiter(`acquireEmailVerificationAttempt`)가 있는 서비스 코드에 도달하지 않는다. 즉 이 요청 형태는 "10분/10회" 제한을 전혀 소모하지 않는다.
- 기대 결과: 이 엔드포인트의 존재 이유가 코드 추측 시도 제한이므로, 필드 형태와 무관하게 모든 제출 시도가 같은 출처 제한을 소모해야 한다.
- 영향 범위: 이메일 인증 코드 제출 제한의 완전성. 실제 코드 추측에는 유효한 `token` 문자열이 필요해 즉시 악용되는 경로는 아니지만, 제한이 설계 의도대로 전체 요청을 세지 못하는 것은 사실이다.

### 3.3 검증 세션 저장소의 실제 저장·차단·원자성 동작이 검증되지 않음

- 오류 메시지: 없음
- 발생 환경: `RedisVerificationAccessStore` (실제 Redis 대상 테스트 부재)
- 재현 조건: 이 클래스의 기존 테스트 커버리지를 확인한다.
- 실제 결과: 실행 전 테스트가 하나도 없었다. "원문이 아니라 SHA-256 해시만 저장한다", "임계치에서 차단한다", "실패 기록이 원자적으로 두 키에 반영된다"는 주장이 모두 `VerificationAccessStore` 인터페이스를 Mock한 `VerificationSessionServiceTest`에서만 간접 확인됐고, 실제 Redis 동작은 한 번도 실행되지 않았다.
- 기대 결과: `RedisMemberRateLimitStoreIntegrationTest`처럼 실제 Redis(Testcontainers)를 대상으로 하는 통합 테스트가 있어야 한다.
- 영향 범위: 회귀 방지. Redis 키 구조나 Lua 스크립트를 잘못 고쳐도 기존 테스트로는 잡히지 않았다.

### 3.4 문서·주석이 최신 구현과 어긋남 (Javadoc, API 계약 예시)

- 오류 메시지: 없음
- 발생 환경: `RestaurantMapPointsQueryService.java` Javadoc, `docs/05-specs/api/discovery/map-discovery-api.md`
- 재현 조건: PR #122(지도 뷰포트 비종속 조회 전환)가 bounds 검증을 제거한 뒤, 이 Javadoc과 API 문서를 원래 코드·의도와 대조한다.
- 실제 결과: Javadoc은 여전히 "BR-MAP-002~004 순서로 검증한 뒤 Query Port를 호출한다"고 서술해, BR-MAP-003(결과 200건 상한)이 Query Port 호출 **이전**에 검증되는 것처럼 읽힌다. API 문서는 `addressSummary` 필드 설명·예시를 "주소 요약"·구 단위로 바꿨는데, 실제 SQL(`RestaurantMapQueryAdapter.java:60`)은 그대로 `road_address`(전체 도로명주소)를 select한다.
- 기대 결과: 주석·문서가 실제 코드 순서·반환값과 일치해야 한다.
- 영향 범위: 문서 정확성. 런타임 동작에는 영향이 없다(어느 쪽도 코드 자체를 바꾸지 않았다).

## 4. 근본 원인

3.1은 `nginx-install.sh`가 "배포 전환" 계약(문서에만 서술된 운영 절차)을 스크립트 수준에서 강제하지 않고, 운영자가 올바른 순서(백엔드 먼저)로 스크립트를 실행할 것이라고만 가정한 것이 원인이다.

3.2는 컨트롤러가 요청 스키마 검증(필드 존재 여부)과 애플리케이션 rate limiting을 서로 다른 계층에서 서로 다른 순서로 처리하면서, "모든 제출 시도를 센다"는 rate limiter의 암묵적 전제를 검증하지 않은 것이 원인이다. `token`이 없는 요청은 정상 스키마 검증 오류로만 보였고, 이게 제한을 우회하는 경로라는 것을 구현 시점에 인지하지 못했다.

3.3은 이 클래스가 새로 추가되면서, 같은 패턴(Redis 다중 키 rate limiting)을 이미 다루는 `member` 도메인의 Testcontainers 통합 테스트 관행을 이번 `security` 도메인 구현에 적용하지 않은 것이 원인이다.

3.4는 PR #122가 bounds 검증 제거라는 코드 변경에 집중하면서, 그 변경이 만드는 문서·주석 파급 범위를 전부 추적하지 않은 것이 원인이다. 특히 `addressSummary` 예시 변경은 이번 PR의 목적과 무관해, 검토 없이 우발적으로 들어간 것으로 보인다(추정 — 원 작성자 의도를 직접 확인하지는 못했다).

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `deploy/nginx/masiton.click.conf`에서 `/_verification/*` 내부 위치와 `/api/verification/sessions` 위치의 `proxy_set_header` 지시어 대조 | 세 내부 위치는 `Authorization ""`가 있고 클라이언트 대상 위치만 없음 | 지적이 사실. 일관성을 위해 추가 |
| `RestaurantMapQueryAdapter.java`에서 `addressSummary` 컬럼 매핑 확인 | `r.road_address AS address_summary` — 코드는 전체 주소를 그대로 select | 문서만 잘못 바뀐 드리프트로 판단, 문서를 코드에 맞춰 원복 |
| 프론트엔드 `map-points-response.test.ts` 픽스처 확인 | 이미 전체 주소 형태("...월드컵로 1")를 기대값으로 사용 | 원복 방향이 실제 동작과 일치함을 재확인 |
| `gh api repos/team-youngkk/masit-on/rulesets/19533356`·`19533400`으로 `allowed_merge_methods` 재조회 | `develop`=`["squash"]`, `main`=`["merge"]` — 현재 문서와 일치 | 실제 ruleset은 이미 올바르게 설정됨(이 부분은 "이미 해결") |
| `docs/07-adr/` 전체에서 병합 방식 관련 ADR 검색 | 없음. `implementation-conventions.md`의 자기 인용과 PR #121의 "2026-08-03 승인" 문구만 존재 | 정책 전환의 근거 문서화는 이 PR 범위를 넘는 팀 결정 사항으로 분리 |
| `SameSite=Strict`를 `Lax`로 바꾸는 코드·테스트 변경을 실제로 적용해봄 | 컴파일·테스트는 통과했으나 값이 [ADR-DEPLOY-003](../07-adr/platform/deploy-003-validation-cookie-session.md)·[API 계약](../05-specs/api/common/validation-access-contract.md)에 명시된 Accepted 계약값임을 뒤늦게 확인 | CLAUDE.md 7절(계약 변경은 소유자 사전 합의)에 따라 되돌리고 "결정 필요"로 분류 |
| Redis 다중 키 원자성 — Member 도메인의 `RECORD_LOGIN_FAILURE` Lua 스크립트 패턴 확인 | 동일한 `for _, key in ipairs(KEYS)` 구조로 이미 검증된 패턴 | 새 스크립트를 만들지 않고 그대로 재사용 |

## 6. 최종 해결

- 변경 내용:
  - `deploy/scripts/nginx-install.sh`: Basic Auth 제거 전에 `curl`로 `/internal/verification/session`이 401을 응답하는지 확인하는 사전 검사를 추가. 실패 시 기존 Basic Auth 구성을 그대로 두고 중단(3.1).
  - `MemberAuthenticationController.verifyEmail`/`MemberAuthenticationService.verifyEmail`: 컨트롤러의 사전 null 검사를 제거하고 서비스로 `token`을 그대로 전달. 서비스가 rate limiter를 통과시킨 뒤 `null` 여부를 확인해 같은 `MISSING_REQUIRED_FIELD`를 던지도록 순서를 바꿈(3.2).
  - `VerificationSessionServiceTest`·`VerificationSessionControllerTest`: 429(차단) 분기, 실제 세션 ID를 사용한 revoke 성공·Redis 장애 경로 테스트 추가(3.3).
  - 신규 `RedisVerificationAccessStoreIntegrationTest`: Testcontainers 기반으로 해시 저장, 로그인/출처별 차단 임계치, 실패 기록 정리를 실제 Redis로 검증(3.3).
  - `RedisVerificationAccessStore`: 로그인 키·출처 키의 `INCR`+`EXPIRE`를 Member 도메인과 동일한 다중 키 Lua 스크립트 하나로 통합해 원자적으로 처리.
  - `deploy/nginx/masiton.click.conf`: `/api/verification/sessions` 위치에 `proxy_set_header Authorization "";` 추가.
  - `RestaurantMapPointsQueryService.java`의 Javadoc을 실제 검증 순서로 재작성(3.4).
  - `docs/05-specs/api/discovery/map-discovery-api.md`의 `addressSummary` 설명·예시를 실제 코드 반환값(전체 도로명주소)으로 원복(3.4).
- 선택 이유: 모든 변경이 리뷰가 지적한 실제 코드·문서 상태와의 불일치를 최소 범위로 맞추는 수정이다. `SameSite`와 병합 방식 ADR 건은 계약·거버넌스 문서를 건드리므로 소유자 결정 없이 임의로 바꾸지 않았다.
- 변경 파일: `deploy/scripts/nginx-install.sh`, `deploy/nginx/masiton.click.conf`, `src/main/java/com/masiton/member/presentation/MemberAuthenticationController.java`, `src/main/java/com/masiton/member/application/MemberAuthenticationService.java`, `src/main/java/com/masiton/security/infrastructure/redis/RedisVerificationAccessStore.java`, `src/main/java/com/masiton/restaurant/application/query/RestaurantMapPointsQueryService.java`, `docs/05-specs/api/discovery/map-discovery-api.md`, `src/test/java/com/masiton/member/application/MemberAuthenticationServiceTest.java`, `src/test/java/com/masiton/member/presentation/MemberAuthenticationControllerTest.java`, `src/test/java/com/masiton/security/application/VerificationSessionServiceTest.java`, `src/test/java/com/masiton/security/presentation/VerificationSessionControllerTest.java`, 신규 `src/test/java/com/masiton/security/infrastructure/redis/RedisVerificationAccessStoreIntegrationTest.java`
- 고려한 대안:
  - 3.2에서 컨트롤러가 직접 rate limiter를 호출하는 대안도 검토했으나, Presentation이 Infrastructure(rate limiter)를 직접 다루게 돼 계층 경계를 어기므로 기각하고 서비스 내부에서 순서만 바꾸는 방식을 채택했다.
  - `SameSite=Lax` 변경은 5절에서 시도했다가, 계약 문서 근거를 확인한 뒤 되돌렸다(위 참고).

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `bash -n deploy/scripts/nginx-install.sh` | 통과 | 구문 오류 없음 |
| `./gradlew compileJava compileTestJava` | 통과 | 전체 컴파일 오류 없음 |
| `./gradlew test --tests VerificationSessionServiceTest --tests VerificationSessionControllerTest --tests RedisVerificationAccessStoreIntegrationTest --tests MemberAuthenticationServiceTest --tests MemberAuthenticationControllerTest` | 통과 | 신규·수정 테스트 포함 전체 통과(Testcontainers 실제 Redis 포함) |
| `./gradlew clean build` | 통과 | `BUILD SUCCESSFUL in 7m 3s`, 9 actionable tasks, 실패한 테스트 0건(Testcontainers PostgreSQL·Redis 포함 전체 스위트) |
| `grep`으로 `road_address AS address_summary` SQL 확인 | 통과 | 코드가 전체 주소를 그대로 반환함을 확인 |
| `gh api .../rulesets/19533356`·`19533400` | 통과 | 실제 ruleset이 문서와 일치 |

## 8. 재발 방지 및 다음 확인

- 재발 방지:
  - 운영 절차 문서(예: E1-T13 배포 전환)가 "먼저 A, 그 다음 B" 형태의 순서를 요구하면, 가능하면 그 순서를 스크립트 자체가 헬스체크로 강제한다. 운영자의 수동 순서 준수에만 의존하지 않는다.
  - rate limiter가 있는 엔드포인트를 구현할 때는 "모든 형태의 제출 시도가 제한을 소모하는가"를 스키마 검증 순서와 별도로 확인한다.
  - 같은 패턴(다중 키 rate limiting, Redis 기반 저장소)을 다른 도메인에 새로 구현할 때는 기존 도메인의 테스트 구성(Testcontainers 통합 테스트 포함)을 먼저 확인하고 동일하게 맞춘다.
- 다음 확인:
  - `SameSite=Lax` 전환 여부는 OPS-VALIDATION 소유자(이우람)의 결정이 필요하다. 결정되면 [ADR-DEPLOY-003](../07-adr/platform/deploy-003-validation-cookie-session.md)·[검증 참여자 API 계약](../05-specs/api/common/validation-access-contract.md)과 코드·테스트를 같은 PR에서 함께 바꾼다.
  - `develop`↔`main` 병합 방식 전환에 대한 별도 ADR 작성 여부는 팀 결정이 필요하다. 작성하지 않기로 하면 이 문서의 링크만 남기고 종료한다.
  - `deploy/nginx/masiton.click.conf` 변경은 로컬에 `nginx` 바이너리가 없어 `nginx -t`로 직접 검증하지 못했다. 배포 전 운영 환경 또는 CI의 Nginx 검증 단계에서 확인이 필요하다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 이메일 인증 제출 제한 우회 가능 여부 | 가능(필드 누락 시 무제한) | 코드 리뷰·단위 테스트로 확인 | 불가능(모든 제출이 제한 소모) | 개선 — 배포 후 실제 트래픽에서 429 발생 빈도 변화는 별도 모니터링 대상 | OPS-VALIDATION 담당(이우람), 다음 운영 모니터링 주기에 확인 |
| Basic Auth → 세션 게이트 전환 시 자동 안전장치 | 없음(운영자 수동 순서 의존) | 스크립트 코드 리뷰 | 있음(백엔드 헬스체크 실패 시 자동 중단) | 개선 | 실제 배포 시점에 담당자가 로그로 헬스체크 통과 여부 확인 |

그 외 항목(Redis 원자성, Nginx 헤더 일관성, 문서 정확성)은 수치로 비교할 성능·오류율 지표가 아니라 정확성 수정이므로 `해당 없음`이다.

## 10. 남은 사항

- `SameSite=Lax` 전환은 결정 필요 — OPS-VALIDATION 소유자(이우람) 확인 대기.
- 병합 방식 전환의 ADR 작성 여부는 결정 필요 — 팀 논의 대기.
- `deploy/nginx/masiton.click.conf`의 `nginx -t` 검증은 로컬에서 수행하지 못했다.
