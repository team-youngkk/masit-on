---
status: Ready
plan_date: 2026-07-29
owners:
  - 김인안
  - 박진영
  - 양성훈
  - 이우람
related_documents:
  - mvp-2day-implementation-plan.md
  - ../00-overview/scope.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../01-requirements/non-functional-requirements.md
  - ../02-analysis/first-expansion-workstreams.md
  - ../04-product/traceability.md
  - ../05-specs/api-traceability.md
  - ../05-specs/data/data-traceability.md
  - ../07-adr/adr-traceability.md
  - ../07-adr/security/auth-002-member-jwt-refresh-token.md
  - ../07-adr/integration/map-001-map-bounds-search.md
  - first-expansion-baseline-review.md
  - expansion-1-task-breakdown.md
---

# 맛잇온 1차 확장 구현 계획

## 1. 목표와 범위

이 계획은 1차 확장으로 회원 계정·인증, 찜, 최근 본 맛집, 지도 기반 탐색, 유튜버 상세를 구현하는 순서·책임·검증 기준이다. MVP 구현 계획의 Task 형식과 통합 원칙을 따르며, 기능·API·데이터·ADR 원문을 변경하지 않는다.

- 핵심 사용자 경로: `공통 계약 확정 → 회원 스키마·일반 사용자 인증 → 찜·최근 본 맛집 → 회원 기반 사용자 여정 통합`
- 병렬 경로: `맛집 좌표 정책 → 지도 탐색`, `Creator 계약 확장 → 유튜버 상세`
- 범위 포함: 확정된 `FR-MEMBER-*`, `FR-AUTH-*`, `FR-FAVORITE-*`, `FR-RECENT-*`, `FR-MAP-*`, `FR-CREATOR-004~006`
- 범위 제외: 소셜 로그인, 현재 위치·반경·다각형 검색, PostGIS, 자동 좌표 동기화, 캐시·읽기 저장소·물리 CQRS, 일반 락 강화, 자동 재시도·Outbox, 사용자 조회 중 실시간 YouTube 호출

Task에 없는 미결정 기술을 구현으로 끌어오지 않는다. 캐시·읽기 저장소·물리적 CQRS, 일반 락 강화, 자동 재시도·Outbox, 자동 좌표 동기화, PostGIS, 현재 위치·반경 검색은 각각의 Conditional 또는 Post-MVP ADR을 활성화하기 전에는 이 계획의 범위가 아니다.

## 2. 현재 구현 기준선

기준선은 2026-07-29 `develop`의 `f70ed19`이며, 자세한 근거는 [1차 확장 구현 기준선 검토](first-expansion-baseline-review.md)를 따른다.

| 조사 대상 | 현재 상태 | 계획 반영 |
|---|---|---|
| MVP 사용자 흐름 | 요구사항 20개 중 17개 완료, 유튜버 선택 UI 관련 3개 부분 완료 | `FE-01`이 1차 확장 인수 전에 잔여 탐색 흐름을 닫는다. |
| 일반 사용자 인증 | 미구현; 관리자 JWT·Redis만 구현 | `FE-02`가 관리자와 분리된 identity·audience·쿠키·Redis namespace를 만든다. |
| 맛집 좌표 | Kakao 응답부터 DB·공개 API까지 미저장 | `FE-06`이 V4 좌표를 추가했고, 과거 bounds 계약은 `FE-10`에서 필터 기반 마커 조회로 교체한다. |
| Creator 상세 표시 정보 | 채널 ID·이름·URL·공개/생명주기만 보유 | `FE-08`이 V6와 관리자 확인 흐름을 확장한다. |
| 품질 게이트 | 백엔드 테스트 class loading 실패, CI workflow 없음; 프론트 typecheck·build 성공 | `FE-00`이 기능 Task보다 먼저 실제 테스트 실행과 CI 차단을 복구한다. |

## 3. Workstream과 책임

| Workstream | 최종 책임 / 기본 리뷰 | 구현 Task | 사용자 완료 경계 |
|---|---|---|---|
| [WS-05 사용자 계정·인증](../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증) | 김인안 / 이우람 | `FE-02`, `FE-03` | 가입부터 탈퇴까지 회원 세션·계정 상태가 관리자 인증과 분리된다. |
| [WS-06 개인 맛집 관리](../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) | 박진영 / 김인안 | `FE-04`, `FE-05` | 본인 찜·최근 기록의 소유권·보존·삭제가 일관된다. |
| [WS-07 지도 탐색](../02-analysis/first-expansion-workstreams.md#6-ws-07-지도-탐색) | 양성훈 / 박진영 | `FE-06`, `FE-07`, `FE-10` | 좌표·필터 기반 마커·대체 목록이 지도 이동과 독립적으로 동작한다. |
| [WS-08 유튜버 상세](../02-analysis/first-expansion-workstreams.md#7-ws-08-유튜버-상세) | 이우람 / 박진영 | `FE-08` | 저장된 공개 Creator·Visit·Restaurant·Video로 상세 세 화면을 제공한다. |
| [OPS-VALIDATION 공통 운영·배포](../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙) (역사) | 이우람 / 김인안 | `FE-12` | M2 제한 공개 진입 경계를 회원·관리자 인증과 분리해 운영했고, 정식 공개 전환에서 전용 경계를 제거한다. |
| 공통 품질·통합 | 이우람 / 김인안 | `FE-00`, `FE-01`, `FE-09` | 기준선 결함, MVP 잔여 흐름, 교차 인수·회귀를 닫는다. |

## 4. 공통 계약 확정

| 기준 | 상태 | 처리 원칙 |
|---|---|---|
| 회원 인증 정책·API·데이터·ADR | 확정 | `FE-02`~`FE-05`는 [ADR-AUTH-002](../07-adr/security/auth-002-member-jwt-refresh-token.md)와 회원 API·데이터 계약을 구현한다. |
| 지도 좌표·마커 조회·외부 SDK ADR | 확정 | `FE-06`~`FE-07`, `FE-10`은 [ADR-MAP-001](../07-adr/integration/map-001-map-bounds-search.md)의 뷰포트 비종속 경계를 따른다. |
| Creator 상세 API·데이터 계약 | 확정 | `FE-08`은 저장된 Creator·Visit·Restaurant·Video만 조회하며 사용자 조회 중 YouTube API를 호출하지 않는다. |
| 검증 참여자 제한 공개 계약 | M2 역사·정식 공개 전환으로 제거 | [ADR-DEPLOY-003](../07-adr/platform/deploy-003-validation-cookie-session.md)과 검증 참여자 API는 `FE-12`·`E1-T13`에서 구현·검증했으며, 현재는 [ADR-DEPLOY-006](../07-adr/platform/deploy-006-public-release-without-validation-gate.md)에 따라 전용 경계를 제거한다. |
| V2~V6 Flyway 순서 | 확정 | [마이그레이션 계획](../05-specs/data/migration-plan.md#9-1차-확장-전진-마이그레이션-순서)을 지키고 V1을 수정하지 않는다. 세부 테이블·열·제약·인덱스는 [테이블 정의](../05-specs/data/table-definitions.md#14-1차-확장-v4v6-데이터-계약), [제약조건](../05-specs/data/constraints.md), [인덱스 전략](../05-specs/data/index-strategy.md#5-1차-확장-인덱스)을 기준으로 구현한다. |
| MVP 잔여 사용자 흐름·품질 게이트 | 기준선 결함 | `FE-00`·`FE-01`에서 먼저 닫거나, 해당 미완료 상태를 1차 확장 완료로 오인하지 않는다. |

## 5. 구현 순서와 선행 관계

1. `FE-00`으로 실행 가능한 테스트·CI 품질 게이트를 복구한다.
2. `FE-01`으로 MVP 유튜버 선택·탐색 흐름을 닫는다. 이 작업은 확장 기능의 기능 선행은 아니지만 전체 완료 판정의 기준선이다.
3. `FE-02`가 V2와 회원 보안 경계를 제공한다.
4. `FE-03`과 `FE-04`가 회원 인증 기반 위에서 각각 계정 여정과 개인화 데이터 API를 구현한다.
5. `FE-05`가 실제 인증·개인화 API를 사용자 화면으로 통합한다.
6. 독립 경로에서 `FE-06 → FE-07`은 지도, `FE-08`은 유튜버 상세를 완성한다.
7. `FE-09`가 V1→V6, 보안·브라우저·교차 사용자 여정을 인수한다.

| 제공 계약·기반 | 선행 Task | 후속 Task | 차단 범위 |
|---|---|---|---|
| 실제 실행 가능한 테스트·CI | `FE-00` | `FE-01`~`FE-08`, `FE-09` | 품질 완료 판정 전체 |
| 회원 identity·Security matcher·V2 | `FE-02` | `FE-03`, `FE-04` | 회원 API·개인화 데이터 |
| 회원 인증 사용자 여정 | `FE-03` | `FE-05`, `FE-09` | 개인화 화면·탈퇴 교차 검증 |
| Favorite·Recent V3 API | `FE-04` | `FE-05`, `FE-09` | 개인화 화면·데이터 검증 |
| 좌표 V4·필터 마커 조회 | `FE-06`, `FE-10` | `FE-07`, `FE-09` 재실행 | 지도 화면·SDK·이동 시 결과 유지 검증 |
| Creator 표시·관계 V6 | `FE-08` | `FE-09` | 유튜버 상세 인수 |

## 6. 병렬 작업 범위

| 단계 | 김인안 | 박진영 | 양성훈 | 이우람 |
|---|---|---|---|---|
| 품질·기준선 | `FE-00` 리뷰 | V2~V6 검토 | `FE-01` 화면 | `FE-00` 주 담당 |
| 회원 기반 | `FE-02`→`FE-03` | `FE-04` 준비·구현 | 인증 화면 공통 상태 지원 | API·CI 리뷰 |
| 독립 공개 기능 | 보안 경계 리뷰 | `FE-06` 데이터, `FE-08` 리뷰 | `FE-06`→`FE-07` 지도 | `FE-08` 유튜버 상세 |
| 통합 | 인증·권한 회귀 | 데이터·마이그레이션 회귀 | 브라우저·접근성 회귀 | `FE-09` 조율 |

- `FE-06 → FE-07`과 `FE-08`은 `FE-02`를 기다리지 않고 공개 조회 계약으로 병렬 구현할 수 있다.
- `FE-04`는 `FE-02` 이후 시작하되, `FE-03` 완료 전에는 인증 주체 계약 Stub으로 내부 규칙만 검증할 수 있다.
- Flyway V2~V6의 파일 번호와 공통 파일 최종 병합은 박진영, 회원 인증 공통은 김인안, 공통 Layout은 양성훈, CI·실행 기반은 이우람이 조율한다.

## 7. 통합 순서

1. `V2 회원 계정·세션 → 회원가입·로그인·재발급·탈퇴`를 실제 PostgreSQL·Redis로 검증한다.
2. `인증 회원 → V3 찜·최근 기록 → 찜/최근 화면`을 연결하고, 탈퇴 뒤 데이터 삭제를 검증한다.
3. `V4 좌표 → 필터 기반 마커 API → 지도 마커·대체 목록`을 연결하고 지도 이동과 결과 집합을 분리한다.
4. `V6 Creator 표시 정보 → Creator 상세·방문 맛집·근거 영상`을 공개·유효 Visit 판정과 연결한다.
5. `FE-01 MVP 탐색 → 지도·유튜버 상세 → 로그인 개인화 → 탈퇴`의 교차 흐름을 `FE-09`에서 회귀한다.

각 단계의 자동화 테스트가 통과하기 전에는 다음 단계의 통합 완료를 선언하지 않는다. 임시 Stub·Fixture는 테스트 또는 명시적 로컬 개발 경계에만 두고, 실제 Port·API가 준비되면 제거하거나 테스트 전용으로 격리한다.

## 8. 전체 Task 표

실행·PR·검증에는 [최종 Task 분해](expansion-1-task-breakdown.md)의 `E1-T01`~`E1-T13`을 사용한다. 아래 `FE-*`는 구현 순서와 통합 단위를 설명하는 상위 묶음이다. 2026-08-03 지도 계약 변경은 `FE-10`·`E1-T11`, 가입 이메일 인증 코드 변경은 `FE-11`·`E1-T12`, 제한 공개 인증 변경은 `FE-12`·`E1-T13`에서 별도 추적한다.

| Task | 주 담당 / 기본 리뷰어 | 관련 FR·PRD | 주요 계약·산출물 | 선행 Task | 필수 검증 | 완료 기준 |
|---|---|---|---|---|---|---|
| `FE-00` 품질 게이트 기준선 복구 | 이우람 / 김인안 | `NFR-TEST-001`~`004`, `NFR-DEPLOYMENT-001`~`002` | 백엔드 테스트 class loading 원인 수정, CI 빌드·테스트 품질 게이트, 실패 결과 보존 | 없음 | Gradle 단위·통합·ArchUnit, 프론트 typecheck·build, CI 재현 | 현재 기준선의 백엔드 테스트가 실제 실행되고 PR에서 실패를 차단한다. |
| `FE-01` MVP 탐색 잔여 흐름 종료 | 양성훈 / 이우람 | `FR-RESTAURANT-005`, `FR-CREATOR-001`, `FR-CREATOR-003`; `PRD-DISCOVERY-001/002` | 유튜버 선택 UI, 목록 조건 결합, 환경 기반 API 주소 | `FE-00` | 브라우저 흐름, 목록·Creator API 계약, 빈·오류 상태 | 사용자가 URL 수기 입력 없이 유튜버를 선택하고 다른 탐색 조건과 함께 조회한다. |
| `FE-02` 회원 데이터·보안 기반 | 김인안 / 박진영 | `FR-MEMBER-001`~`005`, `FR-AUTH-001`~`003`; `PRD-ACCOUNT-001` | V2, Member·Action Token·세션 폐기 표식, 회원 Security matcher·principal·Redis namespace | `FE-00` | Flyway V1→V2, Testcontainers, JWT audience 교차 거부, Redis 원자 세션 테스트 | 회원·관리자 경계가 분리되고 V2가 기존 V1 데이터와 호환된다. |
| `FE-03` 회원 계정·인증 사용자 흐름 | 김인안 / 이우람 | `FR-MEMBER-001`~`005`, `FR-AUTH-001`~`003`; `PRD-ACCOUNT-001` | 가입·인증·재설정·로그인·재발급·로그아웃·현재 회원·탈퇴 API와 화면 | `FE-02` | MockMvc, Redis·PostgreSQL 통합, 브라우저 인증 만료·오류, 계정 열거 방지 | 정상·제한·장애·탈퇴 흐름과 최대 3세션·`sid` 즉시 폐기가 계약대로 동작한다. |
| `FE-04` 개인 맛집 데이터·API | 박진영 / 김인안 | `FR-FAVORITE-001`~`004`, `FR-RECENT-001`~`003`; `PRD-PERSONAL-001` | V3, Favorite·RecentView 명령·조회, 상세 성공 시 최근 기록, 하루 한 번 이상 30일 만료 cleanup Scheduler | `FE-02` | V1→V3, 중복 찜 동시성, recent `GREATEST` upsert·50건 상한·30일 주기 cleanup, 탈퇴 정리 | 관계 고유성·보존·공개 상태·본인 접근 계약이 데이터와 API에서 일치한다. |
| `FE-05` 개인 맛집 화면·통합 | 박진영 / 김인안 | `FR-FAVORITE-001`~`004`, `FR-RECENT-001`~`003`; `PRD-PERSONAL-001` | 찜 상태·목록, 최근 목록·삭제, 빈·비공개·인증 만료 화면 | `FE-03`, `FE-04` | 브라우저·API 통합, 모바일 빈 상태, 다른 회원 접근 거부 | 로그인 회원이 찜·최근 기록을 관리하고 탈퇴 뒤 개인화 데이터가 남지 않는다. |
| `FE-06` 지도 좌표·영역 조회(과거 계약) | 양성훈 / 박진영 | `FR-MAP-001`~`002`; `PRD-DISCOVERY-003` | V4, nullable WGS84 좌표와 과거 bounds API. bounds 부분은 `FE-10`에서 교체 | `FE-00` | V1→V4, 좌표 CHECK·인덱스·200개·429 | 좌표 없는 기존 맛집은 공개 목록·상세에 남고 지도에서만 제외된다. |
| `FE-07` 지도 화면·접근성 통합 | 양성훈 / 박진영 | `FR-MAP-001`~`002`; `PRD-DISCOVERY-003` | Kakao 지도·마커·대체 목록·선택 동기화, SDK/키/로그 경계 | `FE-06` | 지원 브라우저, 키보드·스크린 리더·360px, SDK 실패 | 지도 장애가 대체 목록·다른 공개 조회를 막지 않고 사용자 위치를 수집하지 않는다. |
| `FE-08` 유튜버 상세 수직 슬라이스 | 이우람 / 박진영 | `FR-CREATOR-004`~`006`; `PRD-DETAIL-002` | V6, Creator 표시 필드, 상세·방문 맛집·근거 영상 API와 화면 | `FE-00` | V1→V6, 공개·빈·404·중복·페이지, 외부 API 미호출, 브라우저 흐름 | 저장된 정보와 공개 유효 관계만으로 세 상세 상태가 일관되게 표시된다. |
| `FE-09` 1차 확장 교차 인수·회귀 | 전원 / 상호 교차 리뷰 | 1차 확장 전체 FR·NFR | 추적표 완료 확인, 보안·통합·브라우저·성능 결과와 기준선 비교 | `FE-01`, `FE-03`, `FE-05`, `FE-07`, `FE-08` | `NFR-TEST-004`, 성능 NFR, V1→V6 업그레이드, CI | 각 FR이 주 PRD·API 또는 화면·데이터·ADR·WS·테스트·Task로 추적되고 미결정 도입이 없음을 검토한다. |
| `FE-10` 지도 뷰포트 비종속 전환 | 양성훈 / 박진영 | `FR-MAP-001`~`002`; `PRD-DISCOVERY-003` | bounds API·SQL·Query Key·idle 재조회 제거, 필터 결과·목록 유지 | `FE-06`, `FE-07` | bounds 없는 API, 이동 시 요청 0건, 결과·선택 유지, 200/201개 | 지도 이동으로 검색 결과가 사라지지 않고 네 URL 필터 변경 때만 재조회한다. |
| `FE-11` 가입 이메일 인증 8자 코드 전환 | 김인안 / 이우람 | `FR-MEMBER-002`; `PRD-ACCOUNT-001` | 8자 CSPRNG 코드, 입력 정규화·제출 제한, 메일·화면 정합화 | `FE-02`, `FE-03` | 문자 집합·40-bit·5분·단일 소비·10분 10회·원문 비로그·장애별 입력 보존 | 가입 인증만 8자 코드로 동작하고 비밀번호 재설정·Access·Refresh Token 계약은 유지된다. |
| `FE-12` 검증 참여자 쿠키 세션 전환 (역사) | 이우람 / 김인안 | `NFR-SECURITY-003`, `NFR-DEPLOYMENT-004`; `ADR-DEPLOY-003` | 7일 HttpOnly 쿠키, Redis 세션, Nginx `auth_request`, Basic Auth 제거 | M2-11, `FE-03` | 무세션 차단, 반복창 0회, 회원·관리자 Bearer 동시 동작, 장애·로그·배포 복구 | M2 제한 공개 전환을 완료했다. 정식 공개에서는 전용 경계·자원만 제거하고 회원·관리자 인증은 유지한다. |

## 9. Workstream별 Task

| Workstream | Task | 수직 슬라이스 | 인수 테스트 핵심 |
|---|---|---|---|
| WS-05 | `FE-02` | V2, 회원 principal·Security·Redis session | 관리자/회원 audience 교차 거부, 3세션, V1→V2 |
| WS-05 | `FE-03` | 가입·메일 인증·재설정·로그인·탈퇴 API/화면 | 만료·제한·장애·계정 열거 방지·`sid` 즉시 폐기 |
| WS-05 | `FE-11` | 가입 이메일 인증 8자 코드·메일·API·화면 정합화 | 문자 집합·CSPRNG·제출 제한·단일 소비·원문 비로그·비밀번호 재설정 회귀 |
| [OPS-VALIDATION](../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙) | `FE-12` | 검증 참여자 쿠키 세션과 Nginx·Redis 진입 경계 | 반복 인증창 0회, Bearer 충돌 제거, 무세션·장애 차단, 정식 공개 제거 가능성 |
| WS-06 | `FE-04` | V3 Favorite·Recent 명령/조회와 상세 성공 부수효과 | 동시 중복 찜, `GREATEST` upsert·50건 상한, 30일 주기 cleanup, 탈퇴 |
| WS-06 | `FE-05` | 찜·최근 목록과 빈·비공개·인증 만료 화면 | 다른 회원 접근 거부, 모바일 상태, 탈퇴 정리 |
| WS-07 | `FE-06` | V4 좌표와 과거 bounds API·보강 절차 | 좌표 CHECK·인덱스·200개·429·NULL 호환; bounds는 `FE-10`에서 제거 |
| WS-07 | `FE-07` | Kakao 지도·마커·대체 목록·접근성 | SDK 장애, 360px, 키보드·스크린 리더, 사용자 위치 비수집 |
| WS-07 | `FE-10` | 뷰포트 비종속 API·Query Key·지도 이동 UX 정합화 | 이동 시 요청 0건, 결과·선택 유지, 필터 변경 재조회 |
| WS-08 | `FE-08` | V6, Creator 상세·방문 맛집·근거 영상 API/화면 | 공개·빈·404·중복·페이지, 외부 API 미호출 |
| 공통 | `FE-00`, `FE-01`, `FE-09` | 품질 게이트·MVP 탐색 잔여·교차 회귀 | CI 차단, Creator 선택, V1→V6, 보안·브라우저·성능 |

## 10. 1차 확장 FR → Task 세부 매핑

| 기능 요구사항 | 구현 Task | 인수·회귀 Task |
|---|---|---|
| `FR-MEMBER-001`, `FR-MEMBER-002`, `FR-MEMBER-003`, `FR-MEMBER-004`, `FR-MEMBER-005`, `FR-AUTH-001`, `FR-AUTH-002`, `FR-AUTH-003` | `FE-02`, `FE-03`, `FE-11` | `FE-09` 재실행 |
| `FR-FAVORITE-001`, `FR-FAVORITE-002`, `FR-FAVORITE-003`, `FR-FAVORITE-004`, `FR-RECENT-001`, `FR-RECENT-002`, `FR-RECENT-003` | `FE-04`, `FE-05` | `FE-09` |
| `FR-MAP-001`, `FR-MAP-002` | `FE-06`, `FE-07`, `FE-10` | `FE-09` 재실행 |
| `FR-CREATOR-004`, `FR-CREATOR-005`, `FR-CREATOR-006` | `FE-08` | `FE-09` |

MVP 기능 요구사항은 [MVP 구현 계획 Task 목록](mvp-2day-implementation-plan.md#8-task-목록)의 `T-01`~`T-14`가 소유하며, 1차 확장과 맞닿은 미완료 탐색 흐름은 `FE-01`에서 닫는다.

## 11. 위험과 대응

| 위험 | 영향 | 대응·중단 기준 |
|---|---|---|
| 백엔드 테스트·CI 기준선 미복구 | 모든 완료 판정이 신뢰 불가 | `FE-00`을 우선 처리한다. 실제 테스트 실행 실패 상태에서는 후속 Task를 완료 처리하지 않는다. |
| 관리자 인증 재사용 중 경계 혼합 | 회원 Token으로 관리자 접근 또는 세션 상호 오염 | audience·principal·cookie·Redis namespace·Security matcher를 `FE-02`에서 분리하고 교차 거부 테스트를 둔다. |
| V2~V6 병렬 변경 충돌 | Flyway 순서·FK·데이터 호환성 파손 | 기존 V1은 수정하지 않고 전진 migration만 추가한다. 박진영이 번호·업그레이드 검증을 최종 조율한다. |
| 좌표 결측·외부 SDK 장애 | 지도 화면이 공개 탐색을 막음 | NULL 좌표는 지도에서만 제외하고 대체 목록을 유지한다. SDK 실패는 독립 오류 상태로 처리한다. |
| 개인정보·위치·토큰 로그 유출 | 보안·개인정보 NFR 위반 | Token·메일 인증값·사용자 위치를 로그에서 제외하고 지도 뷰포트는 서버로 전송하지 않는 회귀 테스트를 둔다. |
| 조건부 기술의 조기 도입 | 범위·운영 복잡도 확대 | Backlog 활성화와 Accepted ADR, 별도 Task 없이는 PostGIS·캐시·락·Outbox·자동 동기화를 구현하지 않는다. |

## 12. 권장 구현 핵심 경로

```text
공통 계약·품질 게이트 확정 (FE-00, FE-01)
  ↓
회원 스키마·일반 사용자 인증 (FE-02, FE-03)
  ↓
찜·최근 본 맛집 (FE-04)
  ↓
회원 기반 사용자 여정 통합 (FE-05)

병렬: 맛집 좌표 정책 → 지도 탐색 → 뷰포트 비종속 정합화 (FE-06 → FE-07 → FE-10)
병렬: Creator 계약 확장 → 유튜버 상세 (FE-08)
  ↓
교차 인수·회귀 (FE-09)
```

## 13. 확장 1차 완료 정의

- `FE-00`의 실제 테스트 실행·CI 차단이 복구되고, MVP 잔여 유튜버 선택 흐름(`FE-01`)이 완료된다.
- 각 1차 확장 FR은 정확히 하나의 주 PRD와 API 또는 화면 계약, 데이터 소유·생명주기, Accepted ADR 또는 명시적 보류, Workstream·테스트·Task를 가진다.
- 회원은 최대 3개 세션으로 가입·로그인·재발급·로그아웃·재설정·탈퇴를 수행하고, 찜·최근 기록은 본인 경계와 보존·삭제 정책을 지킨다.
- 지도는 NULL 좌표·SDK 장애에도 대체 목록을 제공하고 지도 이동 중 검색 결과를 유지하며, 유튜버 상세는 외부 실시간 호출 없이 저장된 공개 관계만 표시한다.
- V1→V6 업그레이드와 보안·통합·브라우저·접근성·성능 테스트가 CI에서 통과한다.
- 각 Task는 연결된 FR의 정상·예외·경계 인수 조건을 자동화 테스트로 증명한다.
- 외부 API, API 키, 개인정보, 보안 Token, migration 순서, 공통 Layout 및 인증 경계를 바꾸면 영향 Workstream의 기본 리뷰어가 검토한다.
- Conditional·Post-MVP ADR의 활성화 조건을 만족하지 않은 경우에는 구현 Task를 새로 만들지 않고 Backlog에 근거와 함께 남긴다.
- Task 완료 표시는 코드·계약·테스트·추적표가 같은 PR에서 동기화됐을 때만 허용한다.
