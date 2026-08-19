package com.masiton.orchestration.infrastructure.rollback;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.orchestration.application.port.out.AiRegisteredContentStore;

/** AI rollback의 공개 상태 변경을 소유하는 orchestration 저장 Adapter다. */
@Repository
public class JdbcAiRegisteredContentStore implements AiRegisteredContentStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcAiRegisteredContentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void makePrivateIfCreated(UUID snapshotId, UUID restaurantId, boolean restaurantCreated,
                                     UUID creatorId, boolean creatorCreated,
                                     UUID videoId, boolean videoCreated,
                                     UUID visitId, boolean visitCreated) {
        if (visitCreated && visitId != null) {
            jdbcTemplate.update("UPDATE visit SET publication_status = 'PRIVATE' WHERE id = ?", visitId);
            jdbcTemplate.update("DELETE FROM visit_tag WHERE visit_id = ? AND created_from_snapshot_id = ?", visitId, snapshotId);
        }
        if (videoCreated && videoId != null) {
            jdbcTemplate.update("UPDATE video SET publication_status = 'PRIVATE' WHERE id = ?", videoId);
        }
        if (creatorCreated && creatorId != null) {
            jdbcTemplate.update("UPDATE creator SET publication_status = 'PRIVATE' WHERE id = ?", creatorId);
        }
        if (restaurantCreated && restaurantId != null) {
            jdbcTemplate.update("UPDATE restaurant SET publication_status = 'PRIVATE' WHERE id = ?", restaurantId);
        }
    }

    @Override
    public void deleteIfCreated(UUID restaurantId, boolean restaurantCreated,
                                UUID creatorId, boolean creatorCreated,
                                UUID videoId, boolean videoCreated,
                                UUID visitId, boolean visitCreated) {
        if (visitCreated && visitId != null) {
            jdbcTemplate.update("DELETE FROM visit_tag WHERE visit_id = ?", visitId);
            jdbcTemplate.update("DELETE FROM visit WHERE id = ?", visitId);
        }
        if (videoCreated && videoId != null) {
            jdbcTemplate.update("DELETE FROM video WHERE id = ?", videoId);
        }
        if (creatorCreated && creatorId != null) {
            jdbcTemplate.update("DELETE FROM creator WHERE id = ?", creatorId);
        }
        if (restaurantCreated && restaurantId != null) {
            jdbcTemplate.update("DELETE FROM restaurant WHERE id = ?", restaurantId);
        }
    }

    @Override
    public void updateFoodCategory(UUID restaurantId, UUID foodCategoryId) {
        jdbcTemplate.update(
                "UPDATE restaurant SET food_category_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                foodCategoryId, restaurantId);
    }
}
