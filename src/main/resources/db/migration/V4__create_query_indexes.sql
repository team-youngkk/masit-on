-- V4: 초기 조회 인덱스
-- 근거: docs/05-specs/data/index-strategy.md 2절 (선행 V2, V3)

CREATE INDEX ix_restaurant__public_order
    ON restaurant (name COLLATE "C", road_address COLLATE "C", id)
    INCLUDE (region_id, food_category_id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE';

CREATE INDEX ix_restaurant__public_region_order
    ON restaurant (region_id, name COLLATE "C", road_address COLLATE "C", id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE';

CREATE INDEX ix_restaurant__public_category_order
    ON restaurant (food_category_id, name COLLATE "C", road_address COLLATE "C", id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE';

CREATE INDEX ix_creator__public_name
    ON creator (channel_name COLLATE "C", id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE'
        AND external_availability_status = 'AVAILABLE';

CREATE INDEX ix_video__creator
    ON video (creator_id)
    WHERE creator_id IS NOT NULL;

CREATE INDEX ix_visit__creator_restaurant
    ON visit (creator_id, restaurant_id, video_id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE';

CREATE INDEX ix_visit__restaurant_creator
    ON visit (restaurant_id, creator_id, video_id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE';

CREATE INDEX ix_visit__video
    ON visit (video_id, creator_id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE';

CREATE INDEX ix_confirmation_token__admin_issued
    ON confirmation_token (admin_account_id, issued_at DESC);

CREATE INDEX ix_confirmation_token__cleanup_issued
    ON confirmation_token (expires_at)
    WHERE status = 'ISSUED';

CREATE INDEX ix_confirmation_token__cleanup_completed
    ON confirmation_token (completed_at)
    WHERE status IN ('CREATED', 'DUPLICATE');
