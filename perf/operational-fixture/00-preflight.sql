-- #190 운영 직접 성능 검증 fixture 사전 점검.
-- 읽기 전용이다. RUN_ID는 숫자형 실행 식별자로만 사용한다.

\set ON_ERROR_STOP on
\if :{?RUN_ID}
\else
\echo 'RUN_ID가 없어 운영 fixture 사전 점검을 중단한다.'
\quit 3
\endif

SELECT set_config('masiton.perf.run_id', :'RUN_ID', false);

DO $$
DECLARE
    run_id text := current_setting('masiton.perf.run_id');
BEGIN
    IF current_database() <> 'masiton' THEN
        RAISE EXCEPTION '운영 DB 이름이 masiton이 아니다: %', current_database();
    END IF;

    IF to_regclass('public.ai_candidate_snapshot') IS NULL THEN
        RAISE EXCEPTION 'V4 AI 스키마가 없어 운영 fixture를 적용할 수 없다.';
    END IF;

    IF run_id !~ '^[0-9]{8}$' THEN
        RAISE EXCEPTION 'RUN_ID는 YYYYMMDD 형식의 숫자 8자리여야 한다: %', run_id;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM restaurant
         WHERE kakao_place_id LIKE 'PERF-OP-' || run_id || '-%'
    ) OR EXISTS (
        SELECT 1
          FROM member_account
         WHERE email LIKE 'perf-op-' || run_id || '-%@example.invalid'
    ) OR EXISTS (
        SELECT 1
          FROM curation
         WHERE title = '성능검증 큐레이션 ' || run_id
    ) THEN
        RAISE EXCEPTION '같은 RUN_ID의 운영 fixture가 이미 존재한다: %', run_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM admin_account WHERE active = true
    ) THEN
        RAISE EXCEPTION '활성 관리자 계정이 없어 합성 큐레이션을 만들 수 없다.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM generate_series(1, 5) AS positions(position)
         WHERE NOT EXISTS (
             SELECT 1 FROM curation
              WHERE publication_status = 'PUBLISHED'
                AND main_position = positions.position
         )
    ) THEN
        RAISE EXCEPTION '공개 큐레이션 위치 1~5가 모두 사용 중이라 합성 큐레이션을 만들 수 없다.';
    END IF;
END
$$;

SELECT current_database() AS database_name,
       :'RUN_ID' AS run_id,
       (SELECT count(*) FROM restaurant) AS restaurant_count,
       (SELECT count(*) FROM member_account) AS member_count,
       (SELECT count(*) FROM admin_account WHERE active = true) AS active_admin_count,
       (SELECT count(*) FROM curation WHERE publication_status = 'PUBLISHED') AS published_curation_count;
