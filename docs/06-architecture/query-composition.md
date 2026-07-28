---
related_documents:
  - application-flow.md
  - transaction-boundaries.md
  - dependency-rules.md
  - diagrams/restaurant-detail-sequence.md
  - ../05-specs/api/detail/restaurant-detail-api.md
  - ../05-specs/api/discovery/restaurant-discovery-api.md
  - ../05-specs/data/data-model.md
  - ../07-adr/data/data-003-spring-data-jpa.md
---

# 조회 조합

## 1. 결정

맛집 상세 응답은 `orchestration.application.query.RestaurantDetailQueryService`가 조합한다. 이 서비스는 비즈니스 도메인이 아닌 **전용 Application Query 책임**이며 다음 두 출력 Port를 사용한다.

- `RestaurantDetailBaseQueryPort`: 공개 Restaurant 기본 정보
- `RestaurantDetailContentQueryPort`: 공개·유효 Visit를 기준으로 Creator·Video 표시 정보를 가져오는 읽기 Projection

Restaurant Domain 객체가 Visit, Creator, Video를 조회하지 않는다. Controller도 Repository를 직접 조합하지 않는다.

두 Port의 구현은 `orchestration.infrastructure.query`에 둔다. 이 Adapter는 여러 소유 테이블을 읽을 수 있지만 쓰기, Entity 상태 변경과 도메인 규칙 소유를 금지한다.

## 2. 대안 비교

| 대안 | 장점 | 문제 | 판단 |
|---|---|---|---|
| Restaurant Domain 객체가 조합 | 객체 하나로 보임 | 도메인 경계·프레임워크 독립성 위반 | 제외 |
| 각 도메인 Application Service를 순차 호출 | 소유권 명확 | 양방향 도메인 의존, 호출·쿼리 수 증가, 부분 결과 관리 복잡 | 상세 경로에서는 제외 |
| Restaurant Application Facade | Controller 단순 | Restaurant가 다른 도메인 조정까지 소유하는 것으로 오해 | 제외 |
| 전용 Orchestration Query Service + 읽기 Projection | Domain 쓰기 모델 분리, 한 곳에서 부분 실패·DTO 조합, N+1 통제 | DB 스키마와 읽기 모델 결합 | **선택** |
| 별도 CQRS 저장소 | 조회 최적화 | 동기화·운영 복잡도와 추가 인프라 | MVP 제외 |

## 3. 입력과 출력

```text
입력: RestaurantId

출력: RestaurantDetailResult
├─ RestaurantDetailBase
├─ ContentStatus
├─ List<VisitedCreatorView>
└─ List<RelatedVideoView>
```

`RestaurantDetailResult`는 Application 읽기 모델이다. JPA Entity, Domain Aggregate나 외부 DTO가 아니다. Presentation은 이를 API 응답 DTO로 단순 변환한다.

## 4. 조회 순서와 부분 실패

1. 기본 정보를 먼저 조회한다.
2. 기본 정보가 없거나 공개 대상이 아니면 `404`다.
3. 기본 정보 조회가 저장소 오류로 실패하면 전체 `500`이다.
4. 콘텐츠 Projection을 조회한다.
5. 콘텐츠가 없으면 `AVAILABLE`과 빈 배열을 반환한다.
6. 콘텐츠 조회가 실패하면 `TEMPORARILY_UNAVAILABLE`과 빈 배열을 반환한다.
7. 서로 다른 콘텐츠의 일부 성공값은 섞지 않는다.

이 흐름은 [맛집 상세 API](../05-specs/api/detail/restaurant-detail-api.md)의 확정된 부분 실패 계약을 그대로 구현한다.

## 5. Repository/Query Adapter 전략

### 기본 정보

Restaurant 테이블과 Restaurant 소유 참조 데이터(Region, FoodCategory)를 Projection으로 조회한다. Restaurant는 `PUBLIC`·`ACTIVE` 조건을 만족해야 하며 상세 API가 요구하는 필드만 선택한다.

### 콘텐츠

Visit를 기준으로 Creator와 Video를 조인해 다음 조건을 DB에서 먼저 적용한다.

- Visit `PUBLIC`·`ACTIVE`
- Creator `PUBLIC`·`ACTIVE` 및 외부 `AVAILABLE`
- Video `PUBLIC`·`ACTIVE` 및 외부 `AVAILABLE`
- 요청 Restaurant ID 일치

결과 Row는 한 Visit에 필요한 Creator·Video 표시 필드를 함께 가진다. Application이 다음을 수행한다.

- Creator ID 기준 중복 제거
- Video ID 기준 중복 제거
- 안정적인 정렬 적용
- API 목록으로 매핑

`visitedBy`는 `channelName`, 같은 이름은 Creator ID 오름차순으로 정렬한다. `videos`는 `title`, 같은 제목은 Video ID 오름차순으로 정렬한다. 게시일·방문일 최신순을 도입하지 않으며 DB의 우연한 반환 순서에 의존하지 않는다.

## 6. N+1과 쿼리 수

상세 정상 경로의 목표는 다음과 같다.

- 기본 정보: 1 query
- 콘텐츠: 1 query
- 총 2 query

각 Visit Row마다 Creator나 Video Repository를 다시 호출하지 않는다. Lazy 연관 탐색으로 응답을 만들지 않는다. 통합 테스트에서 쿼리 수를 단언한다.

목록 조회도 Restaurant마다 방문 Creator를 개별 조회하지 않는다. `EXISTS`, 집계 Projection 또는 제한된 별도 일괄 조회 중 실행 계획이 단순하고 성능 기준을 만족하는 방식을 사용한다. QueryDSL은 Conditional ADR이 활성화되기 전 선제 도입하지 않는다.

### 맛집 목록의 Creator 조건 조합

`RestaurantSearchQueryService`는 Creator·Visit 테이블을 직접 판정하지 않고 Visit 도메인의
`FindDistinctValidRestaurantIdsByCreatorQuery`를 호출한다. 이 공개 Query Port가 Creator
공개성과 공개·유효 Visit에 따른 중복 없는 Restaurant ID 후보 집합을 소유한다.

- `creatorId` 미지정: 후보 제한을 `null`로 전달해 Restaurant 전체를 검색한다.
- 공개되지 않았거나 존재하지 않는 Creator: `400 INVALID_FIELD_VALUE(creatorId)`다.
- 공개 Creator이지만 유효 후보 없음: 빈 집합을 전달해 `200`과 빈 목록을 반환한다.
- 후보 있음: Restaurant 검색 Adapter가 후보 ID를 이름·자치구·카테고리와 `AND`로 결합한 뒤
  안정 정렬과 페이지네이션을 적용한다.

Restaurant 검색 Adapter는 Visit 유효성이나 Creator 공개성을 다시 판정하지 않는다. 따라서
Visit 정책 변경은 Visit 도메인의 Query 구현 한 곳에 반영하고, Restaurant 목록 조합은 후보
집합과 자체 소유 필터 결합에만 책임을 둔다.

## 7. 페이징과 결과 크기

- 맛집 목록은 공통 페이지 계약(기본 20, 허용 10/20/50)을 따른다.
- 상세 API는 방문 Creator와 Video 전체를 반환하는 현재 계약이므로 내부 페이지네이션을 노출하지 않는다.
- 초기 데이터는 맛집 1,000개, 유튜버 200개, 영상 5,000개, 방문 관계 10,000개를 기준으로 한다. 맛집당 관계 상한은 두지 않고 성능 데이터셋에 관계가 많은 맛집을 포함한다.
- 단일 맛집의 관계 수가 p95 500ms 또는 응답 크기 기준을 위협한다면 API 분리·페이지네이션을 계약 변경으로 검토한다.

상세 내부에서 임의로 결과를 잘라 API 계약과 다른 응답을 만들지 않는다.

## 8. Command와 Query 분리 수준

MVP는 별도 물리 저장소가 없는 **코드 수준 CQRS**만 적용한다.

- Command: Domain 모델과 Repository Port로 불변 조건·상태 변경
- Query: 전용 Projection과 Application 읽기 DTO
- 동일 PostgreSQL, 동일 트랜잭션 관리와 동일 배포 단위 사용
- Query DTO를 Command 입력이나 Domain Entity로 재사용하지 않음
- 읽기 테이블·이벤트 동기화·별도 검색 엔진은 도입하지 않음

## 9. 일관성

등록 성공은 같은 PostgreSQL 커밋 직후 새 조회에 반영된다. 상세의 기본 정보와 콘텐츠 두 쿼리 사이에서 다른 트랜잭션이 커밋할 수 있으므로 두 결과가 완전히 같은 시점 Snapshot이라는 보장은 현재 요구하지 않는다.

다음이 필요해지면 별도 결정한다.

- 한 요청 안의 강한 Snapshot 일관성
- 대규모 읽기 복제본
- 비동기 Projection 동기화
- 캐시 무효화와 허용 staleness

## 10. 캐싱

MVP 상세 조회 캐시는 도입하지 않는다.

- Redis는 관리자 Refresh Token 저장 역할로 확정됐고 조회 캐시는 별도 활성화 조건이 필요하다.
- 기준 데이터 규모와 목표 부하는 확정했으며 캐시는 실제 반복 조회율과 DB 부하가 병목으로 확인될 때만 검토한다.
- 관리자 등록 직후 반영 요구와 캐시 무효화 규칙이 추가된다.

실측 병목이 생기면 캐시 키, TTL, 무효화, 장애 시 fallback과 Token Redis 역할 분리를 추가 ADR로 결정한다.

## 11. 테스트

- 관계 없음: `AVAILABLE`, 두 목록 빈 배열
- 영상 없는 유효 관계 또는 비공개 콘텐츠: API 계약에 맞는 필터 결과
- 중복 관계 Row: Creator·Video ID 기준 한 번만 반환
- 콘텐츠 Adapter 실패: 기본 정보 유지, `TEMPORARILY_UNAVAILABLE`
- 기본 Adapter 실패: 전체 500
- 공개·비공개 조합과 외부 이용 불가 상태
- 상세 정상 경로 쿼리 수 2회
- 외부 HTTP Client가 공개 조회에서 호출되지 않음
- p95 500ms 목표에 대한 통합·성능 검증
