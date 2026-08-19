package com.masiton.ai.infrastructure.persistence;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.ai.application.port.out.AiRegistrationUnitReviewStore;

/** PostgreSQL adapter for {@code ai_registration_unit_review} (V6). */
@Repository
class JdbcAiRegistrationUnitReviewStore implements AiRegistrationUnitReviewStore {

    private final JdbcTemplate jdbcTemplate;

    JdbcAiRegistrationUnitReviewStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UUID insert(RegistrationUnitReviewInsert insert) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_registration_unit_review (
                    id, registration_unit_id, decision, reason, submitted_supplements,
                    previous_category_decision, reverted_registration, reviewed_by, reviewed_at
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, CURRENT_TIMESTAMP)
                """, id, insert.registrationUnitId(), insert.decision(), insert.reason(),
                insert.submittedSupplementsJson(), insert.previousCategoryDecisionJson(),
                insert.revertedRegistrationJson(), insert.reviewedBy());
        return id;
    }
}
