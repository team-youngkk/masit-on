-- perf/seed/05-visit.sql
-- NFR-PERFORMANCE-006 부하 테스트용 기준 데이터 시드 (5/9).
-- visit 10,000건을 넣는다 (RV-NFR-002 확정 건수).
--
-- 선행 조건: 04-video.sql까지 실행됨.
--
-- 제약 조건(스키마 사실 4번):
--   - 복합 FK (video_id, creator_id) -> video(id, creator_id): visit.creator_id는 그
--     video의 creator_id와 정확히 같아야 한다.
--   - uk_visit__restaurant_creator_video (restaurant_id, creator_id, video_id) UNIQUE:
--     10,000건을 중복 없이 조합해야 한다.
--
-- (restaurant_idx, video_idx) 쌍을 중복 없이 10,000개 만드는 방법:
--   k = 0..9999
--   restaurant_idx = (k % 1000) + 1                       -- 1..1000 순환
--   lap            = k / 1000                              -- 0..9 (정수 나눗셈)
--   video_idx      = ((k + lap * 37) % 5000) + 1            -- 1..5000
-- 증명: 같은 restaurant_idx를 갖는 두 값 k1, k2(k1<k2)는 k2-k1 = 1000*d (d=1..9)를
-- 만족해야 한다. 이때 video_idx 차이는 (1000*d + 37*d) mod 5000 = 1037*d mod 5000이다.
-- gcd(1037, 5000) = 1이므로 d=1..9(즉 5000의 배수가 아닌 범위)에서 1037*d mod 5000은
-- 0이 될 수 없다. 따라서 같은 restaurant_idx를 가진 두 행은 video_idx가 항상 달라
-- (restaurant_idx, video_idx) 쌍이 k 전체 구간(0~9999)에서 유일하다. video_idx가
-- creator_idx를 유일하게 결정하므로(아래) 3중 조합도 유일하다.
--
-- creator_idx = ((video_idx - 1) % 200) + 1는 04-video.sql이 영상에 크리에이터를
-- 배분한 것과 동일한 식이라 FK가 항상 성립한다.
--
-- 모든 행을 publication_status='PUBLIC', lifecycle_status='ACTIVE'로 둔다. 이유는
-- 02-restaurant.sql과 동일: 부하 측정 대상 partial index(ix_visit__* 등)가
-- PUBLIC+ACTIVE 조건이라 시드도 그 조건을 만족해야 실제로 그 인덱스를 탄다.

INSERT INTO visit (
    id, restaurant_id, creator_id, video_id, publication_status, lifecycle_status,
    created_at, updated_at, deleted_at
)
SELECT
    (regexp_replace(md5('perf-seed-visit-' || base.k::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    (regexp_replace(md5('perf-seed-restaurant-' || base.restaurant_idx::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    (regexp_replace(md5('perf-seed-creator-' || base.creator_idx::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    (regexp_replace(md5('perf-seed-video-' || base.video_idx::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    'PUBLIC',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL
FROM (
    SELECT
        k,
        (k % 1000) + 1 AS restaurant_idx,
        video_idx,
        ((video_idx - 1) % 200) + 1 AS creator_idx
    FROM (
        SELECT
            k,
            ((k + (k / 1000) * 37) % 5000) + 1 AS video_idx
        FROM generate_series(0, 9999) AS k
    ) AS with_video
) AS base
ON CONFLICT (id) DO NOTHING;
