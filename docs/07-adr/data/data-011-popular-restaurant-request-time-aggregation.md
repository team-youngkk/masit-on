---
id: ADR-DATA-011
title: 인기 맛집 요청 시점 실시간 집계
status: Accepted
decision_date: 2026-08-03
owners:
  - 양성훈
reviewers:
  - 박진영
related_requirements:
  - FR-POPULAR-001
  - BR-POPULAR-001
  - BR-POPULAR-002
  - BR-POPULAR-003
  - NFR-PERFORMANCE-006
  - NFR-RELIABILITY-004
related_documents:
  - ../../04-product/prd/discovery/popular-restaurants.md
  - ../../05-specs/api/discovery/popular-restaurant-api.md
  - ../../05-specs/data/second-expansion-data-contract.md
  - ../../02-analysis/second-expansion-workstreams.md
  - data-001-postgresql.md
  - ../adr-backlog.md
  - ../adr-index.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-DATA-011 인기 맛집 요청 시점 실시간 집계

## 1. 상태

Accepted

## 2. 결정 요약

인기 맛집은 요청마다 PostgreSQL의 현재 `favorite`와 공개·활성 `restaurant`를 집계해 상위 20개를 반환한다. 별도 Metric·Snapshot, Scheduler·Spring Batch, 이벤트 기반 카운터와 Redis 캐시는 도입하지 않는다.

## 3. 배경과 결정 문제

2차 확장의 인기 신호는 전체 기간의 현재 찜 관계 수 하나이며 찜 추가·해제·회원 탈퇴·맛집 공개 상태 변경의 커밋 뒤 다음 조회에 반영돼야 한다. 초기 데이터와 정상 부하에서 p95 500ms 이하를 만족해야 하지만, Snapshot이나 캐시가 필요하다는 측정 근거는 없다.

현재 사실을 즉시 반영하면서 집계 데이터의 갱신·재계산·복구 생명주기를 새로 만들지 않는 최소 구조가 무엇인지 결정한다.

## 4. 고려한 선택지

- 요청 시 PostgreSQL 실시간 집계
- 주기적 PopularitySnapshot과 Scheduler 또는 Spring Batch
- 찜 이벤트를 소비하는 비동기 집계 테이블
- Redis 카운터·정렬 집합 캐시

## 5. 결정

- `GET /api/restaurants/popular`는 현재 `favorite`를 `restaurant_id`로 집계하고 공개·활성 Restaurant만 남긴다.
- 현재 찜 1건 이상, `favoriteCount DESC, restaurantId ASC`, 최대 20개를 고정한다.
- `favorite(restaurant_id, member_id)` 역방향 인덱스를 V3에 추가한다.
- 집계 결과·순위·갱신 시각을 영속 저장하거나 Redis에 복제하지 않는다.
- DB 조회 실패 시 과거 결과를 정상 응답처럼 반환하지 않고 표준 서버 오류로 실패한다.
- 집계는 `favorite` 행의 존재만 판정하고 `member_account` 상태를 조인하지 않는다. `BR-POPULAR-003`이 말하는 "해당 트랜잭션"은 탈퇴에 대해 **찜 관계를 실제로 제거하는 정리 트랜잭션**이며, `DELETION_PENDING` 전이 트랜잭션이 아니다.

## 6. 선택 근거

현재 신호가 하나이고 결과가 20개뿐이라 SQL 집계와 인덱스만으로 요구사항을 직접 표현할 수 있다. Snapshot·이벤트·캐시는 staleness, 무효화, 재계산, 이중 쓰기와 운영 복구를 추가한다. 이를 감수해야 할 측정된 병목이 없으므로 최소 일관성 구조를 선택한다.

## 7. 트레이드오프

조회마다 집계 비용을 지불하며 Favorite가 크게 늘면 DB CPU와 정렬 비용이 병목이 될 수 있다. 대신 별도 데이터의 지연·불일치·재구축 문제가 없고 모든 찜 변경이 커밋 직후 자연스럽게 반영된다.

탈퇴만은 예외적으로 지연 창이 있다. `BR-MEMBER-004`가 탈퇴를 `DELETION_PENDING` 전이 → 찜·최근 기록 정리 → 물리 삭제의 다단계로 정의하므로, 탈퇴를 요청한 회원의 찜은 정리 트랜잭션이 커밋될 때까지 집계에 남는다. 현재 정리 주기가 `PT15M`이라 최대 지연은 약 15분이고 정리 재시도 중에는 더 길어질 수 있다.

이 지연을 감수하는 이유는 다음과 같다. 집계에 `member_account` 상태 조건을 넣으면 공개 조회가 회원 계정 상태를 판정 기준으로 삼게 되어 `BR-POPULAR-001`이 정한 "현재 유지 중인 찜 관계 수 하나"라는 신호 정의가 둘로 늘어난다. 또한 `favorite` 행이 남아 있는데 집계에서만 빠지는 상태가 생겨, 회원 목록·상태 조회와 인기 집계가 서로 다른 "현재 찜"을 보게 된다. 탈퇴의 정합성은 `BR-FAVORITE-004`대로 Command·FK 정책이 책임지고 공개 조회는 그 결과만 읽는다.

지연 창을 줄여야 하면 정리 주기 단축이나 탈퇴 요청 트랜잭션에서의 찜 정리로 해결하며, 인기 집계 SQL에 회원 상태 조건을 추가하지 않는다.

## 8. 강제 규칙

- 조회 수·최근 기록·비로그인 행동과 기간 가중치를 섞지 않는다.
- 성능 측정 없이 Snapshot, Batch, 이벤트 소비자, Redis 캐시를 추가하지 않는다.
- 인기 조회가 Favorite 원본을 변경하거나 조회 중 집계 테이블을 갱신하지 않는다.
- 쿼리 변경은 안정 정렬과 공개 상태 조건의 회귀 테스트를 동반한다.

## 9. 구현·운영 영향

WS-10이 집계 Projection과 실행계획을 소유하고 WS-06 Favorite 원본 계약은 변경하지 않는다. 별도 작업 스케줄, 재계산 운영 절차, 캐시 장애 정책과 신규 인프라는 없다.

## 10. 검증 방법

- 찜·찜 해제·탈퇴·공개 상태 변경 커밋 뒤 다음 조회 결과를 통합 테스트한다. 탈퇴는 찜 관계가 제거되는 정리 시점을 기준으로 검증한다.
- 동점 ID 보조 정렬, 최소 1건과 상위 20개 경계를 검증한다.
- 정상 부하 50명·20 RPS에서 서버 내부 p95 500ms 이하와 오류율 1% 미만을 확인한다.
- 대표 데이터에서 `EXPLAIN (ANALYZE, BUFFERS)`로 반복 전체 스캔 병목과 인덱스 사용 가능성을 점검한다.

## 11. 재검토 조건

운영 유사 데이터에서 쿼리·인덱스 최적화 뒤에도 NFR을 반복 위반하거나 DB 부하가 다른 핵심 API를 침해하면 Snapshot·이벤트 집계·Redis 캐시를 새 ADR로 비교한다. 허용 staleness, 재구축, 무효화, 장애 fallback과 비용을 먼저 승인해야 한다.

## 12. 관련 문서

- [인기 맛집 API](../../05-specs/api/discovery/popular-restaurant-api.md)
- [2차 확장 데이터 계약](../../05-specs/data/second-expansion-data-contract.md)
- [ADR-CACHE-001](../adr-backlog.md#adr-cache-001-redis-캐시-도입)
