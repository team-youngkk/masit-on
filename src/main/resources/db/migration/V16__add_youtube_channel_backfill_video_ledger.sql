CREATE TABLE youtube_channel_backfill_video (
    run_id uuid NOT NULL REFERENCES youtube_channel_backfill_run(id) ON DELETE CASCADE,
    youtube_video_id varchar(128) NOT NULL,
    result varchar(16) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (run_id, youtube_video_id),
    CONSTRAINT ck_youtube_backfill_video_result CHECK (result IN ('SUBMITTED', 'REUSED')),
    CONSTRAINT ck_youtube_backfill_video_id_not_blank CHECK (btrim(youtube_video_id) <> '')
);

CREATE INDEX ix_youtube_backfill_video_run ON youtube_channel_backfill_video (run_id, created_at);
