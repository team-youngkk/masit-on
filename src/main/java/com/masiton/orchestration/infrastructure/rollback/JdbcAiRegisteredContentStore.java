package com.masiton.orchestration.infrastructure.rollback;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.orchestration.application.port.out.AiRegisteredContentStore;

/** AI rollback의 공개 상태 변경을 소유하는 orchestration 저장 Adapter다. */
@Repository
public class JdbcAiRegisteredContentStore implements AiRegisteredContentStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcAiRegisteredContentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void makePrivateIfCreated(UUID snapshotId, UUID restaurantId, boolean restaurantCreated,
                                     UUID creatorId, boolean creatorCreated,
                                     UUID videoId, boolean videoCreated,
                                     UUID visitId, boolean visitCreated) {
        makePrivateIfCreated(snapshotId, restaurantId, restaurantCreated, creatorId, creatorCreated,
                videoId, videoCreated, visitId, visitCreated, null);
    }

    @Override
    @Transactional
    public void makePrivateIfCreated(UUID snapshotId, UUID restaurantId, boolean restaurantCreated,
            UUID creatorId, boolean creatorCreated, UUID videoId, boolean videoCreated,
            UUID visitId, boolean visitCreated, UUID registrationUnitId) {
        lockResources(restaurantId, creatorId, videoId, visitId);
        if (visitCreated && visitId != null && !isShared("visit", visitId, snapshotId, visitId, registrationUnitId)) {
            jdbcTemplate.update("UPDATE visit SET publication_status = 'PRIVATE' WHERE id = ?", visitId);
            jdbcTemplate.update("DELETE FROM visit_tag WHERE visit_id = ? AND created_from_snapshot_id = ?", visitId, snapshotId);
        }
        if (videoCreated && videoId != null && !isShared("video", videoId, snapshotId, visitId, registrationUnitId)) {
            jdbcTemplate.update("UPDATE video SET publication_status = 'PRIVATE' WHERE id = ?", videoId);
        }
        if (creatorCreated && creatorId != null && !isShared("creator", creatorId, snapshotId, visitId, registrationUnitId)) {
            jdbcTemplate.update("UPDATE creator SET publication_status = 'PRIVATE' WHERE id = ?", creatorId);
        }
        if (restaurantCreated && restaurantId != null && !isShared("restaurant", restaurantId, snapshotId, visitId, registrationUnitId)) {
            jdbcTemplate.update("UPDATE restaurant SET publication_status = 'PRIVATE' WHERE id = ?", restaurantId);
        }
    }

    @Override
    @Transactional
    public void deleteIfCreated(UUID restaurantId, boolean restaurantCreated,
                                UUID creatorId, boolean creatorCreated,
                                UUID videoId, boolean videoCreated,
                                UUID visitId, boolean visitCreated) {
        lockResources(restaurantId, creatorId, videoId, visitId);
        if (visitCreated && visitId != null && !hasRegistrationReference("visit", visitId)
                && !exists("SELECT EXISTS (SELECT 1 FROM visit_tag_revision WHERE visit_id = ?)", visitId)
                && !exists("SELECT EXISTS (SELECT 1 FROM visit_tag_merge_provenance WHERE visit_id = ?)", visitId)) {
            jdbcTemplate.update("DELETE FROM visit_tag WHERE visit_id = ?", visitId);
            jdbcTemplate.update("DELETE FROM visit WHERE id = ?", visitId);
        }
        if (videoCreated && videoId != null && !hasRegistrationReference("video", videoId)
                && !exists("SELECT EXISTS (SELECT 1 FROM visit WHERE video_id = ?)", videoId)) {
            jdbcTemplate.update("DELETE FROM video WHERE id = ?", videoId);
        }
        if (creatorCreated && creatorId != null && !hasRegistrationReference("creator", creatorId)
                && !hasVideoReferencing(creatorId)
                && !exists("SELECT EXISTS (SELECT 1 FROM visit WHERE creator_id = ?)", creatorId)
                && !exists("SELECT EXISTS (SELECT 1 FROM youtube_channel_watch WHERE creator_id = ?)", creatorId)
                && !exists("SELECT EXISTS (SELECT 1 FROM youtube_channel_backfill_run WHERE creator_id = ?)", creatorId)) {
            // VerifiedVideoRegistrationService.existingWithCreator()는 creator_id가 비어 있던 기존
            // Video에 이번 시도의 새 Creator를 연결하면서도 videoCreated=false를 반환한다. 그 Video는
            // 보존 대상(videoCreated=false)이라 위에서 지우지 않았으므로, 이 Creator를 지우면
            // video.creator_id FK(RESTRICT) 위반으로 트랜잭션 전체가 롤백된다. 참조가 남아 있으면
            // 삭제를 건너뛰고 Creator를 보존한다 — kakao_place_id처럼 재시도를 막는 unique 제약이
            // 없으므로 남겨 둬도 재시도 자체는 막히지 않는다.
            jdbcTemplate.update("DELETE FROM creator WHERE id = ?", creatorId);
        }
        if (restaurantCreated && restaurantId != null && !hasRegistrationReference("restaurant", restaurantId)
                && !hasRestaurantReference(restaurantId)) {
            jdbcTemplate.update("DELETE FROM restaurant WHERE id = ?", restaurantId);
        }
    }

    private void lockResources(UUID restaurantId, UUID creatorId, UUID videoId, UUID visitId) {
        // 등록 트랜잭션의 Creator → Restaurant → Video → Visit 순서를 유지한다.
        lock("creator", creatorId);
        lock("restaurant", restaurantId);
        lock("video", videoId);
        lock("visit", visitId);
    }

    private void lock(String table, UUID id) {
        if (id != null) {
            jdbcTemplate.query("SELECT id FROM " + table + " WHERE id = ? FOR UPDATE", resultSet -> null, id);
        }
    }

    private boolean hasRegistrationReference(String resource, UUID id) {
        String column = "registered_" + resource + "_id";
        return exists("SELECT EXISTS (SELECT 1 FROM ai_registration_unit WHERE " + column + " = ?) "
                + "OR EXISTS (SELECT 1 FROM ai_candidate_snapshot WHERE " + column + " = ?)", id, id);
    }

    private boolean isShared(String resource, UUID id, UUID snapshotId, UUID ownVisitId, UUID registrationUnitId) {
        String column = "registered_" + resource + "_id";
        boolean sharedUnit = registrationUnitId == null
                ? exists("SELECT EXISTS (SELECT 1 FROM ai_registration_unit WHERE " + column
                        + " = ? AND snapshot_id <> ?) OR (SELECT count(*) FROM ai_registration_unit WHERE "
                        + column + " = ?) > 1", id, snapshotId, id)
                : exists("SELECT EXISTS (SELECT 1 FROM ai_registration_unit WHERE " + column
                        + " = ? AND id <> ?)", id, registrationUnitId);
        if (sharedUnit || exists("SELECT EXISTS (SELECT 1 FROM ai_candidate_snapshot WHERE "
                + column + " = ? AND id <> ?)", id, snapshotId)) {
            return true;
        }
        if (!"visit".equals(resource) && exists("SELECT EXISTS (SELECT 1 FROM visit WHERE " + resource
                + "_id = ? AND id IS DISTINCT FROM ?)", id, ownVisitId)) {
            return true;
        }
        if ("creator".equals(resource)) {
            return exists("SELECT EXISTS (SELECT 1 FROM video WHERE creator_id = ? AND id NOT IN "
                    + "(SELECT video_id FROM visit WHERE id = ?))", id, ownVisitId);
        }
        return false;
    }

    private boolean hasRestaurantReference(UUID id) {
        for (String table : java.util.List.of("visit", "favorite", "recent_restaurant_view", "collection_restaurant",
                "curation_restaurant", "restaurant_kakao_revalidation", "restaurant_kakao_revalidation_audit")) {
            if (exists("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE restaurant_id = ?)", id)) {
                return true;
            }
        }
        return false;
    }

    private boolean exists(String sql, Object... values) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, values));
    }

    private boolean hasVideoReferencing(UUID creatorId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM video WHERE creator_id = ?)", Boolean.class, creatorId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void updateFoodCategory(UUID restaurantId, UUID foodCategoryId) {
        jdbcTemplate.update(
                "UPDATE restaurant SET food_category_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                foodCategoryId, restaurantId);
    }
}
