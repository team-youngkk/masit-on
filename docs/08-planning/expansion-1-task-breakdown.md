---
status: Ready
plan_date: 2026-07-29
related_documents:
  - expansion-1-implementation-plan.md
  - first-expansion-baseline-review.md
  - ../02-analysis/first-expansion-workstreams.md
  - ../03-team/ownership.md
  - ../04-product/traceability.md
  - ../05-specs/api-traceability.md
  - ../05-specs/data/data-traceability.md
  - ../07-adr/adr-traceability.md
---

# 맛잇온 1차 확장 최종 Task 분해

## 1. 목적과 사용 규칙

이 문서는 [1차 확장 구현 계획](expansion-1-implementation-plan.md)의 단계별 `FE-*` 작업 묶음을 실행 가능한 `E1-T01`~`E1-T10` Task로 분해한다. 이 문서의 `E1-T*`가 구현·PR·검증 진행 상태를 기록하는 최종 Task ID이며, `FE-*`는 일정·통합 순서를 설명하는 상위 묶음으로만 유지한다.

담당자와 기본 리뷰어는 [1차 확장 Workstream](../02-analysis/first-expansion-workstreams.md)의 확정 배정을 사용한다. 소유권 변경, 공통 파일 병합 책임 변경, 또는 API·데이터·ADR 계약 변경은 이 문서만 고쳐서는 안 되며 팀 합의와 상위 계약 갱신을 먼저 거친다.

## 2. 전체 Task 표

| ID | Task | 담당자 / 기본 리뷰어 | 선행 Task | 병렬 가능 | 크기 |
|---|---|---|---|---|---|
| `E1-T01` | 품질 게이트 기준선 복구 | 이우람 / 김인안 | 없음 | 아니오 | L |
| `E1-T02` | MVP 유튜버 선택 탐색 흐름 종료 | 양성훈 / 이우람 | `E1-T01` | 아니오 | M |
| `E1-T03` | 회원 데이터·보안 기반 | 김인안 / 박진영 | `E1-T01` | 지도·Creator 경로와 가능 | L |
| `E1-T04` | 회원 계정·인증 사용자 여정 | 김인안 / 이우람 | `E1-T03` | `E1-T05`와 가능 | L |
| `E1-T05` | 찜·최근 본 데이터·API | 박진영 / 김인안 | `E1-T03` | `E1-T04`와 가능 | L |
| `E1-T06` | 찜·최근 본 화면 통합 | 박진영 / 김인안 | `E1-T04`, `E1-T05` | 아니오 | M |
| `E1-T07` | 지도 좌표·영역 조회 | 양성훈 / 박진영 | `E1-T01` | 회원·Creator 경로와 가능 | L |
| `E1-T08` | 지도 화면·접근성 통합 | 양성훈 / 박진영 | `E1-T07` | `E1-T09`와 가능 | M |
| `E1-T09` | 유튜버 상세 수직 슬라이스 | 이우람 / 박진영 | `E1-T01` | 지도·회원 경로와 가능 | L |
| `E1-T10` | 1차 확장 교차 인수·회귀 | 전원 / 상호 교차 리뷰 | `E1-T02`, `E1-T04`, `E1-T06`, `E1-T08`, `E1-T09` | 아니오 | L |

크기는 한 명이 하나의 PR로 다룰 수 있는 작업량 기준으로 `S`(반일 이하), `M`(1일), `L`(1일 초과 또는 여러 계층 통합)이다. `L` Task는 내부에서 테스트·마이그레이션·화면 작업을 나눌 수 있지만, 아래 완료 조건이 충족될 때만 완료 처리한다.

## 3. Task 상세

### E1-T01 품질 게이트 기준선 복구

- 담당자 / 리뷰어: 이우람 / 김인안
- 관련 계약: `NFR-TEST-001`~`004`, `NFR-DEPLOYMENT-001`~`002`; [테스트·CI ADR](../07-adr/quality/test-001-automation-strategy.md), [CI ADR](../07-adr/platform/ci-001-github-actions-quality-gate.md)
- API·테이블·ADR: 신규 API·테이블 없음; [ADR-TEST-001](../07-adr/quality/test-001-automation-strategy.md), [ADR-CI-001](../07-adr/platform/ci-001-github-actions-quality-gate.md)
- 수정 예상 영역: Gradle 테스트 설정·테스트 classpath, `.github/workflows`, 프론트 typecheck·build 명령, 실행·검증 문서
- 선행 / 병렬 / 크기: 없음 / 다른 기능 Task의 완료 판정 전에는 병렬 완료 불가 / L
- 테스트 범위: Gradle 단위·통합·ArchUnit 실제 실행, 프론트 typecheck·build, CI에서 실패 차단 재현
- 완료 조건: 백엔드 테스트가 class loading을 통과해 실제 테스트 결과를 내고, PR CI가 빌드·테스트 실패를 차단한다.

### E1-T02 MVP 유튜버 선택 탐색 흐름 종료

- 담당자 / 리뷰어: 양성훈 / 이우람
- 관련 계약: `FR-RESTAURANT-005`, `FR-CREATOR-001`, `FR-CREATOR-003`; `PRD-DISCOVERY-001`, `PRD-DISCOVERY-002`; [맛집·유튜버 탐색 API](../05-specs/api/discovery/restaurant-discovery-api.md), [유튜버 선택 API](../05-specs/api/discovery/creator-discovery-api.md)
- 테이블·ADR: `restaurant`, `creator`, `visit`; [ADR-WEB-002](../07-adr/platform/web-002-data-state.md), [ADR-ARCH-001](../07-adr/architecture/arch-001-domain-monolith.md)
- 수정 예상 영역: `frontend/app/restaurants`, 목록 API client와 환경 기반 API 주소, Creator 선택 UI·빈/오류 상태
- 선행 / 병렬 / 크기: `E1-T01` / 아니오 / M
- 테스트 범위: Creator 선택→목록 AND 조건 브라우저 흐름, 목록·Creator API 계약, 새로고침·빈·오류 상태
- 완료 조건: 사용자는 URL을 수기 입력하지 않고 유튜버를 선택해 다른 탐색 조건과 함께 맛집을 조회한다.

### E1-T03 회원 데이터·보안 기반

- 담당자 / 리뷰어: 김인안 / 박진영
- 관련 계약: `FR-MEMBER-001`~`005`, `FR-AUTH-001`~`003`; `PRD-ACCOUNT-001`; [회원 계정·인증 API](../05-specs/api/account/member-authentication-api.md); [ADR-AUTH-002](../07-adr/security/auth-002-member-jwt-refresh-token.md)
- 테이블·ADR: V2 `member_account`, `member_action_token`, `member_session_revocation`, 회원 Redis 세션·제한 key; [Flyway ADR](../07-adr/data/data-004-flyway.md), [보안 경계](../06-architecture/security-boundary.md)
- 수정 예상 영역: `security`, 신규 `member` 도메인, Flyway V2, JWT audience·principal·Security matcher, Redis namespace·세션 회전
- 선행 / 병렬 / 크기: `E1-T01` / `E1-T07`, `E1-T09`와 가능 / L
- 테스트 범위: V1→V2 Testcontainers, 회원·관리자 JWT audience 교차 거부, 최대 3세션·원자 회전·재사용 탐지, Redis 장애 fail-closed
- 완료 조건: 회원과 관리자 identity·Token·쿠키·Redis 경계가 분리되고, V2가 기존 V1 데이터에 전진 적용된다.

### E1-T04 회원 계정·인증 사용자 여정

- 담당자 / 리뷰어: 김인안 / 이우람
- 관련 계약: `FR-MEMBER-001`~`005`, `FR-AUTH-001`~`003`; `PRD-ACCOUNT-001`; [회원 계정·인증 API](../05-specs/api/account/member-authentication-api.md); [ADR-AUTH-002](../07-adr/security/auth-002-member-jwt-refresh-token.md)
- 테이블·ADR: `member_account`, `member_action_token`, `member_session_revocation`, 회원 Redis 세션·제한 key; [오류 계약](../05-specs/api/common/error-contract.md)
- 수정 예상 영역: 회원 Application·Controller·메일 Adapter·Security, 회원가입/로그인/재설정/현재 회원/탈퇴 화면, 인증 만료·권한 오류 공통 상태
- 선행 / 병렬 / 크기: `E1-T03` / `E1-T05`와 가능 / L
- 테스트 범위: MockMvc, PostgreSQL·Redis 통합, 메일·Redis 장애, 계정 열거 방지, 브라우저 가입·로그인·만료·탈퇴 여정
- 완료 조건: 가입·인증·재설정·로그인·재발급·로그아웃·탈퇴가 정상·제한·장애 경계에서 계약대로 동작하고, 탈퇴 즉시 `sid`가 폐기된다.

### E1-T05 찜·최근 본 데이터·API

- 담당자 / 리뷰어: 박진영 / 김인안
- 관련 계약: `FR-FAVORITE-001`~`004`, `FR-RECENT-001`~`003`; `PRD-PERSONAL-001`; [개인 맛집 API](../05-specs/api/personal/personal-restaurant-api.md), [맛집 상세 API](../05-specs/api/detail/restaurant-detail-api.md)
- 테이블·ADR: V3 `favorite`, `recent_restaurant_view`; [ADR-DATA-003](../07-adr/data/data-003-spring-data-jpa.md), [ADR-AUTH-002](../07-adr/security/auth-002-member-jwt-refresh-token.md), [ADR-AUTO-001](../07-adr/adr-backlog.md#adr-auto-001-자동-수집과-배치-처리)
- 수정 예상 영역: 신규 개인화 도메인/Port, Flyway V3, 맛집 상세 성공 시 최근 기록 부수효과, 하루 한 번 이상 30일 만료 cleanup Scheduler와 관측·재시도, 본인 자원 인가·탈퇴 정리
- 선행 / 병렬 / 크기: `E1-T03` / `E1-T04`와 가능 / L
- 테스트 범위: V1→V3, 중복·동시 찜, `GREATEST` upsert·최신 50건 상한, 30일 주기 cleanup, 비공개 맛집 숨김, 다른 회원 접근·탈퇴 정리
- 완료 조건: 관계 고유성·멱등성·보존·공개 상태·본인 접근 규칙이 API와 DB 제약에서 일치한다.

### E1-T06 찜·최근 본 화면 통합

- 담당자 / 리뷰어: 박진영 / 김인안
- 관련 계약: `FR-FAVORITE-001`~`004`, `FR-RECENT-001`~`003`; `PRD-PERSONAL-001`; [개인 맛집 API](../05-specs/api/personal/personal-restaurant-api.md)
- 테이블·ADR: `favorite`, `recent_restaurant_view`; [ADR-WEB-002](../07-adr/platform/web-002-data-state.md), [ADR-AUTH-002](../07-adr/security/auth-002-member-jwt-refresh-token.md)
- 수정 예상 영역: 찜 상태·목록, 최근 목록·삭제, 빈·비공개·인증 만료 화면과 공통 Layout 인증 상태
- 선행 / 병렬 / 크기: `E1-T04`, `E1-T05` / 아니오 / M
- 테스트 범위: 브라우저·API 통합, 모바일 빈 상태, 비공개 자원 표시 정책, 다른 회원 접근 거부, 탈퇴 후 목록 없음
- 완료 조건: 로그인 회원이 찜·최근 기록을 관리하고, 인증 만료·빈·비공개 상태를 명확히 안내받는다.

### E1-T07 지도 좌표·영역 조회

- 담당자 / 리뷰어: 양성훈 / 박진영
- 관련 계약: `FR-MAP-001`~`002`; `PRD-DISCOVERY-003`; [지도 탐색 API](../05-specs/api/discovery/map-discovery-api.md); [ADR-MAP-001](../07-adr/integration/map-001-map-bounds-search.md)
- 테이블·ADR: V4 `restaurant.latitude`, `restaurant.longitude`; [Flyway ADR](../07-adr/data/data-004-flyway.md), [좌표 계약](../05-specs/api/common/coordinate-contract.md)
- 수정 예상 영역: Kakao 확인 응답·snapshot, Restaurant 도메인/JPA, Flyway V4, bounds Query·Controller, 좌표 backfill 운영 절차
- 선행 / 병렬 / 크기: `E1-T01` / `E1-T03`~`E1-T05`, `E1-T09`와 가능 / L
- 테스트 범위: V1→V4, 좌표 쌍·범위 CHECK, partial index·bounds 실행계획, AND 조건·200개 상한·429, NULL 좌표 호환
- 완료 조건: 좌표 없는 기존 맛집은 공개 목록·상세에 유지되고 지도 bounds 결과에서만 제외된다.

### E1-T08 지도 화면·접근성 통합

- 담당자 / 리뷰어: 양성훈 / 박진영
- 관련 계약: `FR-MAP-001`~`002`; `PRD-DISCOVERY-003`; [지도 탐색 API](../05-specs/api/discovery/map-discovery-api.md); [ADR-MAP-001](../07-adr/integration/map-001-map-bounds-search.md)
- 테이블·ADR: `restaurant.latitude`, `restaurant.longitude`; [외부 SDK 보안 경계](../06-architecture/security-boundary.md), [ADR-WEB-001](../07-adr/platform/web-001-frontend-platform.md)
- 수정 예상 영역: 지도 Route·Kakao Maps SDK loader, 마커·대체 목록·선택 동기화, 키·로그 경계, 모바일·키보드 접근성
- 선행 / 병렬 / 크기: `E1-T07` / `E1-T09`와 가능 / M
- 테스트 범위: 지원 브라우저, 360px, 키보드·스크린 리더·터치, SDK 실패, bounds 원문 로그 제외
- 완료 조건: 지도 장애가 대체 목록과 다른 공개 조회를 막지 않으며, 마커와 목록 선택이 양방향으로 동기화된다.

### E1-T09 유튜버 상세 수직 슬라이스

- 담당자 / 리뷰어: 이우람 / 박진영
- 관련 계약: `FR-CREATOR-004`~`006`; `PRD-DETAIL-002`; [유튜버 상세 API](../05-specs/api/detail/creator-detail-api.md); [ADR-ARCH-001](../07-adr/architecture/arch-001-domain-monolith.md)
- 테이블·ADR: V5 Creator 상세 표시 컬럼, `creator`, `visit`, `restaurant`, `video`; [Flyway ADR](../07-adr/data/data-004-flyway.md), [조회 조합](../06-architecture/query-composition.md)
- 수정 예상 영역: Creator 도메인·Projection/Orchestration, Flyway V5, 상세·방문 맛집·근거 영상 API, Creator 상세 Route
- 선행 / 병렬 / 크기: `E1-T01` / `E1-T03`~`E1-T08`과 가능 / L
- 테스트 범위: V1→V5, 공개·빈·404·중복 제거·페이지, 비공개/삭제 관계, 사용자 조회 중 외부 API 미호출, 브라우저 상세 흐름
- 완료 조건: 저장된 공개·유효 관계만으로 채널 정보, 방문 맛집, 근거 영상의 세 화면 상태가 일관되게 표시된다.

### E1-T10 1차 확장 교차 인수·회귀

- 담당자 / 리뷰어: 전원 / 상호 교차 리뷰
- 관련 계약: 1차 확장 전체 FR·BR·NFR·PRD; [제품 추적표](../04-product/traceability.md), [API 추적표](../05-specs/api-traceability.md), [데이터 추적표](../05-specs/data/data-traceability.md), [ADR 추적표](../07-adr/adr-traceability.md)
- API·테이블·ADR: 회원·개인화·지도·Creator 전체 API와 V2~V5, [ADR-AUTH-002](../07-adr/security/auth-002-member-jwt-refresh-token.md), [ADR-MAP-001](../07-adr/integration/map-001-map-bounds-search.md), 테스트·CI ADR
- 수정 예상 영역: 교차 인수 테스트·브라우저 테스트·CI 결과, 추적표와 완료 기록. 기능 계약을 새로 결정하거나 범위를 확장하지 않는다.
- 선행 / 병렬 / 크기: `E1-T02`, `E1-T04`, `E1-T06`, `E1-T08`, `E1-T09` / 아니오 / L
- 테스트 범위: V1→V5 업그레이드, 인증·탈퇴·개인화, 지도 SDK·접근성, Creator 공개 상태, 성능 NFR, CI 전체 회귀
- 완료 조건: 모든 1차 확장 FR이 주 PRD·API 또는 화면·데이터·ADR·Workstream·테스트·`E1-T*`로 추적되고, Conditional/Post-MVP 기술의 무단 도입이 없다.

## 4. 변경 통제와 리뷰 순서

1. 구현 시작 전 해당 Task의 상위 FR·PRD·API·데이터·ADR가 모두 `확정` 또는 명시적 보류인지 확인한다.
2. Task 구현 PR에는 코드, 대상 테스트, 영향을 받는 계약 문서와 추적표를 함께 포함한다. 상위 계약을 바꾸려면 별도 계약 변경 리뷰를 먼저 한다.
3. 담당자 외 기본 리뷰어가 API·데이터·보안·공통 파일 영향과 완료 조건을 검토한다. 공통 Layout·Flyway 번호·인증 경계·CI는 지정 조율자가 최종 병합한다.
4. 미결정 사항을 구현 Task의 TODO나 가정으로 넘기지 않는다. 범위 확대가 필요하면 Backlog와 ADR 활성화, 새 Task 합의를 선행한다.
