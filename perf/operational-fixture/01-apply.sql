-- #190 운영 직접 성능 검증 fixture 적재.
-- RUN_ID별 합성 맛집 25건, 회원 25건, 찜 500건, 공개 큐레이션 1건을 추가한다.
-- 기존 실제 행은 수정하지 않는다.

\set ON_ERROR_STOP on
\if :{?RUN_ID}
\else
\echo 'RUN_ID가 없어 운영 fixture 적재를 중단한다.'
\quit 3
\endif
\if :{?PRODUCTION_PERF_APPLY_APPROVED}
\else
\echo 'PRODUCTION_PERF_APPLY_APPROVED=true가 없어 운영 fixture 적재를 중단한다.'
\quit 3
\endif
\if :PRODUCTION_PERF_APPLY_APPROVED
\else
\echo 'PRODUCTION_PERF_APPLY_APPROVED=true가 아니어서 운영 fixture 적재를 중단한다.'
\quit 3
\endif

SELECT set_config('masiton.perf.run_id', :'RUN_ID', false);

BEGIN;
SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '60s';

DO $$
DECLARE
    run_id text := current_setting('masiton.perf.run_id');
BEGIN
    IF current_database() <> 'masiton' THEN
        RAISE EXCEPTION '운영 DB 이름이 masiton이 아니다: %', current_database();
    END IF;
    IF run_id !~ '^[0-9]{8}$' THEN
        RAISE EXCEPTION 'RUN_ID는 YYYYMMDD 형식의 숫자 8자리여야 한다: %', run_id;
    END IF;
END
$$;

INSERT INTO member_account (
    id, email, password_hash, email_verified_at, status, deletion_requested_at,
    created_at, updated_at
)
SELECT
    (regexp_replace(md5('perf-op-member-' || :'RUN_ID' || '-' || gs::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    'perf-op-' || :'RUN_ID' || '-' || lpad(gs::text, 3, '0') || '@example.invalid',
    'perf-op-not-a-real-hash-placeholder',
    CURRENT_TIMESTAMP,
    'ACTIVE',
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM generate_series(1, 25) AS gs;

INSERT INTO restaurant (
    id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
    road_address, detail_address, phone_number, publication_status, lifecycle_status,
    created_at, updated_at, deleted_at, latitude, longitude
)
SELECT
    (regexp_replace(md5('perf-op-restaurant-' || :'RUN_ID' || '-' || gs::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    ('10000000-0000-4000-8000-' || lpad((1 + ((gs - 1) % 25))::text, 12, '0'))::uuid,
    ('20000000-0000-4000-8000-' || lpad((1 + ((gs - 1) % 10))::text, 12, '0'))::uuid,
    '성능검증 맛집 ' || :'RUN_ID' || '-' || lpad(gs::text, 3, '0'),
    'PERF-OP-' || :'RUN_ID' || '-RESTAURANT-' || lpad(gs::text, 3, '0'),
    'https://place.map.kakao.com/perf-op-' || :'RUN_ID' || '-' || gs,
    '서울특별시 성능검증구 성능검증로 ' || gs,
    NULL,
    '02-1000-' || lpad(gs::text, 4, '0'),
    'PUBLIC',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL,
    CASE WHEN gs <= 5 THEN 37.497500 + ((gs - 1) * 0.000100) ELSE NULL END,
    CASE WHEN gs <= 5 THEN 127.027600 + ((gs - 1) * 0.000100) ELSE NULL END
FROM generate_series(1, 25) AS gs;

INSERT INTO curation (
    id, title, description, publication_status, main_position,
    created_by, updated_by, created_at, updated_at, published_at
)
SELECT
    (regexp_replace(md5('perf-op-curation-' || :'RUN_ID'),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    '성능검증 큐레이션 ' || :'RUN_ID',
    '검증 참여자 전용 운영 성능 측정 합성 큐레이션',
    'PUBLISHED',
    available_position.position,
    admin.id,
    admin.id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (SELECT id FROM admin_account WHERE active = true ORDER BY id LIMIT 1) AS admin
CROSS JOIN LATERAL (
    SELECT position
      FROM generate_series(1, 5) AS positions(position)
     WHERE NOT EXISTS (
         SELECT 1 FROM curation
          WHERE publication_status = 'PUBLISHED'
            AND main_position = positions.position
     )
     ORDER BY position
     LIMIT 1
) AS available_position;

DO $$
DECLARE
    run_id text := current_setting('masiton.perf.run_id');
    fixture_curation_id uuid;
BEGIN
    fixture_curation_id := (regexp_replace(md5('perf-op-curation-' || run_id),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid;
    IF NOT EXISTS (
        SELECT 1
          FROM curation
         WHERE id = fixture_curation_id
           AND title = '성능검증 큐레이션 ' || run_id
    ) THEN
        RAISE EXCEPTION '합성 큐레이션 생성이 경쟁으로 실패해 적재를 중단한다.';
    END IF;
END
$$;

INSERT INTO curation_restaurant (curation_id, restaurant_id, position, added_at)
SELECT
    (regexp_replace(md5('perf-op-curation-' || :'RUN_ID'),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    restaurant.id,
    row_number() OVER (ORDER BY restaurant.kakao_place_id),
    CURRENT_TIMESTAMP
FROM restaurant
WHERE restaurant.kakao_place_id LIKE 'PERF-OP-' || :'RUN_ID' || '-RESTAURANT-%'
ORDER BY restaurant.kakao_place_id
LIMIT 20;

INSERT INTO favorite (member_id, restaurant_id, favorited_at)
SELECT
    (regexp_replace(md5('perf-op-member-' || :'RUN_ID' || '-' || member_idx::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    (regexp_replace(md5('perf-op-restaurant-' || :'RUN_ID' || '-' || restaurant_idx::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    CURRENT_TIMESTAMP - ((favorite_idx || ' seconds')::interval)
FROM generate_series(1, 25) AS restaurant_idx
CROSS JOIN generate_series(1, 20) AS favorite_idx
CROSS JOIN LATERAL (
    SELECT ((restaurant_idx + favorite_idx - 1) % 25) + 1 AS member_idx
) AS members;

ANALYZE restaurant;
ANALYZE favorite;
ANALYZE member_account;
ANALYZE curation;
ANALYZE curation_restaurant;

COMMIT;
