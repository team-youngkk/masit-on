ALTER TABLE ai_candidate_snapshot
    ADD COLUMN registered_restaurant_id uuid,
    ADD COLUMN registered_creator_id uuid,
    ADD COLUMN registered_video_id uuid,
    ADD COLUMN registered_visit_id uuid,
    ADD COLUMN restaurant_created boolean,
    ADD COLUMN creator_created boolean,
    ADD COLUMN video_created boolean,
    ADD COLUMN visit_created boolean;

ALTER TABLE ai_candidate_snapshot
    ADD CONSTRAINT fk_ai_snapshot__registered_restaurant FOREIGN KEY (registered_restaurant_id)
        REFERENCES restaurant(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_ai_snapshot__registered_creator FOREIGN KEY (registered_creator_id)
        REFERENCES creator(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_ai_snapshot__registered_video FOREIGN KEY (registered_video_id)
        REFERENCES video(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_ai_snapshot__registered_visit FOREIGN KEY (registered_visit_id)
        REFERENCES visit(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT ck_ai_snapshot__registration_flags CHECK (
        (registered_restaurant_id IS NULL AND restaurant_created IS NULL)
        OR (registered_restaurant_id IS NOT NULL AND restaurant_created IS NOT NULL)
    ),
    ADD CONSTRAINT ck_ai_snapshot__registration_flags_creator CHECK (
        (registered_creator_id IS NULL AND creator_created IS NULL)
        OR (registered_creator_id IS NOT NULL AND creator_created IS NOT NULL)
    ),
    ADD CONSTRAINT ck_ai_snapshot__registration_flags_video CHECK (
        (registered_video_id IS NULL AND video_created IS NULL)
        OR (registered_video_id IS NOT NULL AND video_created IS NOT NULL)
    ),
    ADD CONSTRAINT ck_ai_snapshot__registration_flags_visit CHECK (
        (registered_visit_id IS NULL AND visit_created IS NULL)
        OR (registered_visit_id IS NOT NULL AND visit_created IS NOT NULL)
    );

ALTER TABLE ai_candidate_tag_review
    ADD COLUMN manual_tag_code varchar(64),
    ADD CONSTRAINT ck_ai_candidate_tag_review__manual_tag_code CHECK (
        manual_tag_code IS NULL OR btrim(manual_tag_code) <> ''
    );

CREATE TABLE ai_extraction_manual_review (
    id uuid NOT NULL PRIMARY KEY,
    snapshot_id uuid NOT NULL REFERENCES ai_candidate_snapshot(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    decision varchar(16) NOT NULL CHECK (decision IN ('CONFIRM','DISCARD','ROLLBACK')),
    previous_review_status varchar(24) NOT NULL CHECK (previous_review_status IN ('AUTO_CONFIRMED','AUTO_BLOCKED','AUTO_REJECTED')),
    reviewed_by uuid NOT NULL REFERENCES admin_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    reason varchar(1000) NOT NULL CHECK (btrim(reason) <> ''),
    reviewed_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_ai_manual_review__snapshot ON ai_extraction_manual_review(snapshot_id, reviewed_at DESC, id DESC);
