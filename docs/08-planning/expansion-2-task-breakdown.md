---
status: Planned
plan_date: 2026-08-03
implementation_gate: Ready
related_documents:
  - expansion-2-implementation-plan.md
  - second-expansion-baseline-review.md
  - second-expansion-test-matrix.md
  - ../02-analysis/second-expansion-workstreams.md
  - ../04-product/traceability.md
  - ../05-specs/api-traceability.md
  - ../05-specs/data/data-traceability.md
  - ../07-adr/adr-traceability.md
---

# 맛잇온 2차 확장 최종 Task 분해

## 1. 사용 규칙

이 문서의 `E2-T*`는 2차 확장 구현·PR·검증 상태를 기록하는 최종 Task ID다. 상위 계약은 이 Task에서 임의로 바꾸지 않으며, 각 Task는 주 테스트 묶음과 완료 증거를 PR에 연결한 뒤 완료한다.

이 문서는 착수 전 계획과 담당자 사전 배정을 위한 Task 분해다. frontmatter의 `implementation_gate`는 [E2-T01](#e2-t01-기준선-확인)의 실행 증거 판정에 따라 2차 확장 구현 브랜치 생성·코드 작성·구현 PR의 착수 가능 여부를 통제한다.

2026-08-04 기준 회원·개인화 네 계약의 확정 상태 전환, 계약 전체 게이트 조건 2~6, 검증 참여자 쿠키 접근, 회원 찜·최근 본 목록과 관리자 네 등록 화면 렌더링, 사용자 확인에 따른 회원·관리자 전체 흐름, 기존 M2 복구 리허설 근거를 충족했다. 운영 지도에서도 새로고침 직후 `map-points` 요청 1건을 기준으로 이동·확대·축소 뒤 요청 수가 1건 그대로 유지되고 결과·선택 상태가 유지됨을 확인해 `implementation_gate`를 `Ready`로 전환한다.

현재 범위는 서비스 내 알림만 포함하므로 푸시 Adapter용 `E2-T12`는 생성하지 않는다. 번호는 향후 조건부 푸시 Task와 기존 후속 Task의 의미를 구분하기 위해 예약한다.

## 2. 전체 Task 표

| ID | Task | 담당자 / 기본 리뷰어 | 선행 | 병렬 | 주 테스트·완료 조건 |
|---|---|---|---|---|---|
| `E2-T01` | 1차 확장·운영 품질 기준선을 확인한다 | 이우람 / 김인안 | E1 완료 또는 명시적 선행 | 제한 | 회원·인증·찜, `E1-T11`~`E1-T12` 및 [OPS-VALIDATION](../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙) `E1-T13` 계약 정합화, ClassNotFoundException 품질 게이트, CI·M2 운영 선행 통과 |
| `E2-T02` | 공통 스키마·권한·생명주기를 구성한다 | 박진영 / 김인안 | `E2-T01` | 불가 | V2→V3·빈 DB migration, 제약·권한·멱등·보존 검증 |
| `E2-T03` | 개인 컬렉션 수직 슬라이스를 구현한다 | 박진영 / 김인안 | `E2-T02` | 가능 | `TST-E2-COL-001`; CRUD·소유권·중복·상한·비공개 맛집 검증 |
| `E2-T04` | 현재 찜 실시간 집계 기반을 구현한다 | 양성훈 / 박진영 | `E2-T02`, `E1-T05` | 가능 | `TST-E2-POP-001`; 전체 기간·현재 찜·공개 상태·동점·실행계획 검증 |
| `E2-T05` | 인기 맛집 API와 화면을 구현한다 | 양성훈 / 박진영 | `E2-T04` | 가능 | `TST-E2-POP-001`; 빈·정상·동점·상위 20·오류 화면 검증 |
| `E2-T06` | 관리자 큐레이션 관리 흐름을 구현한다 | 김인안 / 양성훈 | `E2-T02` | 가능 | `TST-E2-CUR-001`; 작성·완전 교체·정렬·게시/중단·감사 검증 |
| `E2-T07` | 공개 큐레이션 조회와 화면을 구현한다 | 김인안 / 양성훈 | `E2-T06` | 가능 | `TST-E2-CUR-001`; 게시 상태·순서·비공개/삭제 맛집·빈 상태 검증 |
| `E2-T08` | 사용자 제보·신고 접수를 구현한다 | 김인안 / 이우람 | `E2-T02` | 가능 | `TST-E2-SUB-001`, `TST-E2-REP-001`; 인증·중복·합산 제한·악성 입력 검증 |
| `E2-T09` | 관리자 검토·처리 흐름을 구현한다 | 김인안 / 이우람 | `E2-T08` | 불가 | 상태 전이·권한·감사 이력·승인과 실제 조치 분리 검증 |
| `E2-T10` | 서비스 내 알림 저장·조회·읽음 기반을 구현한다 | 이우람 / 김인안 | `E2-T02` | 가능 | `TST-E2-NOT-001`, `TST-E2-LIFE-001`; 본인 접근·미읽음·읽음·보존·탈퇴 검증 |
| `E2-T11` | 제보·신고 처리 결과를 알림에 연결한다 | 이우람 / 김인안 | `E2-T09`, `E2-T10` | 불가 | `TST-E2-ATOMIC-001`; 중복 알림 0건과 상태·이력·알림 원자성 검증 |
| `E2-T13` | 전체 화면과 사용자 여정을 통합한다 | 전원 / 상호 교차 리뷰 | `E2-T03`, `E2-T05`, `E2-T07`, `E2-T11` | 불가 | `TST-E2-E2E-001`; 탐색·개인화·참여·알림 브라우저 여정 검증 |
| `E2-T14` | 보안·성능·운영·회귀 검증을 완료한다 | 전원 / 상호 교차 리뷰 | `E2-T13` | 불가 | 전체 FR 추적, `TST-E2-SEC-001`, `TST-E2-PERF-001`, CI·운영 기준 통과 |

## 3. Task별 계약 경계

### E2-T01 기준선 확인

- [선행 상태 검토](second-expansion-baseline-review.md)의 회원·인증·찜·관리자·운영 조건과 `E1-T11` 지도, `E1-T12` 인증 코드, [OPS-VALIDATION](../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙)의 `E1-T13` 제한 공개 세션 정합화를 실제 실행 증거로 판정한다.
- 운영 복구 선행은 [M2 운영 프로비저닝 기록 13절](m2-provisioning-record.md#13-m2-13-복구-리허설-52)의 2026-07-30 RDS·Redis·직전 이미지 롤백·인스턴스 재기동 전체 리허설을 인용한다.
- 2026-08-04 운영 브라우저에서 Basic Auth 인증창 없이 검증 참여자 쿠키로 진입한 뒤 회원 찜·최근 본 목록과 관리자 맛집·유튜버·영상·Visit 네 등록 화면이 모두 렌더링됨을 직접 확인했다. 회원·관리자 Bearer를 사용하는 전체 기능 흐름과 지도 이동·확대·축소 전후 `map-points` 요청 1건 유지는 사용자가 확인했다.
- `E1-T01`의 백엔드 테스트가 class loading을 지나 실제 테스트를 수행해야 한다. 선행 미완료는 2차 기능 Task의 우회 구현으로 해소하지 않는다.

### E2-T02 공통 기반

- [2차 확장 데이터 계약](../05-specs/data/second-expansion-data-contract.md)과 [공통 API 계약](../05-specs/api/common/second-expansion-contract.md)을 구현한다.
- 기존 Flyway 파일을 수정하지 않고 V3, 회원/관리자 audience, 본인 자원 은닉, `idempotency_record`, 탈퇴·보존 cleanup을 검증한다.
- 공통 기반을 이유로 기능 Aggregate를 합치거나 새 최상위 공유 도메인을 만들지 않는다.

### E2-T03 개인 컬렉션

- `FR-COLLECTION-001~006`, `BR-COLLECTION-001~005`, [개인 컬렉션 API](../05-specs/api/personal/personal-collection-api.md)를 구현한다.
- 공유·공개·직접 정렬·메모·태그·이미지는 추가하지 않는다.

### E2-T04~E2-T05 인기 맛집

- [ADR-DATA-011](../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md)에 따라 기존 `favorite`를 요청 시점에 집계한다.
- `E2-T04`는 집계 Query·안정 정렬·공개 판정·실행계획을, `E2-T05`는 [인기 API](../05-specs/api/discovery/popular-restaurant-api.md)와 화면 상태를 완료한다.
- 지표 이벤트 수집, 최근 기간, 조회 신호, Snapshot·재계산·Batch·Redis는 금지한다.

### E2-T06~E2-T07 큐레이션

- `E2-T06`은 관리자 편집·게시와 감사 이력, `E2-T07`은 게시 중인 공개 조회와 화면을 담당한다.
- 예약 공개·종료 기간은 없고 `PUBLISHED/DRAFT` 상태를 사용한다. 인기와 같은 화면이면 표시 계약만 맞추고 저장 책임은 공유하지 않는다.

### E2-T08~E2-T09 제보·신고

- `E2-T08`은 회원 접수·본인 조회·중복·합산 일일 5건·입력 보안을 구현한다.
- `E2-T09`은 관리자 상태 전이와 감사 이력, `ACCEPTED`와 실제 조치 뒤 `COMPLETED`의 분리를 구현한다. 신고 수만으로 자동 비공개하지 않는다.

### E2-T10~E2-T11 서비스 내 알림

- `E2-T10`은 [알림 API](../05-specs/api/notification/notification-api.md), 정확한 미읽음 수, 개별·전체 멱등 읽음, 90일/최근 200개와 탈퇴 정리를 구현한다.
- `E2-T11`은 [ADR-NOTIFY-002](../07-adr/integration/notify-002-in-app-notification-reliability.md)에 따라 관리자 상태·이력·알림을 한 트랜잭션으로 연결한다.
- 알림 설정·동의·해지, 이메일·웹 푸시·FCM·Outbox·DLQ는 구현하지 않는다.

### E2-T13~E2-T14 통합과 종료

- `E2-T13`은 지원 브라우저·360px·키보드 접근성·빈/오류/만료 상태를 포함한 전체 사용자 여정을 연결한다.
- `E2-T14`는 V3 migration, 권한·동시성·보존·실행계획·부하·운영 지표·전체 회귀와 네 추적표를 최종 판정한다.

## 4. E2-T12 생성 조건

현재 `E2-T12`는 존재하지 않는다. 다음 조건을 모두 만족할 때만 `푸시 전달 Adapter` Task를 추가한다.

1. Scope에 외부 푸시 채널과 사용자 가치가 승인된다.
2. 채널 동의·해지, Token 생명주기, 실패·재시도·전달 SLA가 요구사항과 API·데이터 계약에 추가된다.
3. `ADR-NOTIFY-001`과 필요한 외부 전달 신뢰성 ADR이 Accepted가 된다.
4. 비용·운영 담당자와 테스트 완료 조건이 승인된다.

## 5. 변경 통제

기능 PR에는 해당 `TST-E2-*`, 상위 계약 영향과 추적표 변경을 함께 포함한다. 제품 의미나 운영 정책이 미정이면 Task TODO로 넘기지 않고 Scope·요구사항·PRD·API·데이터·ADR을 먼저 확정한다.
