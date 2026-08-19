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
        if (creatorCreated && creatorId != null && !hasVideoReferencing(creatorId)) {
            // VerifiedVideoRegistrationService.existingWithCreator()는 creator_id가 비어 있던 기존
            // Video에 이번 시도의 새 Creator를 연결하면서도 videoCreated=false를 반환한다. 그 Video는
            // 보존 대상(videoCreated=false)이라 위에서 지우지 않았으므로, 이 Creator를 지우면
            // video.creator_id FK(RESTRICT) 위반으로 트랜잭션 전체가 롤백된다. 참조가 남아 있으면
            // 삭제를 건너뛰고 Creator를 보존한다 — kakao_place_id처럼 재시도를 막는 unique 제약이
            // 없으므로 남겨 둬도 재시도 자체는 막히지 않는다.
            jdbcTemplate.update("DELETE FROM creator WHERE id = ?", creatorId);
        }
        if (restaurantCreated && restaurantId != null) {
            jdbcTemplate.update("DELETE FROM restaurant WHERE id = ?", restaurantId);
        }
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
