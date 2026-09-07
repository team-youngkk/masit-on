---
related_documents:
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/admin-data-management.md
  - ../04-product/prd/detail/restaurant-detail.md
  - ../05-specs/api/admin/tag-definition-api.md
  - ../05-specs/api/admin/restaurant-visit-tags-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../05-specs/data/migration-plan.md
  - restaurant-visit-tag-editing.md
---

# 관리자 태그 정의 생성 및 상세 즉시 선택 구현 계획

## 1. 배경과 목표

이슈 [#363](https://github.com/team-youngkk/masit-on/issues/363)은 #358의 맛집 상세 방문 태그 편집에서 기존 활성 태그만 선택할 수 있는 제약을 보완한다. ADMIN이 유형·코드·표시명·선택적 별칭으로 새 통제 태그를 만들고, 생성 결과를 현재 방문의 선택 상태에 즉시 추가한다.

표시명·별칭 중복과 동시 생성은 애플리케이션의 선행 조회만으로 확정하지 않는다. 사용자가 선택한 A안에 따라 V10 `tag_definition_term`을 추가하고 정규화 용어를 전역 unique로 관리한다. 후속 #364는 이 테이블을 동적 자연어 사전으로 재사용한다.

## 2. 확정 범위

- ADMIN 전용 `GET /api/admin/tag-definitions` 활성 목록
- ADMIN 전용 `POST /api/admin/tag-definitions` 생성
- 유형 `MENU`, `TASTE`, `OCCASION`, `ATMOSPHERE`
- 유형 접두사를 갖는 통제 코드와 표시명·별칭 검증
- Unicode NFKC·공백·영문 대소문자 정규화와 전역 용어 중복 차단
- `MANUAL_OVERRIDE`, `ACTIVE` 정의와 용어의 원자적 생성
- 맛집 상세 편집 패널의 생성 폼, TanStack Query 목록 갱신, 현재 방문 즉시 로컬 선택
- 기존 AI 자동 태그 생성 경로가 V10 term도 함께 쓰도록 보강
- 요구사항·PRD·API·데이터 계약·추적표와 구현·검증을 같은 PR에 포함

## 3. 제외 범위

- 기존 태그 수정·비활성화·재활성화와 변경 감사(#365)
- 자연어 파서의 동적 DB 사전 전환과 별칭 단어 경계 정책(#364)
- 태그 병합과 기존 `VisitTag` 안전 이전(#366)
- 물리 삭제
- 태그 생성과 방문 태그 저장을 하나의 결합 API로 만드는 변경
- 공개 사용자에게 태그 코드·별칭·관리 UI를 노출하는 변경

## 4. 확정 계약

### 4.1 태그와 용어

- `tag_definition`은 코드·유형·표시 원문·별칭 JSONB·상태·출처를 계속 소유한다.
- V10 `tag_definition_term`은 중복 판정용 정규화 키를 소유한다. `normalized_term`은 ACTIVE/DEPRECATED 전체에서 전역 unique다.
- 정규화 순서는 NFKC → trim → 연속 Unicode 공백 한 칸 → `Locale.ROOT` 영문 소문자다.
- 표시명은 하나, 별칭은 0~20개이고 요청 내부에서도 서로 다른 정규화 키여야 한다.
- ADMIN 생성은 `ACTIVE/MANUAL_OVERRIDE`, AI Snapshot 없음이다. AI 생성은 기존 source·Snapshot 근거를 유지한다.
- ADMIN·AI 작성자는 정의·JSONB 별칭·모든 term을 한 트랜잭션에서 생성한다. unique 위반은 409이며 부분 저장은 0건이다.

### 4.2 화면과 저장 경계

1. ADMIN이 맛집 상세 태그 편집을 연다.
2. 활성 목록을 계정 범위를 포함한 TanStack Query key로 조회한다.
3. 관리자가 새 태그 값을 입력해 생성한다.
4. 201 응답을 목록 캐시에 반영하고 서버 목록을 invalidate/refetch한다.
5. 새 태그 코드를 현재 방문의 로컬 선택 상태에 추가하되 기존 선택·사유는 보존한다.
6. 관리자가 기존 방문 태그 저장을 실행해야 `VisitTag`와 `visit_tag_revision`이 같은 트랜잭션으로 확정된다.

태그 생성 후 방문 저장을 취소하거나 실패해도 태그 정의는 유지한다. 생성 시점에는 Visit 연결 감사가 없다. 세션 종료·계정 전환 시 목록·생성·편집 캐시를 폐기하고 익명·MEMBER는 관리자 API를 호출하지 않는다.

## 5. 구현 순서와 소유 범위

### 단계 1. V10 데이터 경계

- 정규화 DB 함수, `tag_definition_term`, FK·CHECK·unique·partial unique를 전진 마이그레이션으로 추가한다.
- 기존 표시명·별칭을 역적재한다. 과거 AI 작성자가 만든 표시명 자기 중복 별칭만 명시적으로 제거하고, 그 밖의 충돌은 사전 검사 결과가 0건일 때만 적용하며 임의 병합하지 않는다.
- V4의 18개 seed와 기존 AI 생성 데이터를 그대로 보존한다.
- 고정 migration 버전 기대값이 있는 테스트는 V10을 포함하도록 갱신한다.

### 단계 2. 백엔드 생성 경로

- 태그 정의 도메인의 application port/service와 JDBC adapter가 활성 목록과 생성 명령을 소유한다.
- 입력 형식·금지 표현을 검증하고 애플리케이션 정규화와 DB 함수의 동등성을 유지한다.
- tag code와 normalized term unique 위반을 구분된 409 오류로 변환한다.
- 기존 AI 정의 저장 경로에도 term 원자 저장을 적용한다. 후보·정식 Entity 원자성은 유지한다.
- `/api/admin/**` 서버 인가와 no-store·traceId 공통 계약을 적용한다.

### 단계 3. 프론트 상세 흐름

- #358 관리자 패널에 태그 생성 폼을 추가한다. 유형·코드·표시명·별칭 오류를 필드 가까이 표시한다.
- 목록 query와 생성 mutation을 분리하고 중복 제출을 막는다.
- 성공 시 현재 방문에 즉시 선택하고 refetch가 편집 중 선택·사유를 덮어쓰지 않게 병합한다.
- 생성 취소, 서버 오류와 409 충돌 뒤 입력을 유지해 수정·재시도할 수 있게 한다.
- 관리자만 생성 폼을 렌더링하고 계정 전환 시 요청 abort와 캐시 제거를 수행한다.

### 단계 4. 계약 동기화와 검증

- API·데이터 구현이 이 문서와 일치하는지 추적표에서 역추적한다.
- 브라우저에서 생성→즉시 선택→방문 저장, 생성 후 방문 저장 취소, 충돌 재시도를 확인한다.
- PR은 템플릿을 사용하고 #363을 연결하며 문서·구현·검증 결과를 함께 제출한다.

## 6. 필수 검증

| 범위 | 시나리오 |
|---|---|
| 인증·공개 경계 | 익명 401, MEMBER 403, ADMIN 200/201, 공개 상세 응답과 화면에 내부 코드·생성 UI 없음 |
| 입력 | 네 유형 정상, 잘못된 유형·접두사·코드 문자·길이, 빈 표시명, 별칭 20개 경계·초과, 금지 표현 |
| 정규화 | NFKC 호환 문자, 앞뒤/연속 공백, 영문 대소문자, 표시명↔별칭·별칭↔별칭 충돌 |
| 동시성·원자성 | 같은 코드·같은 normalized term 동시 POST에서 하나만 201, 나머지 409, 부분 definition/term 0건 |
| 마이그레이션 | 빈 DB V1→V10, V9→V10, 18개 seed 보존·역적재, AI 자기 중복 별칭 정리, 그 밖의 legacy 충돌 시 V10 전체 실패, 버전 목록 V10 포함 |
| AI 회귀 | AI 신규 태그가 정의·term을 함께 생성, 실패 시 후보 외 정식 부분 저장 0건, 기존 provenance 유지 |
| 방문 연결·감사 | POST만으로 VisitTag/revision 0건, 이후 PUT 성공 시 연결·감사 일치, PUT 취소/실패 시 연결 없음 |
| 프론트 | 생성 후 현재 방문 즉시 선택, 다른 선택·사유 유지, 목록 갱신, 중복 제출 방지, 오류 후 재시도, 세션 전환 캐시 폐기 |
| 전체 회귀 | 백엔드 clean build, 프론트 test/typecheck/build, 관련 통합 테스트와 관리자 상세 브라우저 검증 |

테스트 데이터는 운영 seed를 삭제하거나 대체해 통과시키지 않는다. 실제 PostgreSQL 제약과 Flyway 적용 순서를 Testcontainers에서 검증하고, 프론트 fixture 검증과 실제 백엔드 연결 검증을 구분해 기록한다.

## 7. 완료 조건

- 문서 계약과 코드가 같은 PR에서 일치한다.
- ADMIN만 활성 태그 목록과 생성을 사용할 수 있다.
- DB unique가 정규화 표시명·별칭의 동시 중복을 막는다.
- ADMIN·AI 작성 경로에서 정의·용어가 원자적으로 일치한다.
- 생성 직후 현재 방문에서 선택할 수 있고, 별도 저장 전에는 VisitTag·감사가 생기지 않는다.
- 정상·예외·경계·동시성·마이그레이션·브라우저 검증 결과를 PR에 기록한다.

## 8. 구현 중 결정 요청 기준

다음 상황은 계약을 임의 확대하지 않고 선택지·영향·추천안을 사용자에게 제시한다.

- 정규화 방식 또는 전역 고유 범위를 바꿔야 하는 기존 데이터 충돌
- 금지 표현 정책을 새로 정의하거나 기존 AI 정책과 다르게 적용해야 하는 경우
- API 경로·필드·상태 코드 또는 기존 `tagOptions` 호환을 깨야 하는 경우
- 태그 생성과 Visit 저장의 트랜잭션 경계를 결합해야 하는 경우
- 기존 마이그레이션 수정이나 데이터 자동 병합이 필요한 경우

문서에 확정된 범위 안의 패키지·컴포넌트 이름과 내부 구현 선택은 기존 아키텍처·컨벤션에 따라 진행한다.
