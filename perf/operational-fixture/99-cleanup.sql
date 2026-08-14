-- #190 운영 직접 성능 검증 fixture 정리.
-- 실제 참여자 참조가 있으면 전체를 중단한다. 자동으로 참여자 행을 삭제하지 않는다.

\set ON_ERROR_STOP on
\if :{?RUN_ID}
\else
\echo 'RUN_ID가 없어 운영 fixture 정리를 중단한다.'
\quit 3
\endif
\if :{?PRODUCTION_PERF_CLEANUP_APPROVED}
\else
\echo 'PRODUCTION_PERF_CLEANUP_APPROVED=true가 없어 운영 fixture 정리를 중단한다.'
\quit 3
\endif
\if :PRODUCTION_PERF_CLEANUP_APPROVED
\else
\echo 'PRODUCTION_PERF_CLEANUP_APPROVED=true가 아니어서 운영 fixture 정리를 중단한다.'
\quit 3
\endif

BEGIN;

SELECT set_config('masiton.perf.run_id', :'RUN_ID', true);
SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '30s';
-- idempotency_record.actor_id에는 FK가 없으므로 cleanup 전체 동안 신규 기록을 막는다.
LOCK TABLE idempotency_record IN SHARE MODE;
-- member_deletion_job.member_id에도 FK가 없으므로 같은 쓰기 공백 구간을 사용한다.
LOCK TABLE member_deletion_job IN SHARE MODE;

DO $$
DECLARE
    run_id text := current_setting('masiton.perf.run_id');
    fixture_restaurants uuid[];
    fixture_members uuid[];
    fixture_curation_id uuid;
    fixture_favorites integer;
    total_restaurant_favorites integer;
    total_member_favorites integer;
    expected_favorite_pairs integer;
    fixture_curation_restaurants integer;
BEGIN
    IF current_database() <> 'masiton' THEN
        RAISE EXCEPTION '운영 DB 이름이 masiton이 아니다: %', current_database();
    END IF;
    fixture_curation_id := (regexp_replace(md5('perf-op-curation-' || run_id),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid;
    SELECT array_agg(id) INTO fixture_restaurants
      FROM restaurant
     WHERE kakao_place_id LIKE 'PERF-OP-' || run_id || '-RESTAURANT-%';
    SELECT array_agg(id) INTO fixture_members
      FROM member_account
     WHERE email LIKE 'perf-op-' || run_id || '-%@example.invalid';
    IF coalesce(array_length(fixture_restaurants, 1), 0) <> 25
       OR coalesce(array_length(fixture_members, 1), 0) <> 25
       OR NOT EXISTS (
           SELECT 1
             FROM curation
            WHERE id = fixture_curation_id
              AND title = '성능검증 큐레이션 ' || run_id
       ) THEN
        RAISE EXCEPTION 'fixture 부모 건수가 예상과 달라 정리를 중단한다.';
    END IF;

    PERFORM 1 FROM member_account WHERE id = ANY(fixture_members) FOR UPDATE;
    PERFORM 1 FROM curation WHERE id = fixture_curation_id FOR UPDATE;
    PERFORM 1 FROM restaurant WHERE id = ANY(fixture_restaurants) FOR UPDATE;

    SELECT count(*) INTO fixture_favorites
      FROM favorite f
      JOIN restaurant r ON r.id = f.restaurant_id
      JOIN member_account m ON m.id = f.member_id
     WHERE r.id = ANY(fixture_restaurants)
       AND m.id = ANY(fixture_members);
    SELECT count(*) INTO total_restaurant_favorites
      FROM favorite
     WHERE restaurant_id = ANY(fixture_restaurants);
    SELECT count(*) INTO total_member_favorites
      FROM favorite
     WHERE member_id = ANY(fixture_members);
    SELECT count(*) INTO expected_favorite_pairs
      FROM favorite f
      JOIN (
          SELECT
              (regexp_replace(md5('perf-op-member-' || run_id || '-' || member_idx::text),
                  '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid AS member_id,
              (regexp_replace(md5('perf-op-restaurant-' || run_id || '-' || restaurant_idx::text),
                  '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid AS restaurant_id
            FROM generate_series(1, 25) AS restaurant_idx
            CROSS JOIN generate_series(1, 20) AS favorite_idx
            CROSS JOIN LATERAL (
                SELECT ((restaurant_idx + favorite_idx - 1) % 25) + 1 AS member_idx
            ) AS members
      ) AS expected ON expected.member_id = f.member_id
                  AND expected.restaurant_id = f.restaurant_id;
    IF fixture_favorites <> 500 OR total_restaurant_favorites <> 500
       OR total_member_favorites <> 500 OR expected_favorite_pairs <> 500 THEN
        RAISE EXCEPTION 'fixture favorite 쌍이 예상과 달라 정리를 중단한다: exact=%, restaurant_total=%, member_total=%, expected_pairs=%',
            fixture_favorites, total_restaurant_favorites, total_member_favorites, expected_favorite_pairs;
    END IF;

    SELECT count(*) INTO fixture_curation_restaurants
      FROM curation_restaurant
     WHERE curation_id = fixture_curation_id;
    IF fixture_curation_restaurants <> 20 THEN
        RAISE EXCEPTION 'fixture curation 관계 건수가 예상과 달라 정리를 중단한다: %', fixture_curation_restaurants;
    END IF;

    IF EXISTS (
           WITH expected AS (
               SELECT id, row_number() OVER (ORDER BY kakao_place_id) AS position
                 FROM restaurant
                WHERE kakao_place_id LIKE 'PERF-OP-' || run_id || '-RESTAURANT-%'
                ORDER BY kakao_place_id
                LIMIT 20
           )
           SELECT 1
             FROM curation_restaurant cr
             LEFT JOIN expected e ON e.id = cr.restaurant_id
            WHERE cr.curation_id = fixture_curation_id
              AND (e.id IS NULL OR cr.position <> e.position)
       )
       OR EXISTS (
           WITH expected AS (
               SELECT id, row_number() OVER (ORDER BY kakao_place_id) AS position
                 FROM restaurant
                WHERE kakao_place_id LIKE 'PERF-OP-' || run_id || '-RESTAURANT-%'
                ORDER BY kakao_place_id
                LIMIT 20
           )
           SELECT 1
             FROM expected e
            WHERE NOT EXISTS (
                SELECT 1
                  FROM curation_restaurant cr
                 WHERE cr.curation_id = fixture_curation_id
                   AND cr.restaurant_id = e.id
                   AND cr.position = e.position
            )
       ) THEN
        RAISE EXCEPTION 'fixture curation 관계가 예상 맛집·순서와 달라 정리를 중단한다.';
    END IF;

    IF EXISTS (
           SELECT 1
             FROM curation_restaurant
            WHERE restaurant_id = ANY(fixture_restaurants)
              AND curation_id <> fixture_curation_id
       )
       OR EXISTS (SELECT 1 FROM collection_restaurant WHERE restaurant_id = ANY(fixture_restaurants))
       OR EXISTS (SELECT 1 FROM recent_restaurant_view WHERE restaurant_id = ANY(fixture_restaurants))
       OR EXISTS (SELECT 1 FROM visit WHERE restaurant_id = ANY(fixture_restaurants))
       OR EXISTS (SELECT 1 FROM member_action_token WHERE member_id = ANY(fixture_members))
       OR EXISTS (SELECT 1 FROM personal_collection WHERE member_id = ANY(fixture_members))
       OR EXISTS (SELECT 1 FROM notification WHERE member_id = ANY(fixture_members))
       OR EXISTS (SELECT 1 FROM submission WHERE member_id = ANY(fixture_members))
       OR EXISTS (SELECT 1 FROM report WHERE member_id = ANY(fixture_members))
       OR EXISTS (SELECT 1 FROM idempotency_record WHERE actor_id = ANY(fixture_members))
       OR EXISTS (SELECT 1 FROM member_deletion_job WHERE member_id = ANY(fixture_members))
       OR EXISTS (SELECT 1 FROM ai_candidate_snapshot WHERE registered_restaurant_id = ANY(fixture_restaurants)) THEN
        RAISE EXCEPTION '실제 또는 추가 참조가 발견되어 정리를 중단한다.';
    END IF;
END
$$;

DELETE FROM curation_restaurant
 WHERE curation_id = (regexp_replace(md5('perf-op-curation-' || :'RUN_ID'),
     '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid;

DELETE FROM curation
 WHERE id = (regexp_replace(md5('perf-op-curation-' || :'RUN_ID'),
     '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid
   AND title = '성능검증 큐레이션 ' || :'RUN_ID';

DELETE FROM favorite
 WHERE restaurant_id IN (
     SELECT id FROM restaurant WHERE kakao_place_id LIKE 'PERF-OP-' || :'RUN_ID' || '-RESTAURANT-%'
 );

DELETE FROM restaurant
 WHERE kakao_place_id LIKE 'PERF-OP-' || :'RUN_ID' || '-RESTAURANT-%';

DELETE FROM member_action_token
 WHERE member_id IN (
     SELECT id FROM member_account WHERE email LIKE 'perf-op-' || :'RUN_ID' || '-%@example.invalid'
 );

DELETE FROM member_account
 WHERE email LIKE 'perf-op-' || :'RUN_ID' || '-%@example.invalid';

DO $$
DECLARE
    run_id text := current_setting('masiton.perf.run_id');
    fixture_members uuid[];
BEGIN
    SELECT array_agg((regexp_replace(md5('perf-op-member-' || run_id || '-' || gs::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid)
      INTO fixture_members
      FROM generate_series(1, 25) AS gs;
    IF EXISTS (
        SELECT 1
          FROM idempotency_record
         WHERE actor_id = ANY(coalesce(fixture_members, ARRAY[]::uuid[]))
    ) THEN
        RAISE EXCEPTION 'fixture 회원 삭제 후 멱등성 기록이 발견되어 정리를 롤백한다.';
    END IF;
    IF EXISTS (
        SELECT 1
          FROM member_deletion_job
         WHERE member_id = ANY(coalesce(fixture_members, ARRAY[]::uuid[]))
    ) THEN
        RAISE EXCEPTION 'fixture 회원 삭제 후 탈퇴 작업이 발견되어 정리를 롤백한다.';
    END IF;
END
$$;

COMMIT;

ANALYZE restaurant;
ANALYZE favorite;
ANALYZE member_account;
ANALYZE curation;
ANALYZE curation_restaurant;
