package com.masiton.orchestration.infrastructure.query;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.participation.application.port.out.ParticipationTargetReader;
import com.masiton.participation.domain.ParticipationTargetType;

@Component
class ParticipationTargetQueryAdapter implements ParticipationTargetReader {

    private final JdbcTemplate jdbcTemplate;

    ParticipationTargetQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean targetExists(ParticipationTargetType targetType, UUID targetId) {
        String query = switch (targetType) {
            case RESTAURANT -> """
                    SELECT EXISTS(SELECT 1 FROM restaurant
                     WHERE id = ? AND publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE')
                    """;
            case CREATOR -> """
                    SELECT EXISTS(SELECT 1 FROM creator
                     WHERE id = ? AND publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE'
                       AND external_availability_status = 'AVAILABLE')
                    """;
            case VIDEO -> """
                    SELECT EXISTS(SELECT 1 FROM video
                     WHERE id = ? AND publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE'
                       AND external_availability_status = 'AVAILABLE')
                    """;
            case VISIT_RELATIONSHIP -> """
                    SELECT EXISTS(
                        SELECT 1 FROM visit v
                        JOIN restaurant r ON r.id = v.restaurant_id
                        JOIN creator c ON c.id = v.creator_id
                        JOIN video vi ON vi.id = v.video_id
                       WHERE v.id = ?
                         AND v.publication_status = 'PUBLIC' AND v.lifecycle_status = 'ACTIVE'
                         AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'
                         AND c.publication_status = 'PUBLIC' AND c.lifecycle_status = 'ACTIVE'
                         AND c.external_availability_status = 'AVAILABLE'
                         AND vi.publication_status = 'PUBLIC' AND vi.lifecycle_status = 'ACTIVE'
                         AND vi.external_availability_status = 'AVAILABLE')
                    """;
        };
        Boolean exists = jdbcTemplate.queryForObject(query, Boolean.class, targetId);
        return Boolean.TRUE.equals(exists);
    }
}
