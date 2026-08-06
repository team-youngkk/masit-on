-- perf/seed/08-curation.sql
-- NFR-PERFORMANCE-006 부하 테스트용 기준 데이터 시드 (8/9).
-- curation 5건(PUBLISHED, main_position 1~5) + curation_restaurant 큐레이션당 20건을
-- 넣는다. 둘 다 스키마 제약상 최대치다:
--   - uq_curation__status_main_position UNIQUE (publication_status, main_position)이고
--     main_position이 1~5로 제한돼(스키마 사실 11번) PUBLISHED는 최대 5건이다.
--   - uq_curation_restaurant__position UNIQUE (curation_id, position)이고 position이
--     1~20으로 제한돼(스키마 사실 12번) 큐레이션당 최대 20건이다.
--
-- 선행 조건: 01-admin-account.sql, 02-restaurant.sql까지 실행됨(07-favorite.sql까지의
-- 순서는 이 파일과 직접 의존 관계는 없지만 파일명 순서를 그대로 따른다).
--
-- created_by/updated_by는 admin_account FK라 01-admin-account.sql이 만든
-- 'perf-seed-admin' 계정을 그대로 참조한다(로그인 id로 서브쿼리 조회 — 그 계정의 id를
-- 이 파일에서 다시 md5로 계산하지 않아 두 파일 간 문자열이 어긋날 여지를 없앤다).
--
-- curation_restaurant는 큐레이션마다 서로 다른 맛집 20개를 쓴다: 1번 큐레이션은
-- restaurant 순번 1~20, 2번은 21~40, ... 5번은 81~100. (c-1)*20+p 식으로 계산하며
-- 1~100 범위는 02-restaurant.sql이 만든 1~1000 범위 안에 있다.

INSERT INTO curation (
    id, title, description, publication_status, main_position, created_by, updated_by,
    created_at, updated_at, published_at
)
SELECT
    (regexp_replace(md5('perf-seed-curation-' || gs::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    '부하테스트 큐레이션 ' || gs,
    '부하 측정용 큐레이션 설명 ' || gs,
    'PUBLISHED',
    gs,
    (SELECT id FROM admin_account WHERE login_id = 'perf-seed-admin'),
    (SELECT id FROM admin_account WHERE login_id = 'perf-seed-admin'),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM generate_series(1, 5) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO curation_restaurant (curation_id, restaurant_id, position, added_at)
SELECT
    (regexp_replace(md5('perf-seed-curation-' || c::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    (regexp_replace(md5('perf-seed-restaurant-' || ((c - 1) * 20 + p)::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    p,
    CURRENT_TIMESTAMP
FROM generate_series(1, 5) AS c
CROSS JOIN generate_series(1, 20) AS p
ON CONFLICT (curation_id, restaurant_id) DO NOTHING;
