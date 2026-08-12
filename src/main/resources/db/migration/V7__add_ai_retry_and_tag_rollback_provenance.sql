ALTER TABLE ai_extraction_job
    ADD COLUMN retry_reason varchar(1000),
    ADD CONSTRAINT ck_ai_extraction_job__retry_reason
        CHECK (retry_reason IS NULL OR (source = 'ADMIN' AND btrim(retry_reason) <> ''));

ALTER TABLE visit_tag
    ADD COLUMN created_from_snapshot_id uuid,
    ADD CONSTRAINT fk_visit_tag__created_from_snapshot
        FOREIGN KEY (created_from_snapshot_id)
        REFERENCES ai_candidate_snapshot (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE INDEX ix_visit_tag__created_from_snapshot
    ON visit_tag (created_from_snapshot_id)
    WHERE created_from_snapshot_id IS NOT NULL;
