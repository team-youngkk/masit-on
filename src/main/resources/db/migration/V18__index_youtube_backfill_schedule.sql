CREATE INDEX ix_youtube_backfill_latest_channel
    ON youtube_channel_backfill_run (creator_id, youtube_channel_id, updated_at DESC, id DESC);
