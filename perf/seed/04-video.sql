-- perf/seed/04-video.sql
-- NFR-PERFORMANCE-006 부하 테스트용 기준 데이터 시드 (4/9).
-- video 5,000건을 넣는다 (RV-NFR-002 확정 건수). 크리에이터 1인당 평균 25건이 되도록
-- creator 200명에게 순환 배분한다(creator_idx = ((영상 순번-1) % 200) + 1).
--
-- 선행 조건: 03-creator.sql까지 실행됨.
--
-- 복합 FK fk_video__creator_channel (creator_id, publisher_external_channel_id) ->
-- creator (id, external_channel_id) (스키마 사실 3번)을 만족시키려면 두 값이 같은
-- creator_idx로부터 나온 것이어야 한다. creator_id는 03-creator.sql이 쓴 것과 동일한
-- md5('perf-seed-creator-' || creator_idx) 식으로, publisher_external_channel_id는
-- 03-creator.sql이 쓴 것과 동일한 'PERF-SEED-CREATOR-' || lpad(creator_idx,4,'0') 문자열로
-- 다시 계산한다. 두 파일에서 이 두 식이 어긋나면 FK 위반으로 즉시 실패한다.
--
-- external_video_id는 varchar(32)라 짧게 'PERFSEEDVID' + 10자리로 만든다(총 21자,
-- 00-cleanup.sql의 정리 마커와도 일치).
--
-- external_status_checked_at은 NOT NULL이고 DB 기본값이 없다(스키마 사실 2번)이므로
-- 반드시 명시한다.

INSERT INTO video (
    id, creator_id, external_video_id, publisher_external_channel_id, title, source_url, thumbnail_url,
    published_at, publication_status, lifecycle_status, external_availability_status,
    external_status_checked_at, created_at, updated_at, deleted_at
)
SELECT
    (regexp_replace(md5('perf-seed-video-' || t.gs::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    (regexp_replace(md5('perf-seed-creator-' || t.creator_idx::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    'PERFSEEDVID' || lpad(t.gs::text, 10, '0'),
    'PERF-SEED-CREATOR-' || lpad(t.creator_idx::text, 4, '0'),
    '부하테스트 영상 ' || t.gs,
    'https://www.youtube.com/watch?v=perf-seed-video-' || t.gs,
    'https://i.ytimg.com/vi/perf-seed-video-' || t.gs || '/hqdefault.jpg',
    CURRENT_TIMESTAMP - ((t.gs || ' minutes')::interval),
    'PUBLIC',
    'ACTIVE',
    'AVAILABLE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL
FROM (
    SELECT gs, ((gs - 1) % 200) + 1 AS creator_idx
    FROM generate_series(1, 5000) AS gs
) AS t
ON CONFLICT (id) DO NOTHING;
