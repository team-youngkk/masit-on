-- perf/seed/03-creator.sql
-- NFR-PERFORMANCE-006 부하 테스트용 기준 데이터 시드 (3/9).
-- creator 200건을 넣는다 (RV-NFR-002 확정 건수).
--
-- 선행 조건: 02-restaurant.sql까지 실행됨(순서상 독립이지만 파일명 순서를 그대로 따른다).
--
-- external_status_checked_at은 NOT NULL이고 DB 기본값이 없다(스키마 사실 2번)이므로
-- 반드시 명시한다. external_channel_id는 04-video.sql의 publisher_external_channel_id가
-- 그대로 참조하는 복합 FK 대상이라(스키마 사실 3번) 여기서 만든 문자열 형식을
-- 04-video.sql에서 동일한 규칙으로 재생성해 맞춰야 한다.

INSERT INTO creator (
    id, external_channel_id, channel_name, channel_url,
    publication_status, lifecycle_status, external_availability_status, external_status_checked_at,
    created_at, updated_at, deleted_at, profile_image_url, description, handle
)
SELECT
    (regexp_replace(md5('perf-seed-creator-' || gs::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    'PERF-SEED-CREATOR-' || lpad(gs::text, 4, '0'),
    '부하테스트 크리에이터 ' || gs,
    'https://www.youtube.com/channel/perf-seed-creator-' || gs,
    'PUBLIC',
    'ACTIVE',
    'AVAILABLE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL,
    NULL,
    NULL,
    NULL
FROM generate_series(1, 200) AS gs
ON CONFLICT (id) DO NOTHING;
