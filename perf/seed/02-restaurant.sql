-- perf/seed/02-restaurant.sql
-- NFR-PERFORMANCE-006 부하 테스트용 기준 데이터 시드 (2/9).
-- restaurant 1,000건을 넣는다 (RV-NFR-002 확정 건수).
--
-- 선행 조건: 01-admin-account.sql까지 실행됨. region 25건·food_category 10건은
-- V1__create_initial_schema.sql 289~328줄이 이미 넣어둔 기준 데이터이므로 이 파일에서
-- 새로 만들지 않고 고정 id를 그대로 참조한다.
--
-- id 결정 규칙: 01-admin-account.sql 상단 주석과 동일 (md5 재배열 방식, 결정론적).
-- restaurant 순번(1~1000)을 region/food_category 25종/10종에 순환 배분해 실제 탐색
-- 분포와 비슷하게 흩어놓는다.
--
-- 모든 행을 publication_status='PUBLIC', lifecycle_status='ACTIVE'로 둔다. 인기 맛집
-- 집계(GET /api/restaurants/popular)와 공개 큐레이션이 조회하는 대상이 PUBLIC+ACTIVE
-- 맛집이므로, 측정 대상 인덱스(ix_restaurant__public_order 등)를 실제로 타게 하려면
-- 시드 데이터도 그 조건을 만족해야 한다. PRIVATE/DELETED 혼합 분포는 이번 이슈의 측정
-- 대상이 아니라 넣지 않았다.

INSERT INTO restaurant (
    id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
    road_address, detail_address, phone_number, publication_status, lifecycle_status,
    created_at, updated_at, deleted_at, latitude, longitude
)
SELECT
    (regexp_replace(md5('perf-seed-restaurant-' || gs::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    ('10000000-0000-4000-8000-' || lpad((1 + ((gs - 1) % 25))::text, 12, '0'))::uuid,
    ('20000000-0000-4000-8000-' || lpad((1 + ((gs - 1) % 10))::text, 12, '0'))::uuid,
    '부하테스트 맛집 ' || gs,
    'PERF-SEED-RESTAURANT-' || lpad(gs::text, 6, '0'),
    'https://place.map.kakao.com/perf-seed-restaurant-' || gs,
    '서울특별시 부하테스트구 부하테스트로 ' || gs,
    NULL,
    '02-1000-' || lpad(gs::text, 4, '0'),
    'PUBLIC',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL,
    NULL,
    NULL
FROM generate_series(1, 1000) AS gs
ON CONFLICT (id) DO NOTHING;
