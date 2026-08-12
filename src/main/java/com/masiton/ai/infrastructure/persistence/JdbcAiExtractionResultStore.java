package com.masiton.ai.infrastructure.persistence;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.ai.application.port.out.AiExtractionResultStore;

/** PostgreSQL adapter for the atomic AI result transition. */
@Repository
class JdbcAiExtractionResultStore implements AiExtractionResultStore {

    private final JdbcTemplate jdbcTemplate;

    JdbcAiExtractionResultStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ProcessingJob> lockProcessingJob(UUID jobId, String workerId, int attemptNo) {
        List<ProcessingJob> rows = jdbcTemplate.query("""
                SELECT id, youtube_channel_id, youtube_video_id, video_url
                  FROM ai_extraction_job
                 WHERE id = ?
                   AND execution_status = 'RUNNING'
                   AND lease_owner = ?
                   AND attempt_count = ?
                   AND lease_expires_at > clock_timestamp()
                FOR UPDATE
                """, this::mapProcessingJob, jobId, workerId, attemptNo);
        return rows.stream().findFirst();
    }

    @Override
    public int nextSnapshotVersion(UUID jobId) {
        Integer version = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(snapshot_version), 0) + 1
                  FROM ai_candidate_snapshot
                 WHERE job_id = ?
                """, Integer.class, jobId);
        return version == null ? 1 : version;
    }

    @Override
    public UUID insertSnapshot(UUID jobId, int snapshotVersion, String candidateFields, String candidateTags,
                               String fieldConfidences, String evidence, String missingFields,
                               String reviewStatus, String reviewReason, OffsetDateTime reviewedAt,
                               OffsetDateTime createdAt) {
        UUID snapshotId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_candidate_snapshot (
                    id, job_id, snapshot_version, candidate_fields, candidate_tags,
                    field_confidences, evidence, missing_fields, review_status,
                    review_reason, reviewed_at, created_at
                ) VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                """, snapshotId, jobId, snapshotVersion, candidateFields, candidateTags,
                fieldConfidences, evidence, missingFields, reviewStatus, reviewReason, reviewedAt, createdAt);
        return snapshotId;
    }

    @Override
    public Optional<TagDefinition> findTag(String tagCode) {
        List<TagDefinition> rows = jdbcTemplate.query("""
                SELECT id, tag_code, tag_type, display_name, aliases::text, status
                  FROM tag_definition
                 WHERE tag_code = ?
                """, this::mapTagDefinition, tagCode);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<TagDefinition> findTagForUpdate(String tagCode) {
        List<TagDefinition> rows = jdbcTemplate.query("""
                SELECT id, tag_code, tag_type, display_name, aliases::text, status
                  FROM tag_definition
                 WHERE tag_code = ?
                 FOR UPDATE
                """, this::mapTagDefinition, tagCode);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<TagDefinition> insertTagIfAbsent(UUID id, String tagCode, String tagType, String displayName,
                                                      String aliases, String source, UUID snapshotId,
                                                      OffsetDateTime createdAt) {
        List<TagDefinition> rows = jdbcTemplate.query("""
                INSERT INTO tag_definition (
                    id, tag_code, tag_type, display_name, aliases, status, source,
                    created_from_snapshot_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?::jsonb, 'ACTIVE', ?, ?, ?, ?)
                ON CONFLICT (tag_code) DO NOTHING
                RETURNING id, tag_code, tag_type, display_name, aliases::text, status
                """, this::mapTagDefinition, id, tagCode, tagType, displayName, aliases, source, snapshotId,
                createdAt, createdAt);
        return rows.stream().findFirst();
    }

    @Override
    public void insertTagReview(UUID snapshotId, String candidateTagId, String decision,
                                UUID replacementTagDefinitionId, String reason, OffsetDateTime reviewedAt) {
        jdbcTemplate.update("""
                INSERT INTO ai_candidate_tag_review (
                    id, snapshot_id, candidate_tag_id, decision, replacement_tag_definition_id,
                    reason, decision_source, reviewed_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'SYSTEM', ?)
                """, UUID.randomUUID(), snapshotId, candidateTagId, decision,
                replacementTagDefinitionId, reason, reviewedAt);
    }

    @Override
    public void insertVisitTag(UUID visitId, UUID tagDefinitionId, java.math.BigDecimal confidence,
                               String evidence, String extractorVersion, OffsetDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO visit_tag (
                    id, visit_id, tag_definition_id, source, confidence, evidence,
                    extractor_version, created_at
                ) VALUES (?, ?, ?, 'AI_AUTO_CONFIRMED', ?, ?::jsonb, ?, ?)
                ON CONFLICT (visit_id, tag_definition_id) DO NOTHING
                """, UUID.randomUUID(), visitId, tagDefinitionId, confidence, evidence,
                extractorVersion, createdAt);
    }

    @Override
    public void markRegisteredContent(UUID snapshotId, UUID restaurantId, boolean restaurantCreated,
                                      UUID creatorId, boolean creatorCreated, UUID videoId, boolean videoCreated,
                                      UUID visitId, boolean visitCreated) {
        jdbcTemplate.update("""
                UPDATE ai_candidate_snapshot
                   SET registered_restaurant_id = ?, restaurant_created = ?,
                       registered_creator_id = ?, creator_created = ?,
                       registered_video_id = ?, video_created = ?,
                       registered_visit_id = ?, visit_created = ?
                 WHERE id = ?
                """, restaurantId, restaurantCreated, creatorId, creatorCreated, videoId, videoCreated,
                visitId, visitCreated, snapshotId);
    }

    @Override
    public void completeSuccess(UUID jobId, String workerId, int attemptNo, String resultCompleteness,
                                OffsetDateTime attemptStartedAt, OffsetDateTime finishedAt,
                                String providerRequestId) {
        jdbcTemplate.update("""
                INSERT INTO ai_extraction_attempt (
                    id, job_id, attempt_no, provider_request_id, started_at, finished_at, outcome
                ) VALUES (?, ?, ?, ?, ?, ?, 'SUCCEEDED')
                ON CONFLICT (job_id, attempt_no) DO NOTHING
                """, UUID.randomUUID(), jobId, attemptNo, providerRequestId, attemptStartedAt, finishedAt);
        int updated = jdbcTemplate.update("""
                UPDATE ai_extraction_job
                   SET execution_status = 'SUCCEEDED', result_completeness = ?, finished_at = ?,
                       lease_owner = NULL, lease_expires_at = NULL
                 WHERE id = ? AND execution_status = 'RUNNING'
                   AND lease_owner = ? AND attempt_count = ? AND lease_expires_at > clock_timestamp()
                """, resultCompleteness, finishedAt, jobId, workerId, attemptNo);
        if (updated != 1) {
            throw new IllegalStateException("AI extraction job lease was lost while completing the result.");
        }
    }

    private ProcessingJob mapProcessingJob(ResultSet rs, int rowNum) throws SQLException {
        return new ProcessingJob(rs.getObject("id", UUID.class), rs.getString("youtube_channel_id"),
                rs.getString("youtube_video_id"), URI.create(rs.getString("video_url")));
    }

    private TagDefinition mapTagDefinition(ResultSet rs, int rowNum) throws SQLException {
        return new TagDefinition(rs.getObject("id", UUID.class), rs.getString("tag_code"),
                rs.getString("tag_type"), rs.getString("display_name"), rs.getString("aliases"),
                rs.getString("status"));
    }
}
