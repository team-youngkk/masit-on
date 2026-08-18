---
related_documents:
  - ../05-specs/data/migration-plan.md
  - ../05-specs/api/account/member-authentication-api.md
  - ../07-adr/security/auth-007-unified-account-rbac-session.md
  - pr-129-deploy-cutover-and-rate-limit-review.md
  - pr-192-flyway-model-contract-review.md
  - pr-235-unified-auth-contract-review.md
---

# PR #238 리뷰 트러블슈팅: 통합 인증 구현과 CI 회귀

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#238](https://github.com/team-youngkk/masit-on/pull/238) |
| 작성자 | jinyp01 |
| 처리 일자 | 2026-08-18 |
| 범위 | 로그인 실패 제한, JWT 보안 픽스처, 계층 의존, Redis 세션 테스트와 계정 전환 migration 검토 |
| 주 문제 유형 | 애플리케이션 / 데이터베이스 |
| 기존 기록 | [PR #129](pr-129-deploy-cutover-and-rate-limit-review.md)의 요청 선행 제한 원칙, [PR #192](pr-192-flyway-model-contract-review.md)의 Flyway 적용 이력 증거, [PR #235](pr-235-unified-auth-contract-review.md)의 통합 계정 전환 결정을 재사용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [member_account.role migration](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803788129) | role을 조회하는 애플리케이션과 같은 배포에 스키마 전환을 제공한다. | 데이터베이스 | 결정 필요 | 임의 V6 추가를 보류했다. 현재 공유·운영 적용 이력, 활성 관리자 `email_verified_at` 출처, 승인된 매핑 입력이 필요하다. | [migration-plan 13.1](../05-specs/data/migration-plan.md#131-확장복사-단계)은 현재 이력 확인과 공동 승인 입력을 선행 조건으로 둔다. |
| [로그인 실패 제한](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803788131) | 성공 로그인은 실패 한도를 소모하지 않게 한다. | 애플리케이션 | 수정 필요 | 자격 증명 확인 전에는 차단 여부만 조회하고 실패가 확정된 경우에만 계정·계정/출처 카운터를 증가시킨다. 출처 카운터는 요청 선행 필터가 한 번만 증가시킨다. | 서비스·필터·아키텍처 테스트 31건 통과. Redis 경계 테스트를 보강했다. |
| [JWT audience 픽스처](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803799416) | audience 성공 토큰에 필수 sid를 포함한다. | 애플리케이션 | 수정 필요 | 성공 검증은 모든 필수 claim을 가진 토큰을 사용하고 sid 누락 거부 테스트는 별도로 유지했다. | `compileTestJava` 통과. Docker 부재로 MockMvc 통합 실행은 CI에서 재확인한다. |
| [role migration 후속 1](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803806624) | role과 관리자 전환 migration을 추가한다. | 데이터베이스 | 결정 필요 | 최초 migration 스레드와 같은 선행 결정으로 묶어 보류했다. | 최신 CI의 남은 27건이 role 컬럼 부재에 수렴했다. |
| [로그인 실패 제한 후속 1](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803806633) | 성공을 제외하고 실패만 기록한다. | 애플리케이션 | 이미 해결 | `f84728f`에서 실패 확정 뒤 기록하도록 변경했다. | 서비스 회귀 19건 통과. |
| [로그아웃 401 후속 1](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803806638) | 401 뒤 refresh와 DELETE를 한 번 재시도한다. | 애플리케이션 | 수정 필요 | stale token 제거, single-flight refresh, DELETE 1회 재시도, 최종 204 검증을 추가했다. | 프론트 테스트 266건·typecheck 통과. |
| [role migration 후속 2](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803859549) | V6과 관리자 전환을 구현한다. | 데이터베이스 | 결정 필요 | 현재 이력과 승인된 전환 입력이 없어 임의 V6를 추가하지 않았다. | migration plan 13.1 선행 조건. |
| [로그인 실패 제한 후속 2](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803859554) | 성공 로그인 카운터 소비를 제거한다. | 애플리케이션 | 이미 해결 | 실패 기반 집계로 전환하고 사용하지 않는 선행 획득 API를 제거했다. | 성공 로그인 비집계 테스트 추가. |
| [로그아웃 401 후속 2](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803859559) | 만료 access token 로그아웃을 재시도한다. | 애플리케이션 | 수정 필요 | 같은 수정으로 refresh 후 새 Bearer DELETE를 한 번 수행한다. | 전용 프론트 회귀 테스트 통과. |
| [legacy 관리자 인증 stack](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803859562) | 제거된 Controller 전용 미사용 stack을 정리한다. | 애플리케이션 | 수정 필요 | 통합 로그인에 사용되지 않는 service, port, adapter, entity와 전용 테스트를 삭제했다. 관리자 write principal은 유지했다. | `compileTestJava` 통과. |
| [복원 직후 401](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803859567) | 복원 token이 401이면 token과 세션 상태를 정리한다. | 애플리케이션 | 수정 필요 | stale token 제거와 session-changed event를 복원 직후 401 분기에도 적용했다. | 전용 프론트 회귀 테스트 통과. |
| [로그인 필터 계층 의존](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803859570) | security가 member port·구현에 직접 의존하지 않게 한다. | 애플리케이션 | 수정 필요 | `common.security.LoginSourceRateLimiter` port로 의존을 역전하고 주소 해석도 common port만 참조한다. | ArchitectureTest 10건 통과. |
| [Origin 설정 단일화](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803859573) | 미사용 admin Origin 설정을 제거한다. | 애플리케이션 | 수정 필요 | `SecurityProperties.publicBaseUrl`과 검증·판정 API, 중복 application 바인딩을 제거했다. | `compileJava` 통과. |
| [로그인 필터 경계 테스트](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803859580) | 정상 통과와 Redis 장애 503을 검증한다. | 애플리케이션 | 수정 필요 | filter-chain 진행과 fail-closed 분기를 추가했다. | 필터 테스트 4건 통과. |
| [trusted proxy 변수명](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803859584) | 운영 변수를 `AUTH_LOGIN_*` 계약에 맞춘다. | 배포 | 수정 필요 | application·app-run·app-deploy를 공통 변수명으로 통일했다. | 배포 계약 테스트 통과. |
| [실패 기록 원자성](https://github.com/team-youngkk/masit-on/pull/238#discussion_r3803986253) | 확인과 증가를 하나의 Lua 연산으로 묶는다. | 애플리케이션 | 수정 필요 | 실패 기록 Lua가 5/10 한도를 확인하고 허용된 경우만 원자 증가해 결과를 반환하도록 변경했다. | 동시 5/10 경계 Redis 테스트 추가. |

## 3. 문제 현상과 발생 조건

- 오류 메시지: CI 백엔드 작업에서 1,308개 중 38개 테스트 실패
- 발생 환경: PR #238 GitHub Actions, PostgreSQL·Redis Testcontainers 포함 전체 백엔드 테스트
- 재현 조건: 성공 로그인을 반복하거나, 통합 claim 검증 뒤 과거 mock JWT·Redis 키를 사용하는 테스트를 실행하거나, V1~V5 빈 DB에서 role 조회 SQL을 실행한다.
- 실제 결과: 성공도 계정 실패 제한을 소모했고, 보안 테스트는 세션 검사 전에 거부됐으며, Redis 테스트는 이전 keyspace를 조회했다. PostgreSQL 테스트는 존재하지 않는 `member_account.role` 조회에서 실패했다.
- 기대 결과: 실패한 자격 증명만 계정 기반 한도에 집계되고, 테스트는 현재 JWT·Redis 계약을 사용하며, 애플리케이션 사용 전 승인된 계정 전환 migration이 적용돼야 한다.
- 영향 범위: 정상 로그인 가용성, 백엔드 CI, 통합 계정 배포 안전성

## 4. 근본 원인

로그인 서비스가 형식이 유효한 모든 요청에 대해 카운터를 원자 획득하도록 구현되어 성공도 실패 한도에 포함됐고, 이를 실패 뒤 두 Redis 호출로 나눈 첫 수정은 동시 한도 초과 여지를 만들었다. 통합 JWT와 Redis 세션 keyspace를 변경하면서 일부 통합 테스트 픽스처가 이전 claim·키를 유지했고, 로그인 출처 필터가 회원 도메인의 port·구현을 직접 참조해 계층 규칙을 위반했다. 프론트는 로그아웃과 복원 직후 401에서 stale token·Refresh 쿠키 정리를 끝내지 않았다. 별도로 애플리케이션은 `member_account.role`을 사용하지만, 계정 전환 migration은 운영 이력과 승인 입력이 없는 상태에서 구현되지 않아 PostgreSQL 테스트와 실제 배포 모두 선행 조건이 충족되지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| CI 실패 로그 38건을 테스트별로 분류 | role 컬럼 부재, JWT 픽스처, 이전 Redis 키, 계층 의존으로 수렴 | 독립적인 코드·테스트 회귀를 먼저 수정 |
| 첫 수정 push 뒤 CI 재실행 | 공통 `ClientAddressResolver` 구현이 여럿이라 로그인 필터 생성자가 모호해졌고 Context 연쇄 실패 249건 발생 | `memberClientAddressResolver` qualifier를 명시하고 재실행 |
| 로그인 서비스와 Redis Lua 호출 순서 확인 | 성공 전에 계정 카운터를 증가시킴 | 실패 확정 뒤 기록하도록 변경 |
| `ArchitectureTest` 로컬 재현 | security infrastructure가 member infrastructure에 의존 | common의 `ClientAddressResolver` port에 의존하도록 변경 |
| 운영 Flyway 기록 확인 | 2026-08-14 기록은 V1~V3이지만 현재 시점의 공유·운영 이력은 미확인 | 신규 버전 배정 전 데이터 소유자 확인 필요 |
| Docker daemon 확인 | 로컬 Docker Desktop 미실행 | PostgreSQL·Redis 통합 검증은 CI 재실행 필요 |

## 6. 최종 해결

- 변경 내용: 로그인 실패 시점의 원자 5/10 집계, 공통 로그인 제한·클라이언트 주소 port, 완전한 mock JWT claim, 현재 Redis 세션 키, 프론트 401 정리, Origin·trusted-proxy 설정 단일화, 미사용 관리자 인증 stack 삭제를 반영했다.
- 선택 이유: 공개 계약의 실패 기반 계정 제한과 계층 규칙을 최소 변경으로 복원하면서 source-only 선행 제한은 그대로 유지하기 위해서다.
- 변경 파일: `MemberAuthenticationService`, `MemberRateLimitStore`, `RedisMemberRateLimitStore`, `LoginSourceRateLimitFilter`와 관련 테스트
- 고려한 대안: 현재 증거 없이 V6와 관리자 데이터를 임의 생성하는 방안은 적용 이력 충돌과 잘못된 인증 시각·관리자 매핑 위험 때문에 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat compileTestJava test --tests com.masiton.member.application.MemberAuthenticationServiceTest --tests com.masiton.security.infrastructure.web.LoginSourceRateLimitFilterTest --tests com.masiton.architecture.ArchitectureTest --no-daemon --console=plain` | 통과 | 서비스 19건, 필터 2건, 아키텍처 10건 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| PR #238 CI run `32135495164` | 실패 | 프론트엔드·Terraform은 통과. 공통 resolver 주입 모호성으로 백엔드 Context가 연쇄 실패해 qualifier를 후속 보완했다. |
| PR #238 CI run `32136091504` | 실패 | 프론트엔드·Terraform 통과. `SecurityConfigurationApiTest` 25건, `RedisRefreshTokenStoreIntegrationTest` 19건, `ArchitectureTest` 10건 통과. 백엔드 1,309건 중 남은 27건은 role 컬럼 부재로 인한 SQL 오류 또는 그 503 전파다. |
| PR #238 CI run `32137718331` | 실패 | 원자 로그인 제한 Redis 통합 16건 통과. Origin 설정 제거 뒤 운영 configtree 테스트 한 곳이 삭제된 속성을 계속 기대해 현재 member 경로로 수정했다. 나머지 실패는 role migration 부재다. |
| PostgreSQL·Redis·MockMvc 통합 테스트 | 미실행 | 로컬 Docker daemon 미가동. push 뒤 CI에서 재검증한다. |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 성공 로그인 비집계와 실패 한도 경계, 현재 Redis keyspace, 필수 JWT claim을 회귀 테스트에 명시했다.
- 다음 확인: 데이터 소유자와 인증 소유자가 현재 Flyway 이력, `MIGRATE_ACTIVE`의 `email_verified_at` 값 출처, 승인 매핑 입력과 cutover 순서를 확정한 뒤 migration 스레드를 계속 처리한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 백엔드 CI 실패 수 | 최초 38건, 첫 수정 run 249건 | PR #238 CI run별 비교 | 27건 | 코드·픽스처 회귀는 해소됐고 남은 실패는 role migration 결정에 수렴 | 데이터·인증 소유자, PR #238 |
| 성공 로그인 뒤 계정 실패 카운터 | 로그인마다 1 증가 | Redis 통합 테스트 10회 | 0 증가 기대 | CI 재실행에서 확인 | 인증 소유자, PR #238 |

## 10. 남은 사항

- `member_account.role`과 관리자 FK 전환 migration은 데이터 계약상 필요한 입력과 최신 적용 이력이 없어 결정 필요 상태다.
- 로컬 Docker가 없어 Testcontainers 기반 전체 회귀는 CI에서 확인해야 한다.
