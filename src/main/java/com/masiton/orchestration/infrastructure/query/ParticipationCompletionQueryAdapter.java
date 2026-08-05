package com.masiton.orchestration.infrastructure.query;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.participation.application.port.out.ParticipationCompletionReader;
import com.masiton.participation.domain.ModerationActionType;
import com.masiton.participation.domain.ParticipationTargetType;

@Component
public class ParticipationCompletionQueryAdapter implements ParticipationCompletionReader {

    private final JdbcTemplate jdbcTemplate;

    public ParticipationCompletionQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isCompleted(
            ModerationActionType actionType, ParticipationTargetType targetType,
            UUID targetId, OffsetDateTime acceptedAt, Map<String, Object> candidate) {
        Query query = query(actionType, targetType, targetId, candidate);
        query.sql.append(statusAndTime(actionType, targetType));
        query.args.add(acceptedAt);
        Boolean exists = jdbcTemplate.queryForObject(
                query.sql.append(")").toString(), Boolean.class, query.args.toArray());
        return Boolean.TRUE.equals(exists);
    }

    private Query query(
            ModerationActionType actionType, ParticipationTargetType type,
            UUID targetId, Map<String, Object> candidate) {
        List<Object> args = new ArrayList<>();
        args.add(targetId);
        StringBuilder sql = new StringBuilder(switch (type) {
            case RESTAURANT -> "SELECT EXISTS (SELECT 1 FROM restaurant t WHERE t.id = ?";
            case CREATOR -> "SELECT EXISTS (SELECT 1 FROM creator t WHERE t.id = ?";
            case VIDEO -> "SELECT EXISTS (SELECT 1 FROM video t WHERE t.id = ?";
            case VISIT_RELATIONSHIP -> actionType == ModerationActionType.HIDDEN
                    ? "SELECT EXISTS (SELECT 1 FROM visit t WHERE t.id = ?"
                    : """
                    SELECT EXISTS (SELECT 1 FROM visit t
                    JOIN restaurant r ON r.id = t.restaurant_id
                    JOIN creator c ON c.id = t.creator_id
                    JOIN video vi ON vi.id = t.video_id
                    WHERE t.id = ?
                    """;
        });
        if (candidate != null) {
            switch (type) {
                case RESTAURANT -> {
                    sql.append(" AND t.name = ? AND t.road_address = ?");
                    args.add(candidate.get("name"));
                    args.add(candidate.get("roadAddress"));
                }
                case CREATOR -> {
                    sql.append(" AND t.channel_url = ?");
                    args.add(candidate.get("channelUrl"));
                }
                case VIDEO -> {
                    sql.append(" AND t.source_url = ?");
                    args.add(candidate.get("videoUrl"));
                }
                case VISIT_RELATIONSHIP -> {
                    sql.append(" AND t.restaurant_id = ? AND t.creator_id = ? AND t.video_id = ?");
                    args.add(uuid(candidate.get("restaurantId")));
                    args.add(uuid(candidate.get("creatorId")));
                    args.add(uuid(candidate.get("videoId")));
                }
            }
        }
        return new Query(sql, args);
    }

    private String statusAndTime(ModerationActionType actionType, ParticipationTargetType type) {
        if (actionType == ModerationActionType.HIDDEN) {
            return " AND t.publication_status = 'PRIVATE' AND t.updated_at > ?";
        }
        StringBuilder condition = new StringBuilder(
                " AND t.publication_status = 'PUBLIC' AND t.lifecycle_status = 'ACTIVE'");
        if (type == ParticipationTargetType.CREATOR || type == ParticipationTargetType.VIDEO) {
            condition.append(" AND t.external_availability_status = 'AVAILABLE'");
        }
        if (type == ParticipationTargetType.VISIT_RELATIONSHIP) {
            condition.append("""
                     AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'
                     AND c.publication_status = 'PUBLIC' AND c.lifecycle_status = 'ACTIVE'
                     AND c.external_availability_status = 'AVAILABLE'
                     AND vi.publication_status = 'PUBLIC' AND vi.lifecycle_status = 'ACTIVE'
                     AND vi.external_availability_status = 'AVAILABLE'
                    """);
        }
        return condition.append(actionType == ModerationActionType.CREATED
                ? " AND t.created_at >= ?" : " AND t.updated_at > ?").toString();
    }

    private UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private record Query(StringBuilder sql, List<Object> args) {
    }
}
