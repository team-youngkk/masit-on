CREATE TABLE youtube_channel_backfill_run (
    id uuid NOT NULL PRIMARY KEY,
    creator_id uuid NOT NULL REFERENCES creator(id) ON DELETE RESTRICT,
    youtube_channel_id varchar(128) NOT NULL,
    status varchar(16) NOT NULL,
    lease_owner varchar(128),
    lease_expires_at timestamp(6) with time zone,
    page_token varchar(512),
    page_count integer NOT NULL DEFAULT 0,
    scanned_count bigint NOT NULL DEFAULT 0,
    submitted_count bigint NOT NULL DEFAULT 0,
    reused_count bigint NOT NULL DEFAULT 0,
    last_error_category varchar(64),
    created_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_youtube_backfill_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','STOPPED')),
    CONSTRAINT ck_youtube_backfill_channel_not_blank CHECK (btrim(youtube_channel_id) <> ''),
    CONSTRAINT ck_youtube_backfill_counts CHECK (page_count >= 0 AND scanned_count >= 0 AND submitted_count >= 0 AND reused_count >= 0),
    CONSTRAINT ck_youtube_backfill_lease_pair CHECK ((lease_owner IS NULL AND lease_expires_at IS NULL) OR (lease_owner IS NOT NULL AND btrim(lease_owner) <> '' AND lease_expires_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_youtube_backfill_active_creator ON youtube_channel_backfill_run (creator_id) WHERE status IN ('QUEUED','RUNNING');
CREATE INDEX ix_youtube_backfill_due ON youtube_channel_backfill_run (updated_at, id) WHERE status = 'QUEUED';
CREATE INDEX ix_youtube_backfill_expired_lease ON youtube_channel_backfill_run (lease_expires_at, id) WHERE status = 'RUNNING';
