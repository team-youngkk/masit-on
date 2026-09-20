ALTER TABLE youtube_channel_backfill_run
    ADD COLUMN stop_reason varchar(32);

ALTER TABLE youtube_channel_backfill_run
    ADD CONSTRAINT ck_youtube_backfill_stop_reason
        CHECK (stop_reason IS NULL OR stop_reason IN ('MAX_VIDEOS_PER_RUN', 'MANUAL'));
