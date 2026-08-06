---
status: Ready
plan_date: 2026-08-03
related_documents:
  - expansion-2-implementation-plan.md
  - expansion-2-task-breakdown.md
  - second-expansion-baseline-review.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../01-requirements/non-functional-requirements.md
  - ../04-product/traceability.md
  - ../05-specs/api-traceability.md
  - ../05-specs/data/data-traceability.md
  - ../07-adr/adr-traceability.md
---

# 맛잇온 2차 확장 테스트 추적표

## 1. 목적과 판정 규칙

이 문서는 2차 확장 요구사항을 구현 Task의 자동화·인수 검증으로 연결하는 테스트 기준선이다. `TST-E2-*`는 테스트 파일 이름이 아니라 반드시 구현되어야 하는 검증 묶음 ID다. 각 묶음은 해당 계층의 정상·경계·권한·장애 시나리오를 자동화하고, 실행 증거를 연결한 뒤에만 완료한다.

모든 기능 Task는 단위·API 계약·PostgreSQL 통합 테스트를 기본으로 포함한다. 화면이 있는 기능은 브라우저 테스트를 추가한다. 동시성·보존·실행계획처럼 실제 저장소 동작에 의존하는 판정은 mock만으로 완료하지 않는다.

## 2. 기능 요구사항 → 테스트 → Task

| 요구사항 | 핵심 검증 | 주 테스트 묶음 | 구현 Task |
|---|---|---|---|
| `FR-COLLECTION-001` | 인증·멱등 생성·회원당 20개 동시 상한 | `TST-E2-COL-001` | `E2-T03` |
| `FR-COLLECTION-002` | 본인 소유 이름 변경·입력 검증·존재 은닉 | `TST-E2-COL-001` | `E2-T03` |
| `FR-COLLECTION-003` | 본인 소유 삭제·구성 관계 연쇄 삭제·반복 요청 | `TST-E2-COL-001` | `E2-T03` |
| `FR-COLLECTION-004` | 목록·상세 고정 정렬·비공개 맛집 숨김 | `TST-E2-COL-001` | `E2-T03` |
| `FR-COLLECTION-005` | 공개 맛집만 추가·컬렉션별 추가 상태·중복 멱등·100개 동시 상한·성공/실패 후 재조회 | `TST-E2-COL-001` | `E2-T03` |
| `FR-COLLECTION-006` | 관계 제거·반복 제거·다른 컬렉션 무영향 | `TST-E2-COL-001` | `E2-T03` |
| `FR-POPULAR-001` | 현재 찜 집계·공개 상태·상위 20·동점 안정 정렬 | `TST-E2-POP-001` | `E2-T04`, `E2-T05` |
| `FR-CURATION-001` | 관리자 생성·DRAFT·구성/메인 상한·감사 이력 | `TST-E2-CUR-001` | `E2-T06` |
| `FR-CURATION-002` | 원자적 완전 교체·표시 순서·실패 시 기존 구성 유지 | `TST-E2-CUR-001` | `E2-T06` |
| `FR-CURATION-003` | 게시·게시 중단 전이·관리자 권한·메인 5개 상한 | `TST-E2-CUR-001` | `E2-T06` |
| `FR-CURATION-004` | 게시 항목만 공개·비공개 맛집 제외·안정 순서 | `TST-E2-CUR-001` | `E2-T07` |
| `FR-SUBMISSION-001` | 유형별 입력·열린 지문 중복·제보/신고 합산 일일 5건 | `TST-E2-SUB-001` | `E2-T08` |
| `FR-SUBMISSION-002` | 본인 목록·상세·회원 공개 사유·다른 회원 은닉 | `TST-E2-SUB-001` | `E2-T08` |
| `FR-SUBMISSION-003` | 관리자 상태 전이·승인과 실제 등록 분리·감사 이력 | `TST-E2-SUB-001`, `TST-E2-ATOMIC-001` | `E2-T09`, `E2-T11` |
| `FR-REPORT-001` | 대상 존재·신고 유형·열린 중복·합산 일일 5건 | `TST-E2-REP-001` | `E2-T08` |
| `FR-REPORT-002` | 본인 목록·상세·신고자 비노출·다른 회원 은닉 | `TST-E2-REP-001` | `E2-T08` |
| `FR-REPORT-003` | 관리자 상태 전이·자동 비공개 금지·감사 이력 | `TST-E2-REP-001`, `TST-E2-ATOMIC-001` | `E2-T09`, `E2-T11` |
| `FR-NOTIFICATION-001` | 상태·이력·알림 단일 트랜잭션·요청/상태 중복 방지 | `TST-E2-ATOMIC-001` | `E2-T11` |
| `FR-NOTIFICATION-002` | 본인 목록·페이지·정확한 미읽음 수·90일/200개 경계 | `TST-E2-NOT-001` | `E2-T10`, `E2-T14` |
| `FR-NOTIFICATION-003` | 본인 개별 읽음·멱등성·타 회원 은닉 | `TST-E2-NOT-001` | `E2-T10`, `E2-T14` |
| `FR-NOTIFICATION-004` | 전체 읽음 원자성·멱등성·정확한 미읽음 수 | `TST-E2-NOT-001` | `E2-T10`, `E2-T14` |

## 3. 비즈니스 규칙·NFR 교차 검증

| 테스트 묶음 | 적용 계약 | 필수 계층·증거 | 완료 Task |
|---|---|---|---|
| `TST-E2-COL-001` | `BR-COLLECTION-001~005` | 단위, MockMvc, PostgreSQL 동시성, 브라우저 소유권·빈 상태 | `E2-T03` |
| `TST-E2-POP-001` | `BR-POPULAR-001~003`, `NFR-PERFORMANCE-006`, `NFR-RELIABILITY-004` | PostgreSQL 실행계획·부하, 공개 API 계약, 변경 후 다음 조회 반영 | `E2-T04`, `E2-T05`, `E2-T15` |
| `TST-E2-CUR-001` | `BR-CURATION-001~004`, `NFR-OBSERVABILITY-004` | 관리자/공개 API, PostgreSQL 원자성, 브라우저 편집·공개 흐름 | `E2-T06`, `E2-T07` |
| `TST-E2-SUB-001` | `BR-SUBMISSION-001~004`, `NFR-SECURITY-006` | 입력 fuzz·rate limit·중복 동시성, 회원/관리자 API·브라우저 | `E2-T08`, `E2-T09`, `E2-T15` |
| `TST-E2-REP-001` | `BR-REPORT-001~004`, `NFR-SECURITY-006` | 입력 fuzz·rate limit·중복 동시성, 자동 비공개 부재, API·브라우저 | `E2-T08`, `E2-T09`, `E2-T15` |
| `TST-E2-NOT-001` | `BR-NOTIFICATION-002~004` | API 계약, 읽음 동시성, 알림함·`99+` 브라우저 표시 | `E2-T10`, `E2-T14` |
| `TST-E2-ATOMIC-001` | `BR-NOTIFICATION-001`, `NFR-INTEGRITY-005`, `NFR-RELIABILITY-004` | 상태·이력·알림 각 저장 지점 실패 주입과 전체 rollback | `E2-T11`, `E2-T15` |
| `TST-E2-LIFE-001` | `NFR-PRIVACY-005`, `ADR-DATA-012` | 시간 경계, 재실행, 부분 실패, 회원 탈퇴, 식별 제거 통합 테스트 | `E2-T02`, `E2-T10`, `E2-T15` |
| `TST-E2-SEC-001` | `NFR-SECURITY-006`, `NFR-TEST-005` | 회원/관리자 audience 교차 거부, 타 회원 자원, 악성 입력 회귀 | `E2-T15` |
| `TST-E2-PERF-001` | `NFR-PERFORMANCE-006`, `NFR-TEST-005`, `ADR-DATA-011` | 공개 조회 p95·쿼리 수·실행계획·대표 데이터 부하 증거 | `E2-T15` |
| `TST-E2-E2E-001` | 2차 확장 전체 FR·BR·NFR | 지원 브라우저·360px·접근성·V3 전진 migration·CI 전체 회귀 | `E2-T13`, `E2-T14`, `E2-T15` |

## 4. 범위 밖 검증

`FCM`, 이메일·웹 푸시, `DeviceToken`, `NotificationPreference`, 인기 Snapshot·Batch·Redis 캐시, 컬렉션 공유·직접 정렬, 큐레이션 예약 게시·추천 알고리즘은 현재 테스트 완료 조건이 아니다. 구현 흔적이 생기면 테스트를 추가하는 방식으로 정당화하지 않고 먼저 범위와 ADR을 변경한다.

## 5. E2-T15 시점 보류 검증 항목

`E2-T15`(#117)의 완료 조건은 "보안·성능·CI·운영 기준이 통과하고 **미완료·차단 항목이 명시된다**"이고, 같은 이슈가 "미결정 기술을 완료 조건으로 추가하지 않는다"를 함께 규정한다. 아래 두 항목은 그 규정에 따라 `E2-T15` 완료 판정에서 분리하고 후속 Task로 넘긴다. 3절의 계약 자체는 낮추지 않는다. 확정 기준(p95 500ms 이하, 오류율 1% 미만, 지원 브라우저 매트릭스)은 그대로 유지하고 **판정 시점만** 옮긴다.

| 보류 항목 | 소속 묶음 | 차단 사유 | 해제 조건 | 후속 |
|---|---|---|---|---|
| 정상 부하 50명·20 RPS p95·오류율 측정 | `TST-E2-PERF-001` | 자동 반복 실행 도구가 미결정이다. [ADR-PERF-001 k6 성능 테스트 체계](../07-adr/adr-backlog.md)가 백로그이며 활성화 조건인 k6 버전·CI 비용 승인이 아직 없다 | ADR-PERF-001 Accepted 후 운영 동급 환경에서 측정 | 후속 이슈 |
| 실단말·지원 브라우저 매트릭스(PC Chrome/Edge, Mobile Safari) | `TST-E2-E2E-001` | 실단말 수동 확인이 필요하며 자동화 수단이 확정돼 있지 않다 | 실단말 보유자 배정 후 수동 검증 | 후속 이슈 |

`E2-T15` 시점에 남긴 성능 회귀 방어선은 다음 세 가지다. 부하 측정을 보류하는 동안 회귀 탐지는 이 셋이 담당한다.

- `PublicCurationQueryCountApiTest`, `PopularRestaurantQueryCountApiTest` — 공개 조회 쿼리 수 상수 가드
- `CurationPublicQueryPlanPostgreSqlIntegrationTest` — PostgreSQL 실행계획(`loops=1`) 검증
- `PublicCurationPerformanceIntegrationTest` — 순차 내부 처리 latency 측정. CI 러너 편차로 인한 flaky를 피하려고 `@Disabled` 상태이며 수동 실행용이다
