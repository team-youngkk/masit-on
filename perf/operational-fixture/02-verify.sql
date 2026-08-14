-- #190 운영 직접 성능 검증 fixture 검증. 읽기 전용이다.

\set ON_ERROR_STOP on
\if :{?RUN_ID}
\else
\echo 'RUN_ID가 없어 운영 fixture 검증을 중단한다.'
\quit 3
\endif

SELECT set_config('masiton.perf.run_id', :'RUN_ID', false);

DO $$
DECLARE
    run_id text := current_setting('masiton.perf.run_id');
    restaurant_count integer;
    member_count integer;
    favorite_count integer;
    coordinate_count integer;
    curation_count integer;
    curation_restaurant_count integer;
BEGIN
    SELECT count(*) INTO restaurant_count
      FROM restaurant
     WHERE kakao_place_id LIKE 'PERF-OP-' || run_id || '-RESTAURANT-%';
    SELECT count(*) INTO member_count
      FROM member_account
     WHERE email LIKE 'perf-op-' || run_id || '-%@example.invalid';
    SELECT count(*) INTO favorite_count
      FROM favorite f
      JOIN restaurant r ON r.id = f.restaurant_id
     WHERE r.kakao_place_id LIKE 'PERF-OP-' || run_id || '-RESTAURANT-%';
    SELECT count(*) INTO coordinate_count
      FROM restaurant
     WHERE kakao_place_id LIKE 'PERF-OP-' || run_id || '-RESTAURANT-%'
       AND latitude IS NOT NULL
       AND longitude IS NOT NULL;
    SELECT count(*) INTO curation_count
      FROM curation
     WHERE title = '성능검증 큐레이션 ' || run_id;
    SELECT count(*) INTO curation_restaurant_count
      FROM curation_restaurant cr
      JOIN curation c ON c.id = cr.curation_id
     WHERE c.title = '성능검증 큐레이션 ' || run_id;

    IF restaurant_count <> 25 OR member_count <> 25 OR favorite_count <> 500 OR coordinate_count <> 5
       OR curation_count <> 1 OR curation_restaurant_count <> 20 THEN
        RAISE EXCEPTION 'fixture 건수 불일치: restaurant=%, member=%, favorite=%, coordinate=%, curation=%, curation_restaurant=%',
            restaurant_count, member_count, favorite_count, coordinate_count,
            curation_count, curation_restaurant_count;
    END IF;
END
$$;

SELECT
    (SELECT count(*) FROM restaurant WHERE kakao_place_id LIKE 'PERF-OP-' || :'RUN_ID' || '-RESTAURANT-%') AS fixture_restaurants,
    (SELECT count(*) FROM member_account WHERE email LIKE 'perf-op-' || :'RUN_ID' || '-%@example.invalid') AS fixture_members,
    (SELECT count(*)
       FROM favorite f
       JOIN restaurant r ON r.id = f.restaurant_id
      WHERE r.kakao_place_id LIKE 'PERF-OP-' || :'RUN_ID' || '-RESTAURANT-%') AS fixture_favorites,
    (SELECT count(*) FROM restaurant
      WHERE kakao_place_id LIKE 'PERF-OP-' || :'RUN_ID' || '-RESTAURANT-%'
        AND latitude IS NOT NULL AND longitude IS NOT NULL) AS course_restaurants,
    (SELECT count(*) FROM restaurant
      WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE') AS public_active_restaurants,
    (SELECT count(*) FROM curation WHERE publication_status = 'PUBLISHED') AS published_curations;

SELECT id AS fixture_curation_id, title, main_position
  FROM curation
 WHERE title = '성능검증 큐레이션 ' || :'RUN_ID';

SELECT id AS course_restaurant_id, kakao_place_id
  FROM restaurant
 WHERE kakao_place_id LIKE 'PERF-OP-' || :'RUN_ID' || '-RESTAURANT-%'
   AND latitude IS NOT NULL
   AND longitude IS NOT NULL
 ORDER BY kakao_place_id;
