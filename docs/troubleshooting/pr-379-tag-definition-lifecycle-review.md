---
related_documents:
  - ../08-planning/admin-tag-definition-lifecycle.md
  - ../05-specs/api/common/pagination-contract.md
  - pr-273-frontend-ui-sync-review.md
  - pr-368-tag-definition-normalization-review.md
  - pr-280-pagination-policy-documentation-review.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #379 리뷰 트러블슈팅: 태그 관리 세션·용어 잠금·페이지 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#379 관리자 태그 정의 생명주기 관리](https://github.com/team-youngkk/masit-on/pull/379) |
| 작성자 | 양성훈 (`@tjdgns0618`) |
| 처리 일자 | 2026-09-08~2026-09-09 |
| 범위 | 관리자 태그 관리의 세션 이벤트, 용어 교환 동시성, 큰 페이지 OFFSET, 로그인 복귀 경로 리뷰 4건과 후속 CI 보안 감사 |
| 주 문제 유형 | 애플리케이션·데이터베이스 |
| 기존 기록 | [PR #273 화면 상태 동기화](pr-273-frontend-ui-sync-review.md), [PR #368 태그 정규화·원자성](pr-368-tag-definition-normalization-review.md), [PR #280 페이지 계약](pr-280-pagination-policy-documentation-review.md)을 확인했다. 계정별 상태 격리, 정규화 용어의 DB 최종 판정, 범위 밖 페이지의 빈 목록 원칙을 이번 수정에 적용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [토큰 갱신을 계정 전환으로 오인](https://github.com/team-youngkk/masit-on/pull/379#discussion_r3957010126) | 같은 계정의 Token 갱신 뒤에도 관리 화면 요청과 갱신을 계속 허용 | 애플리케이션 | 수정 필요 | 세션 이벤트 리스너를 제거하고 `accountId`가 바뀌어 컴포넌트가 교체되거나 unmount될 때만 요청·캐시를 폐기 | TypeScript 검사, 프론트 340건, 프로덕션 빌드 통과 |
| [서로 다른 정의의 용어 교환 교착](https://github.com/team-youngkk/masit-on/pull/379#discussion_r3957010135) | A→B와 B→A 동시 수정의 advisory lock 순환 대기 방지 | 데이터베이스 | 수정 필요 | 현재 용어와 신규 용어의 합집합을 정렬해 advisory lock한 뒤 용어를 교체 | 동시 용어 교환이 교착이나 500 없이 두 건 모두 `TAG_TERM_ALREADY_EXISTS`로 수렴 |
| [큰 페이지 OFFSET 오버플로](https://github.com/team-youngkk/masit-on/pull/379#discussion_r3957010143) | 목록과 이력의 OFFSET을 `long`으로 계산 | 애플리케이션 | 수정 필요 | 서비스 계산과 Store Port·JDBC 인자의 offset을 `long`으로 변경 | `page=50000000&size=50` 목록·이력이 200 빈 `items` 반환 |
| [태그 관리 로그인 복귀 경로 누락](https://github.com/team-youngkk/masit-on/pull/379#discussion_r3957665716) | 비로그인 직접 접근 뒤 원래 태그 관리 화면으로 복귀 | 애플리케이션 | 수정 필요 | 관리자 복귀 경로 allowlist에 `/admin/tag-definitions`를 추가하고 회귀 테스트로 고정 | `safeAdminReturnTo('/admin/tag-definitions')`가 동일 경로 반환, 프론트 340건 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 용어 교환 경쟁에서는 PostgreSQL deadlock이 발생하면 500으로 전파될 수 있었고, 큰 페이지에서는 음수 OFFSET 오류가 발생할 수 있었다. Token 갱신 문제는 오류 응답 없이 이후 성공 콜백과 캐시 갱신을 무시했다.
- 발생 환경: Next.js 16.3.4, TanStack Query 관리자 화면, Java 21·Spring JDBC·PostgreSQL 17, `feature/t-365-admin-tag-lifecycle`.
- 재현 조건: 같은 관리자에서 Access Token만 갱신하거나, 서로 다른 두 태그가 현재 용어를 맞교환하도록 동시에 수정하거나, `page=50000000&size=50`을 요청하거나, 비로그인 상태에서 `/admin/tag-definitions`에 직접 접근한다.
- 실제 결과: Token 갱신 이벤트가 `active=false`를 남겼고, 용어 수정은 새 용어만 잠가 교차 잠금 순서가 달랐으며, `(page - 1) * size`가 `int` 범위에서 넘쳤다. 새 관리자 경로는 복귀 allowlist에 없어 로그인 뒤 `/admin`으로 대체됐다.
- 기대 결과: 동일 계정 Token 갱신은 화면 작업을 유지하고, 용어 충돌은 교착 없이 409로 수렴하며, 1 이상의 범위 밖 페이지는 200 빈 목록을 반환하고, 로그인 성공 뒤 요청한 태그 관리 화면으로 돌아와야 한다.
- 영향 범위: 관리자 태그 내용·상태 저장 후 화면 갱신, 태그 정의와 감사의 동시 수정 원자성, 관리 목록·감사 이력 조회다.

## 4. 근본 원인

`MEMBER_SESSION_CHANGED_EVENT`는 계정 전환뿐 아니라 같은 계정의 Access Token 저장·갱신에도 발생한다. 화면 effect는 `accountId`에만 의존하므로 이벤트에서 `active=false`로 바꾼 뒤 같은 계정이면 effect가 다시 실행되지 않았다.

태그 내용 수정은 신규 정규화 용어만 advisory lock했다. 두 정의가 서로의 현재 용어를 신규 값으로 선택하면 각 트랜잭션이 다른 잠금을 먼저 소유한 상태에서 상대 용어의 삭제·삽입을 기다릴 수 있었다. 현재·신규 용어 합집합을 동일한 정렬 순서로 잠그지 않은 것이 원인이다.

페이지 OFFSET은 반환 형식만 `long`과 호환됐지만 산술식의 두 피연산자가 모두 `int`였다. 캐스팅 전에 오버플로가 발생해 정상적인 큰 양수 페이지가 음수 OFFSET으로 바뀔 수 있었다.

태그 관리 Route를 추가하면서 `ADMIN_RETURN_TO_PATHS`의 정확한 경로 allowlist를 함께 갱신하지 않았다. `safeAdminReturnTo`는 보안을 위해 미등록 관리자 경로를 거부하므로 정상 경로도 `/admin`으로 대체됐다.

2026-09-09에 갱신된 npm 보안 감사 데이터는 고정 기준선 Next.js 16.2.11과 `sharp` 0.35.0을 각각 GHSA-p293-qw3h-jr36·GHSA-2xp9-vwfh-vxw4, GHSA-rgj7-g3m4-5g8c의 영향 버전으로 판정했다. 코드 리뷰 수정과 무관하게 `npm audit --omit=dev --audit-level=high`가 종료 코드 1을 반환해 프론트 CI가 차단됐다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 세션 이벤트 발생 지점과 `accountId` effect 의존성 대조 | Token 갱신에도 이벤트가 발생하고 같은 계정에서는 effect 재실행이 없음 | 이벤트 리스너를 제거하고 컴포넌트 생명주기로 폐기 범위를 한정 |
| PR #273의 화면 상태 기록 확인 | 사용자 상태 변화와 실제 화면 scope를 구분해야 함 | 계정 식별자인 `accountId`를 격리 기준으로 재사용 |
| 두 정의의 현재·신규 용어 잠금 집합 대조 | 기존 구현은 각 요청의 신규 용어만 잠금 | 두 집합의 합집합을 정렬해 동일 잠금 순서를 강제 |
| 동시 용어 교환 통합 테스트 | 수정 후 두 요청이 교착 없이 계약된 409 코드로 종료 | 회귀 테스트로 유지 |
| `page=50000000`, `size=50` 산술 대조 | `int` 계산은 `-1794967346`, `long` 계산은 `2499999950` | 서비스와 저장소 경계를 `long`으로 통일 |
| `safeAdminReturnTo`와 신규 관리자 Route 대조 | `/admin/tag-definitions`가 allowlist에 없어 `null` 반환 | 정확한 신규 경로를 등록하고 기존 외부·이중 인코딩 거부 테스트를 유지 |
| 실패한 프론트 CI의 `npm audit` 상세 확인 | Next.js 16.2.11과 `sharp` 0.35.0에서 high 이상 취약점 검출 | 수정 버전 Next.js 16.3.4와 `sharp` 0.35.4로 기준선 갱신 |
| Next.js 16.3.4에서 내장 TypeScript 검사 재확인 | TypeScript 7.0.2 검사와 프로덕션 빌드가 정상 완료 | `typescript.ignoreBuildErrors` 우회 제거 |

## 6. 최종 해결

- 변경 내용: 관리자 태그 화면은 `accountId`가 바뀌거나 unmount될 때만 요청과 계정 scope 캐시를 폐기한다. 내용 수정은 현재·신규 정규화 용어 합집합을 정렬해 잠근다. 목록과 이력은 OFFSET을 `long`으로 계산하고 JDBC까지 전달한다. 태그 관리 경로는 관리자 로그인 복귀 allowlist에 등록한다. CI 보안 감사에 맞춰 Next.js를 16.3.4, `sharp`를 0.35.4로 갱신하고 더 이상 필요 없는 내장 TypeScript 검사 우회를 제거했다.
- 선택 이유: API·DB 계약을 바꾸지 않고 각 결함의 실제 경계인 계정 식별자, 정규화 용어 집합, 페이지 산술 타입만 바로잡는 최소 변경이다.
- 변경 파일:
  - `frontend/components/admin/AdminTagDefinitions.tsx`
  - `frontend/lib/member/auth-navigation.ts`
  - `frontend/lib/member/auth-navigation.test.ts`
  - `src/main/java/com/masiton/ai/application/TagDefinitionService.java`
  - `src/main/java/com/masiton/ai/application/port/out/TagDefinitionStore.java`
  - `src/main/java/com/masiton/ai/infrastructure/persistence/JdbcTagDefinitionStore.java`
  - `src/test/java/com/masiton/ai/TagDefinitionIntegrationTest.java`
  - `frontend/package.json`, `frontend/package-lock.json`, `frontend/next.config.ts`
  - `docs/06-architecture/technology-policy.md`, `docs/07-adr/platform/web-001-frontend-platform.md`

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew test --tests com.masiton.ai.TagDefinitionIntegrationTest --tests com.masiton.ai.application.TagDefinitionServiceTest` | 통과, 16건 | 용어 교환 동시성, 큰 페이지, 기존 수정·감사·검증 회귀 |
| `npm run typecheck` | 통과 | 관리자 화면 TypeScript 오류 없음 |
| `npm test` | 통과, 340건 | 기존 프론트 상태·API 조정 회귀 없음 |
| `npm run build` | 통과 | `/admin/tag-definitions` 포함 프로덕션 빌드 성공 |
| `npm audit --omit=dev --audit-level=high` | 통과, 취약점 0건 | Next.js·sharp 보안 감사 차단 해소 |
| `npm ls next typescript sharp postcss nanoid` | 통과 | 고정 버전 Next.js 16.3.4, TypeScript 7.0.2, sharp 0.35.4 확인 |
| `git diff --check` | 통과 | 코드·문서 패치 공백 오류 없음 |
| PR #379 원격 CI | 통과 | 백엔드 빌드·테스트, 프론트 빌드·타입 검사·보안 감사, Terraform 계약, RSA 개인키 검사 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 서로 다른 정의의 용어 교환과 매우 큰 페이지를 PostgreSQL 통합 테스트에 추가했다.
- 다음 확인: 원격 CI에서 전체 백엔드·프론트 회귀 통과를 확인했다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 큰 페이지 OFFSET | `page=50000000,size=50`에서 `-1794967346` | Java `int`·`long` 산술과 API 통합 테스트 | `2499999950`, 200 빈 목록 | 부호 오버플로 제거 | 양성훈, PR #379 리뷰 반영 시점 |
| 용어 교환 요청 완료 | 교착 시 DB 예외가 500으로 전파될 가능성 | 두 정의 동시 교환 통합 테스트 | 두 요청 모두 409 용어 충돌 | 교착·500 없이 계약 코드로 수렴 | 양성훈, PR #379 리뷰 반영 시점 |
| Token 갱신 뒤 화면 활성 상태 | 이벤트 뒤 `active=false`가 같은 계정에서 유지 | 이벤트 리스너와 effect 의존성 정적 대조 | Token 이벤트가 폐기 함수를 호출하지 않음 | 계정 scope가 유지되는 동안 요청·갱신 허용 | 양성훈, PR #379 리뷰 반영 시점 |
| 태그 관리 로그인 복귀 | 미등록 allowlist로 `null`, 로그인 뒤 `/admin` | `safeAdminReturnTo` 단위 테스트 | `/admin/tag-definitions` 반환 | 직접 접근 목적지 보존 | 양성훈, PR #379 리뷰 반영 시점 |
| 프로덕션 의존성 감사 | high 2건으로 CI 실패 | `npm audit --omit=dev --audit-level=high` | 0건 | 보안 품질 게이트 복구 | 양성훈, PR #379 후속 CI 반영 시점 |

## 10. 남은 사항

- 없음. 네 건의 리뷰 지적에 대한 수정과 원격 검증을 완료했다.
