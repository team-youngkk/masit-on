package com.masiton.ai.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.ai.application.port.out.AiRegistrationUnitConcurrentAccessException;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL adapter for {@code ai_registration_unit} (V8). */
@Repository
class JdbcAiRegistrationUnitStore implements AiRegistrationUnitStore {

    private static final String LATEST_SNAPSHOT_ID_SUBQUERY = """
            (SELECT id FROM ai_candidate_snapshot
              WHERE job_id = ?
              ORDER BY snapshot_version DESC, created_at DESC, id DESC
              LIMIT 1)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    JdbcAiRegistrationUnitStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public UUID insert(RegistrationUnitInsert insert) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_registration_unit (
                    id, snapshot_id, unit_index, restaurant_name, review_status, block_reason,
                    place_decision, category_decision, registered_restaurant_id, registered_creator_id,
                    registered_video_id, registered_visit_id, reused_resources, executed_by, decided_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, COALESCE(?::jsonb, '[]'::jsonb), ?, ?)
                """, id, insert.snapshotId(), insert.unitIndex(), insert.restaurantName(), insert.reviewStatus(),
                insert.blockReason(), insert.placeDecisionJson(), insert.categoryDecisionJson(),
                insert.registeredRestaurantId(), insert.registeredCreatorId(), insert.registeredVideoId(),
                insert.registeredVisitId(), insert.reusedResourcesJson(), insert.executedBy(), insert.decidedAt());
        return id;
    }

    @Override
    public void markRegistered(UUID unitId, RegisteredResult registered) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_registration_unit
                   SET review_status = 'AUTO_CONFIRMED',
                       block_reason = NULL,
                       registered_restaurant_id = ?,
                       registered_creator_id = ?,
                       registered_video_id = ?,
                       registered_visit_id = ?,
                       reused_resources = COALESCE(?::jsonb, '[]'::jsonb),
                       place_decision = ?::jsonb,
                       category_decision = ?::jsonb,
                       executed_by = ?
                 WHERE id = ?
                """, registered.registeredRestaurantId(), registered.registeredCreatorId(),
                registered.registeredVideoId(), registered.registeredVisitId(), registered.reusedResourcesJson(),
                registered.placeDecisionJson(), registered.categoryDecisionJson(), registered.executedBy(), unitId);
        if (updated != 1) {
            throw new IllegalStateException("AI registration unit was not found for registration: " + unitId);
        }
    }

    @Override
    public List<RegistrationUnitRow> findBySnapshotId(UUID snapshotId) {
        return jdbcTemplate.query(selectColumns() + " FROM ai_registration_unit WHERE snapshot_id = ? "
                + "ORDER BY unit_index ASC", this::mapRow, snapshotId);
    }

    @Override
    public List<RegistrationUnitRow> findByJobId(UUID jobId) {
        return jdbcTemplate.query(selectColumns() + " FROM ai_registration_unit "
                + "WHERE snapshot_id = " + LATEST_SNAPSHOT_ID_SUBQUERY + " ORDER BY unit_index ASC",
                this::mapRow, jobId);
    }

    @Override
    public Optional<RegistrationUnitRow> lockByJobAndUnitId(UUID jobId, UUID unitId) {
        try {
            return jdbcTemplate.query(selectColumns() + " FROM ai_registration_unit "
                    + "WHERE id = ? AND snapshot_id = " + LATEST_SNAPSHOT_ID_SUBQUERY + " FOR UPDATE NOWAIT",
                    this::mapRow, unitId, jobId).stream().findFirst();
        } catch (PessimisticLockingFailureException exception) {
            throw new AiRegistrationUnitConcurrentAccessException(
                    "Concurrent request on the same registration unit: " + unitId, exception);
        } catch (DataAccessException exception) {
            // Spring's default SQLErrorCodeSQLExceptionTranslator does not map PostgreSQL's
            // lock_not_available (55P03, raised by FOR UPDATE NOWAIT) to PessimisticLockingFailureException,
            // so a NOWAIT conflict surfaces as a generic UncategorizedSQLException here instead.
            if (isLockNotAvailable(exception)) {
                throw new AiRegistrationUnitConcurrentAccessException(
                        "Concurrent request on the same registration unit: " + unitId, exception);
            }
            throw exception;
        }
    }

    private static final String POSTGRES_LOCK_NOT_AVAILABLE_SQL_STATE = "55P03";

    private boolean isLockNotAvailable(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && POSTGRES_LOCK_NOT_AVAILABLE_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void confirmWithSupplement(UUID unitId, RegisteredResult registered) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_registration_unit
                   SET review_status = 'MANUAL_OVERRIDE',
                       block_reason = NULL,
                       registered_restaurant_id = ?,
                       registered_creator_id = ?,
                       registered_video_id = ?,
                       registered_visit_id = ?,
                       reused_resources = COALESCE(?::jsonb, '[]'::jsonb),
                       place_decision = ?::jsonb,
                       category_decision = ?::jsonb
                 WHERE id = ?
                """, registered.registeredRestaurantId(), registered.registeredCreatorId(),
                registered.registeredVideoId(), registered.registeredVisitId(), registered.reusedResourcesJson(),
                registered.placeDecisionJson(), registered.categoryDecisionJson(), unitId);
        if (updated != 1) {
            throw new IllegalStateException("AI registration unit was not found for supplement: " + unitId);
        }
    }

    @Override
    public void rollback(UUID unitId, OffsetDateTime rolledBackAt) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_registration_unit
                   SET review_status = 'MANUAL_OVERRIDE',
                       rolled_back_at = ?,
                       registered_restaurant_id = NULL,
                       registered_creator_id = NULL,
                       registered_video_id = NULL,
                       registered_visit_id = NULL,
                       reused_resources = '[]'::jsonb,
                       place_decision = NULL,
                       category_decision = NULL
                 WHERE id = ?
                """, rolledBackAt, unitId);
        if (updated != 1) {
            throw new IllegalStateException("AI registration unit was not found for rollback: " + unitId);
        }
    }

    @Override
    public void discard(UUID unitId, OffsetDateTime discardedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_registration_unit
                   SET review_status = 'MANUAL_OVERRIDE',
                       discarded_at = ?,
                       block_reason = NULL
                 WHERE id = ?
                """, discardedAt, unitId);
        if (updated != 1) {
            throw new IllegalStateException("AI registration unit was not found for discard: " + unitId);
        }
    }

    @Override
    public void adjustCategory(UUID unitId, String categoryDecisionJson) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_registration_unit
                   SET review_status = 'MANUAL_OVERRIDE',
                       category_decision = ?::jsonb
                 WHERE id = ?
                """, categoryDecisionJson, unitId);
        if (updated != 1) {
            throw new IllegalStateException("AI registration unit was not found for category adjustment: " + unitId);
        }
    }

    private String selectColumns() {
        return """
                SELECT id, snapshot_id, unit_index, restaurant_name, review_status, block_reason,
                       place_decision::text AS place_decision_json,
                       category_decision::text AS category_decision_json,
                       registered_restaurant_id, registered_creator_id, registered_video_id, registered_visit_id,
                       reused_resources::text AS reused_resources_json, executed_by, decided_at,
                       rolled_back_at, discarded_at
                """;
    }

    private RegistrationUnitRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RegistrationUnitRow(
                rs.getObject("id", UUID.class),
                rs.getObject("snapshot_id", UUID.class),
                rs.getInt("unit_index"),
                rs.getString("restaurant_name"),
                rs.getString("review_status"),
                rs.getString("block_reason"),
                rs.getString("place_decision_json"),
                rs.getString("category_decision_json"),
                rs.getObject("registered_restaurant_id", UUID.class),
                rs.getObject("registered_creator_id", UUID.class),
                rs.getObject("registered_video_id", UUID.class),
                rs.getObject("registered_visit_id", UUID.class),
                reusedResources(rs.getString("reused_resources_json")),
                rs.getString("executed_by"),
                rs.getObject("decided_at", OffsetDateTime.class),
                rs.getObject("rolled_back_at", OffsetDateTime.class),
                rs.getObject("discarded_at", OffsetDateTime.class));
    }

    private List<String> reusedResources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode array = objectMapper.readTree(json);
            List<String> values = new ArrayList<>();
            array.forEach(element -> values.add(element.asText()));
            return List.copyOf(values);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid stored ai_registration_unit.reused_resources JSON", exception);
        }
    }
}
