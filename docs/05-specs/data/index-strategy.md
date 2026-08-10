---
status: accepted
related_documents:
  - physical-data-model.md
  - table-definitions.md
  - constraint-mapping.md
  - ../api/discovery/restaurant-discovery-api.md
  - ../api/discovery/creator-discovery-api.md
  - ../api/detail/restaurant-detail-api.md
  - second-expansion-data-contract.md
  - ../../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md
---

# 맛잇온 인덱스 전략

## 1. 원칙

- 유일 제약이 만든 인덱스를 재사용한다.
- FK 자식 컬럼은 부모 삭제·조인 비용을 위해 별도 인덱스를 검토한다.
- 공개 조회의 고정 상태 조건은 partial index로 줄인다.
- 인덱스는 API 쿼리와 `EXPLAIN (ANALYZE, BUFFERS)` 근거가 있어야 추가한다.
- 작은 기준 테이블에는 PK·UK 외 일반 인덱스를 만들지 않는다.
- 쓰기 비용과 VACUUM 부담을 고려해 가능성만 있는 인덱스를 선제 생성하지 않는다.

## 2. 초기 필수 인덱스

PK·UK 인덱스를 제외하고 다음을 초기 스키마 baseline에서 만든다.

| 이름 | 정의 요약 | 지원 쿼리 |
|---|---|---|
| `ix_restaurant__public_order` | `(name COLLATE "C", road_address COLLATE "C", id) INCLUDE (region_id, food_category_id) WHERE publication_status='PUBLIC' AND lifecycle_status='ACTIVE'` | 기본 목록·안정 정렬 |
| `ix_restaurant__public_region_order` | `(region_id, name COLLATE "C", road_address COLLATE "C", id) WHERE 공개·ACTIVE` | 자치구 필터 |
| `ix_restaurant__public_category_order` | `(food_category_id, name COLLATE "C", road_address COLLATE "C", id) WHERE 공개·ACTIVE` | 카테고리 필터 |
| `ix_creator__public_name` | `(channel_name COLLATE "C", id) WHERE PUBLIC·ACTIVE·AVAILABLE` | Creator 선택 목록 |
| `ix_video__creator` | `(creator_id) WHERE creator_id IS NOT NULL` | Creator별 Video, FK 보조 |
| `ix_visit__creator_restaurant` | `(creator_id, restaurant_id, video_id) WHERE PUBLIC·ACTIVE` | Creator 필터의 맛집 후보 |
| `ix_visit__restaurant_creator` | `(restaurant_id, creator_id, video_id) WHERE PUBLIC·ACTIVE` | 목록 방문자 집계·상세 |
| `ix_visit__video` | `(video_id, creator_id) WHERE PUBLIC·ACTIVE` | Video 상태 영향 조회, 복합 FK 보조 |
| `ix_confirmation_token__admin_issued` | `(admin_account_id, issued_at DESC)` | 관리자 Token 운영 조회 |
| `ix_confirmation_token__cleanup_issued` | `(expires_at) WHERE status='ISSUED'` | 만료 미사용 Token 지연 정리 |
| `ix_confirmation_token__cleanup_completed` | `(completed_at) WHERE status IN ('CREATED','DUPLICATE')` | 완료 Token 24시간 후 정리 |

Restaurant 상세는 PK 조회로 충분하다. Region과 Category FK의 자식 인덱스는 위 필터 인덱스가 선두 컬럼으로 포함해 보조한다.

## 3. 검색과 정렬

맛집 이름 검색은 대소문자를 구분하지 않는 부분 일치 `lower(name) LIKE '%' || lower(:query) || '%'`다. 선행 wildcard는 일반 B-tree를 사용할 수 없지만 초기 맛집 1,000개 규모에서는 순차 스캔 비용을 수용하고 `pg_trgm`과 GIN을 활성화하지 않는다.

다음 조건이 모두 충족될 때 별도 ADR/마이그레이션으로 trigram 인덱스를 검토한다.

1. 운영 유사 데이터로 검색 p95 800ms 기준을 반복해서 초과한다.
2. 실행계획에서 Restaurant 부분 검색이 주 병목이다.
3. `pg_trgm` 확장 운영 허용과 RDS 지원 여부를 확인했다.

후보는 다음과 같으며 현재 DDL에는 포함하지 않는다.

```sql
CREATE INDEX ... ON restaurant
USING gin (lower(name) gin_trgm_ops)
WHERE publication_status='PUBLIC' AND lifecycle_status='ACTIVE';
```

기본 정렬은 `ORDER BY name COLLATE "C", road_address COLLATE "C", id`로 고정한다. 동일 표현의 인덱스를 사용하고 애플리케이션·테스트·운영 DB에서 다른 collation을 암묵적으로 사용하지 않는다.

## 4. 복합 필터

지역+카테고리 조합 전용 인덱스는 초기 생성하지 않는다. 두 단일 필터 인덱스 중 선택도가 높은 것을 사용하거나 bitmap AND가 가능한지 실행계획으로 확인한다. 실제 빈도가 높고 성능 기준을 넘으면 `(region_id, food_category_id, name, road_address, id)` partial index를 추가한다.

Creator 필터는 `visit`에서 고유 Restaurant ID를 구한 뒤 Restaurant의 공개 상태와 추가 필터를 결합한다. 중복 제거를 위해 `SELECT DISTINCT restaurant_id` 또는 `EXISTS`를 사용하고, JPA 연관 컬렉션 전체 로딩으로 구현하지 않는다.

## 5. 1차 확장 인덱스

아래 인덱스는 각각의 테이블·열 추가와 함께 `V2__add_expansion_1_schema.sql`(V3~V6 구간, [마이그레이션 계획](migration-plan.md#9-1차-확장-마이그레이션-구성-통합-이전-구간별-기록))에서 생성한다. 기존 V1 인덱스는 수정하지 않는다.

| 이름 | 정의 요약 | 지원 쿼리 |
|---|---|---|
| `ix_favorite__member_favorited` | `(member_id, favorited_at DESC, restaurant_id)` | 현재 회원 찜 목록의 최신순·안정 정렬 |
| `ix_recent_restaurant_view__member_viewed` | `(member_id, last_viewed_at DESC, restaurant_id)` | 최근 본 목록의 최신순·안정 정렬과 50건 초과 정리 대상 선택 |
| `ix_recent_restaurant_view__cleanup_viewed` | `(last_viewed_at)` | 주기 cleanup Command의 30일 경과 기록 범위 삭제 |
| `ix_restaurant__public_coordinate_bounds` | `(latitude, longitude) WHERE publication_status='PUBLIC' AND lifecycle_status='ACTIVE' AND latitude IS NOT NULL AND longitude IS NOT NULL` | 과거 bounds 계약에서 생성된 적용 이력. 현재는 공개 지도 마커의 좌표 보유 후보 보조 인덱스이며 뷰포트 범위 조회를 계약하지 않는다. 실행계획 측정 없이 제거하지 않는다. |
| `ix_member_action_mail_outbox__dispatch` | `(status, next_attempt_at, created_at) WHERE status='PENDING'` | 잠금 가능한 메일 dispatch 후보 선택 |
| `ix_member_deletion_job__next_attempt` | `(next_attempt_at, requested_at)` | 탈퇴 정리 재시도 후보 선택 |
| `ix_member_session_revocation_recovery__next_attempt` | `(next_attempt_at, expires_at)` | `sid` 폐기 표식 보상 후보 선택 |

`favorite`와 `recent_restaurant_view`의 복합 PK는 각각 중복 찜 방지와 upsert 충돌 키를 제공한다. Creator 상세는 PK 한 건 조회이므로 V6 표시 열만을 위한 별도 인덱스를 만들지 않는다.

## 6. 2차 확장 인덱스

2차 확장 인덱스의 정확한 이름·열·partial 조건은 [2차 확장 데이터 계약](second-expansion-data-contract.md)을 따른다. 핵심 추가는 다음과 같다.

- `favorite(restaurant_id, member_id)` 역방향 인덱스로 실시간 인기 집계를 지원한다.
- 개인 컬렉션은 회원·최근 수정, 구성은 컬렉션·추가 최신순으로 인덱싱한다.
- 공개 큐레이션 메인 위치는 지연 가능한 상태·위치 unique, 구성 위치는 큐레이션별 unique로 강제한다.
- 제보·신고는 열린 중복 partial unique와 회원 목록·관리자 오래된 큐·식별 제거 인덱스를 분리한다.
- 알림은 요청·상태 partial unique, 회원 최신순·미읽음 partial·cleanup 인덱스를 둔다.
- 멱등 기록은 만료 시각 인덱스를 둔다.

## 7. 3차 확장 AI 영상 추출 인덱스

3차 확장 인덱스의 정확한 SQL은 [3차 확장 AI 영상 추출 데이터 계약](third-expansion-ai-video-data-contract.md)과 [`V4__create_third_expansion_ai_schema.sql`](../../../src/main/resources/db/migration/V4__create_third_expansion_ai_schema.sql)을 따른다.

| 인덱스 | 대상 경로 | 목적 |
|---|---|---|
| `ix_ai_job__claim` | `QUEUED`의 priority·created_at·id | 실시간 우선 Worker claim |
| `ix_ai_job__expired_lease_claim` | `RUNNING`의 lease_expires_at·priority·created_at·id | 만료 lease 복구 claim |
| `ix_ai_job__review` | `SUCCEEDED`의 created_at·id | 관리자 검수 목록 |
| `ix_ai_snapshot__review` | Snapshot review_status·created_at·id | 후보 검수 목록 |
| `ix_ai_tag_review__candidate` | Snapshot·candidate_tag_id·reviewed_at·id | 최신 태그 판단 이력 |
| `ix_visit_tag__tag_lookup` | tag_definition_id·visit_id | 태그 기반 공개 맛집 조회 |

멱등성·Snapshot·시도·채널 감시의 unique 제약은 보조 인덱스를 별도로 중복 생성하지 않는다. 실제 운영 성능은 Worker claim·공개 태그 조회와 3차 성능 Task의 실행계획·부하 결과로 검증한다.

## 8. 통계와 검증

- Flyway 적용 후 `ANALYZE`를 수동 DDL로 넣지 않는다. 테스트 fixture 적재 뒤 테스트가 명시적으로 `ANALYZE`한다.
- CI 성능 smoke test는 공개/비공개, 관계 없음, Creator당 복수 Video, Video당 복수 Restaurant를 포함한다.
- 핵심 쿼리는 예상 인덱스 이름만 단정하지 않고 결과·쿼리 수·상한 시간과 실행계획의 sequential scan 규모를 함께 검토한다.
- 초기 데이터가 작아 planner가 sequential scan을 고르는 것은 오류가 아니다.

## 9. 운영 점검

출시 후 `pg_stat_user_indexes`로 사용 횟수와 크기를 확인한다. 장기간 미사용 인덱스도 즉시 삭제하지 않고 쿼리 빈도·FK 보조 역할을 확인한 뒤 전진 마이그레이션으로 제거한다. 인덱스 추가·제거는 운영 수동 DDL이 아니라 Flyway만 사용한다.
