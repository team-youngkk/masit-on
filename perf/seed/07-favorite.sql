-- perf/seed/07-favorite.sql
-- NFR-PERFORMANCE-006 부하 테스트용 기준 데이터 시드 (7/9).
-- favorite 20,000건을 넣는다 (이번 이슈에서 팀이 구두 확정, 맛집당 평균 20건).
--
-- 선행 조건: 06-member-account.sql까지 실행됨.
--
-- 분포 설계 (균등 분포를 쓰지 않는 이유):
-- GET /api/restaurants/popular는 `ORDER BY count(*) DESC, r.id ASC`로 집계한다. 모든
-- 맛집이 똑같이 20건씩 찜을 받으면 실행계획·정렬 로직은 "동점 타이브레이커(id ASC)"만
-- 검증하게 되고, 실제로 count(*) 값이 갈라져야 하는 정렬·집계 자체는 검증되지 않는다.
-- 그래서 restaurant 순번(1~1000)을 3단계로 나눠 뚜렷한 편차를 준다.
--   순번 1~50    (상위 50개)  : 맛집당 200건 -> 50  * 200 = 10,000건 (평균의 10배)
--   순번 51~200  (다음 150개) : 맛집당 40건  -> 150 * 40  =  6,000건 (평균의 2배)
--   순번 201~1000(나머지 800개): 맛집당 5건  -> 800 * 5   =  4,000건 (평균의 0.25배)
--   합계 = 10,000 + 6,000 + 4,000 = 20,000건 (요구 건수와 정확히 일치)
-- 맛집당 최댓값(200)은 회원 수 상한(1,000, 스키마 사실 10번)보다 한참 낮아 상한을
-- 위반하지 않는다.
--
-- 맛집 r의 j번째(1-base) 찜을 받는 회원은 member_idx = ((r + j - 1) % 1000) + 1로 고른다.
-- 맛집마다 j=1..tc(r)를 순회하는 동안 member_idx는 서로 달라(mod 1000, tc(r) <= 200 < 1000)
-- pk_favorite(member_id, restaurant_id) 유일성을 자동으로 만족한다.

INSERT INTO favorite (member_id, restaurant_id, favorited_at)
SELECT
    (regexp_replace(md5('perf-seed-member-' || expanded.member_idx::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    (regexp_replace(md5('perf-seed-restaurant-' || expanded.r::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    CURRENT_TIMESTAMP - ((expanded.j || ' seconds')::interval)
FROM (
    SELECT
        tier.r,
        j,
        ((tier.r + j - 1) % 1000) + 1 AS member_idx
    FROM (
        SELECT
            gs AS r,
            CASE
                WHEN gs <= 50 THEN 200
                WHEN gs <= 200 THEN 40
                ELSE 5
            END AS tc
        FROM generate_series(1, 1000) AS gs
    ) AS tier
    CROSS JOIN LATERAL generate_series(1, tier.tc) AS j
) AS expanded
ON CONFLICT (member_id, restaurant_id) DO NOTHING;
