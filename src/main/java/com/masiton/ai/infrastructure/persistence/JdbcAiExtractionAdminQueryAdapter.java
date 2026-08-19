package com.masiton.ai.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;
import com.masiton.common.web.BusinessException;

@Repository
public class JdbcAiExtractionAdminQueryAdapter implements AiExtractionAdminQueryPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    public JdbcAiExtractionAdminQueryAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page list(String executionStatus, String source, String reviewStatus, int offset, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        filter(where, args, "job.execution_status", executionStatus);
        filter(where, args, "job.source", source);
        filter(where, args, "snapshot.review_status", reviewStatus);
        String from = " FROM ai_extraction_job job LEFT JOIN LATERAL (SELECT review_status "
                + "FROM ai_candidate_snapshot WHERE job_id=job.id ORDER BY snapshot_version DESC, "
                + "created_at DESC, id DESC LIMIT 1) snapshot ON true";
        long total = jdbc.queryForObject("SELECT count(*)" + from + where, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add(offset);
        List<AiExtractionJobView> items = jdbc.query(
                listSelect() + from + where + " ORDER BY job.created_at DESC, job.id ASC LIMIT ? OFFSET ?",
                this::job, queryArgs.toArray());
        return new Page(items, total);
    }

    @Override
    public Optional<Detail> detail(UUID jobId) {
        String from = " FROM ai_extraction_job job LEFT JOIN LATERAL (SELECT * FROM ai_candidate_snapshot "
                + "WHERE job_id=job.id ORDER BY snapshot_version DESC, created_at DESC, id DESC LIMIT 1) "
                + "snapshot ON true WHERE job.id=?";
        List<Detail> rows = jdbc.query(select() + from, (rs, n) -> new Detail(
                job(rs, n), json(rs, "candidate_fields"), json(rs, "candidate_tags"),
                json(rs, "field_confidences"), json(rs, "evidence"), json(rs, "missing_fields"),
                rs.getBoolean("candidate_truncated"),
                rs.getString("error_category"), retryable(rs.getString("error_category")), attempts(jobId)), jobId);
        return rows.stream().findFirst();
    }
    @Override
    public Optional<RetryTarget> retryTarget(UUID id) {
        return jdbc.query("SELECT video_url, execution_status, result_completeness FROM ai_extraction_job WHERE id=?",
                (rs, n) -> new RetryTarget(rs.getString(1), rs.getString(2), rs.getString(3)), id)
                .stream().findFirst();
    }
    @Override
    public Optional<ReviewTarget> reviewSnapshot(UUID id) {
        return jdbc.query("""
                SELECT snapshot.id, snapshot.review_status, job.id, job.youtube_channel_id, job.youtube_video_id,
                       job.video_url, snapshot.candidate_fields, snapshot.candidate_tags, snapshot.field_confidences, snapshot.evidence,
                       snapshot.registered_restaurant_id, snapshot.restaurant_created,
                       snapshot.registered_creator_id, snapshot.creator_created,
                       snapshot.registered_video_id, snapshot.video_created,
                       snapshot.registered_visit_id, snapshot.visit_created,
                       (SELECT registration_snapshot.id
                          FROM ai_candidate_snapshot registration_snapshot
                         WHERE registration_snapshot.job_id = snapshot.job_id
                           AND (registration_snapshot.registered_restaurant_id IS NOT NULL
                                OR registration_snapshot.registered_creator_id IS NOT NULL
                                OR registration_snapshot.registered_video_id IS NOT NULL
                                OR registration_snapshot.registered_visit_id IS NOT NULL)
                         ORDER BY registration_snapshot.snapshot_version ASC,
                                  registration_snapshot.created_at ASC, registration_snapshot.id ASC
                         LIMIT 1) AS registration_snapshot_id
                  FROM ai_candidate_snapshot snapshot
                  JOIN ai_extraction_job job ON job.id = snapshot.job_id
                 WHERE snapshot.job_id=?
                 ORDER BY snapshot.snapshot_version DESC, snapshot.created_at DESC, snapshot.id DESC
                LIMIT 1
                """, this::mapReviewTarget, id).stream().findFirst();
    }
    @Override
    public Optional<ReviewTarget> reviewTarget(UUID id) {
        List<UUID> lockedJobs = jdbc.query("SELECT id FROM ai_extraction_job WHERE id=? FOR UPDATE",
                (rs, n) -> rs.getObject(1, UUID.class), id);
        if (lockedJobs.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.query("""
                SELECT snapshot.id, snapshot.review_status, job.id, job.youtube_channel_id, job.youtube_video_id,
                       job.video_url, snapshot.candidate_fields, snapshot.candidate_tags, snapshot.field_confidences, snapshot.evidence,
                       snapshot.registered_restaurant_id, snapshot.restaurant_created,
                       snapshot.registered_creator_id, snapshot.creator_created,
                       snapshot.registered_video_id, snapshot.video_created,
                       snapshot.registered_visit_id, snapshot.visit_created,
                       (SELECT registration_snapshot.id
                          FROM ai_candidate_snapshot registration_snapshot
                         WHERE registration_snapshot.job_id = snapshot.job_id
                           AND (registration_snapshot.registered_restaurant_id IS NOT NULL
                                OR registration_snapshot.registered_creator_id IS NOT NULL
                                OR registration_snapshot.registered_video_id IS NOT NULL
                                OR registration_snapshot.registered_visit_id IS NOT NULL)
                         ORDER BY registration_snapshot.snapshot_version ASC,
                                  registration_snapshot.created_at ASC, registration_snapshot.id ASC
                         LIMIT 1) AS registration_snapshot_id
                  FROM ai_candidate_snapshot snapshot
                  JOIN ai_extraction_job job ON job.id = snapshot.job_id
                 WHERE snapshot.job_id=?
                 ORDER BY snapshot.snapshot_version DESC, snapshot.created_at DESC, snapshot.id DESC
                LIMIT 1
                FOR UPDATE
                """, (rs,n)->new ReviewTarget(rs.getObject(1,UUID.class),rs.getString(2),
                rs.getObject(3,UUID.class),rs.getString(4),rs.getString(5),rs.getString(6),
                json(rs,"candidate_fields"),json(rs,"candidate_tags"),json(rs,"field_confidences"),json(rs,"evidence"),registered(rs)), id).stream().findFirst();
    }
    private ReviewTarget mapReviewTarget(ResultSet rs, int n) throws SQLException {
        return new ReviewTarget(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                rs.getString(4), rs.getString(5), rs.getString(6), json(rs, "candidate_fields"),
                json(rs, "candidate_tags"), json(rs, "field_confidences"), json(rs, "evidence"), registered(rs));
    }
    @Override public void markRegisteredContent(UUID snapshotId, RegisteredContent content) {
        jdbc.update("""
                UPDATE ai_candidate_snapshot
                   SET registered_restaurant_id=?, restaurant_created=?, registered_creator_id=?, creator_created=?,
                       registered_video_id=?, video_created=?, registered_visit_id=?, visit_created=?
                 WHERE id=?
                """, content.restaurantId(), content.restaurantCreated(), content.creatorId(), content.creatorCreated(),
                content.videoId(), content.videoCreated(), content.visitId(), content.visitCreated(), snapshotId);
    }
    @Override
    public List<TagDecision> connectConfirmedTags(UUID snapshotId, UUID visitId, List<TagDecision> decisions) {
        List<TagDecision> attached = new ArrayList<>();
        java.util.Map<String, TagDecision> replacements = new java.util.HashMap<>();
        for (TagDecision decision : decisions) {
            replacements.put(decision.candidateTagId(), decision);
        }
        List<java.util.Map<String, Object>> candidates = jdbc.query("""
                SELECT tag.value->>'candidateTagId' AS candidate_tag_id,
                       COALESCE(NULLIF(tag.value->>'normalizedCode', ''), '') AS normalized_code,
                       (tag.value->>'confidence')::numeric AS confidence,
                       tag.value->'evidence' AS evidence,
                       job.model_version || '/' || job.prompt_version || '/' || job.schema_version AS extractor_version
                  FROM ai_candidate_snapshot snapshot
                  JOIN ai_extraction_job job ON job.id = snapshot.job_id
                  CROSS JOIN LATERAL jsonb_array_elements(snapshot.candidate_tags) tag(value)
                 WHERE snapshot.id = ?
                """, (rs, n) -> {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("id", rs.getString("candidate_tag_id"));
                    row.put("code", rs.getString("normalized_code"));
                    row.put("confidence", rs.getBigDecimal("confidence"));
                    row.put("evidence", rs.getString("evidence"));
                    row.put("version", rs.getString("extractor_version"));
                    return row;
                }, snapshotId);
        java.util.Map<String, UUID> activeDefinitions = activeTagDefinitions(candidates, replacements);
        for (java.util.Map<String, Object> candidate : candidates) {
            String candidateId = (String) candidate.get("id");
            TagDecision manualDecision = replacements.get(candidateId);
            String evidence = (String) candidate.get("evidence");
            if (manualDecision == null && !hasConnectableEvidence(evidence)) {
                continue;
            }
            String code = manualDecision == null || manualDecision.tagCode() == null
                    || manualDecision.tagCode().isBlank()
                    ? (String) candidate.get("code")
                    : manualDecision.tagCode().trim();
            if (code == null || code.isBlank()) {
                continue;
            }
            UUID definitionId = activeDefinitions.get(code);
            if (definitionId == null) {
                if (manualDecision != null) {
                    throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "AIEXTRACT_VALIDATION_CONFLICT",
                            "The requested tag code is not active.");
                }
                continue;
            }
            jdbc.update("""
                    INSERT INTO visit_tag(id, visit_id, tag_definition_id, source, confidence, evidence, extractor_version,
                                          created_from_snapshot_id)
                    VALUES (?, ?, ?, ?, ?, COALESCE(?::jsonb, '{}'::jsonb), ?, ?)
                    ON CONFLICT (visit_id, tag_definition_id) DO NOTHING
                    """, UUID.randomUUID(), visitId, definitionId, "ADMIN_OVERRIDE",
                    candidate.get("confidence"), evidence, candidate.get("version"), snapshotId);
            attached.add(new TagDecision(candidateId, "MANUAL_OVERRIDE", code));
        }
        return attached;
    }

    private java.util.Map<String, UUID> activeTagDefinitions(List<java.util.Map<String, Object>> candidates,
                                                               java.util.Map<String, TagDecision> replacements) {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (java.util.Map<String, Object> candidate : candidates) {
            TagDecision manualDecision = replacements.get(candidate.get("id"));
            String code = manualDecision == null || manualDecision.tagCode() == null || manualDecision.tagCode().isBlank()
                    ? (String) candidate.get("code") : manualDecision.tagCode().trim();
            if (code != null && !code.isBlank()) {
                codes.add(code);
            }
        }
        if (codes.isEmpty()) {
            return java.util.Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(codes.size(), "?"));
        return jdbc.query("SELECT id, tag_code FROM tag_definition WHERE status='ACTIVE' AND tag_code IN (" + placeholders + ")",
                rs -> {
                    java.util.Map<String, UUID> definitions = new java.util.HashMap<>();
                    while (rs.next()) {
                        definitions.put(rs.getString("tag_code"), rs.getObject("id", UUID.class));
                    }
                    return definitions;
                }, codes.toArray());
    }

    private boolean hasConnectableEvidence(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(evidence);
            String type = node.path("type").asText();
            return "TIMESTAMP".equals(type)
                    || ("TEXT_RANGE".equals(type) && node.hasNonNull("sourceHash")
                    && !node.path("sourceHash").asText().isBlank());
        } catch (Exception ignored) {
            return false;
        }
    }
    @Override
    public UUID override(UUID snapshotId, String expected, UUID adminId, String reason, String decision) {
        UUID inserted = jdbc.query("""
                WITH source AS (
                    SELECT snapshot.*, (SELECT COALESCE(MAX(snapshot_version), 0) + 1
                                          FROM ai_candidate_snapshot next_snapshot
                                         WHERE next_snapshot.job_id = snapshot.job_id) AS next_version
                      FROM ai_candidate_snapshot snapshot
                     WHERE snapshot.id = ?
                       AND snapshot.review_status = ?
                       AND snapshot.id = (
                           SELECT latest.id
                             FROM ai_candidate_snapshot latest
                            WHERE latest.job_id = snapshot.job_id
                            ORDER BY latest.snapshot_version DESC, latest.created_at DESC, latest.id DESC
                            LIMIT 1
                       )
                )
                INSERT INTO ai_candidate_snapshot (
                    id, job_id, snapshot_version, candidate_fields, candidate_tags, field_confidences, evidence,
                    missing_fields, review_status, reviewed_by, review_reason, reviewed_at, created_at,
                    registered_restaurant_id, registered_creator_id, registered_video_id, registered_visit_id,
                    restaurant_created, creator_created, video_created, visit_created
                )
                SELECT ?, job_id, next_version, candidate_fields, candidate_tags, field_confidences, evidence,
                       missing_fields, 'MANUAL_OVERRIDE', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                       registered_restaurant_id, registered_creator_id, registered_video_id, registered_visit_id,
                       restaurant_created, creator_created, video_created, visit_created
                  FROM source
                RETURNING id
                """, (rs, n) -> rs.getObject(1, UUID.class), snapshotId, expected, UUID.randomUUID(), adminId,
                reason.trim()).stream().findFirst().orElse(null);
        if (inserted != null) {
            appendManualReviewAudit(inserted, decision, expected, adminId, reason);
        }
        return inserted;
    }
    private void appendManualReviewAudit(UUID snapshotId, String decision, String expected,
                                         UUID adminId, String reason) {
        jdbc.update("INSERT INTO ai_extraction_manual_review(id,snapshot_id,decision,previous_review_status,reviewed_by,reason) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), snapshotId, decision, expected, adminId, reason.trim());
    }
    @Override
    public Optional<JobVideoReference> jobVideoReference(UUID jobId) {
        return jdbc.query("SELECT youtube_channel_id, youtube_video_id, video_url FROM ai_extraction_job WHERE id=?",
                (rs, n) -> new JobVideoReference(rs.getString(1), rs.getString(2), rs.getString(3)), jobId)
                .stream().findFirst();
    }

    @Override
    public Optional<SnapshotCandidateJson> snapshotCandidates(UUID snapshotId) {
        return jdbc.query("SELECT candidate_fields, field_confidences, evidence, candidate_tags "
                        + "FROM ai_candidate_snapshot WHERE id=?",
                (rs, n) -> new SnapshotCandidateJson(json(rs, "candidate_fields"), json(rs, "field_confidences"),
                        json(rs, "evidence"), json(rs, "candidate_tags")), snapshotId)
                .stream().findFirst();
    }

    @Override
    public void appendTagOverrides(UUID snapshotId, UUID adminId, String reason, List<TagDecision> decisions) {
        for (TagDecision decision : decisions) {
            jdbc.update("INSERT INTO ai_candidate_tag_review(id,snapshot_id,candidate_tag_id,decision,"
                            + "decision_source,reviewed_by,reason,manual_tag_code) "
                            + "VALUES (?,?,?,'MANUAL_OVERRIDE','ADMIN',?,?,?)",
                    UUID.randomUUID(), snapshotId, decision.candidateTagId(), adminId,
                    reason.trim(), decision.tagCode());
        }
    }
    private String listSelect() {
        return "SELECT job.id,job.source,job.youtube_channel_id,job.youtube_video_id,job.video_url,"
                + "job.execution_status,job.result_completeness,snapshot.review_status,job.provider,"
                + "job.model_version,job.prompt_version,job.schema_version,job.attempt_count,"
                + "job.created_at,job.started_at,job.finished_at";
    }

    private String select() {
        return "SELECT job.id,job.source,job.youtube_channel_id,job.youtube_video_id,job.video_url,"
                + "job.execution_status,job.result_completeness,snapshot.review_status,job.provider,"
                + "job.model_version,job.prompt_version,job.schema_version,job.attempt_count,"
                + "job.created_at,job.started_at,job.finished_at,job.error_category,"
                + "snapshot.candidate_fields,snapshot.candidate_tags,snapshot.field_confidences,"
                + "snapshot.evidence,snapshot.missing_fields,snapshot.candidate_truncated";
    }

    private AiExtractionJobView job(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AiExtractionJobView(
                resultSet.getObject("id", UUID.class), resultSet.getString("source"),
                resultSet.getString("youtube_channel_id"), resultSet.getString("youtube_video_id"),
                resultSet.getString("video_url"), resultSet.getString("execution_status"),
                resultSet.getString("result_completeness"), resultSet.getString("review_status"),
                resultSet.getString("provider"), resultSet.getString("model_version"),
                resultSet.getString("prompt_version"), resultSet.getString("schema_version"),
                resultSet.getInt("attempt_count"), resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("finished_at", OffsetDateTime.class), false);
    }

    private List<Attempt> attempts(UUID id) {
        return jdbc.query("SELECT attempt_no,outcome,error_category,started_at,finished_at "
                        + "FROM ai_extraction_attempt WHERE job_id=? ORDER BY attempt_no ASC",
                (resultSet, rowNumber) -> new Attempt(resultSet.getInt(1), resultSet.getString(2),
                        resultSet.getString(3), resultSet.getObject(4, OffsetDateTime.class),
                        resultSet.getObject(5, OffsetDateTime.class)), id);
    }

    private JsonNode json(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        try {
            return value == null ? objectMapper.nullNode() : objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid stored AI snapshot JSON", exception);
        }
    }

    private Boolean retryable(String category) {
        return category == null
                ? null
                : !(category.equals("INPUT") || category.equals("SCHEMA") || category.equals("PROVIDER_BLOCKED"));
    }

    private void filter(StringBuilder where, List<Object> arguments, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append("=?");
            arguments.add(value);
        }
    }
    private RegisteredContent registered(ResultSet rs) throws SQLException {
        UUID restaurantId = rs.getObject("registered_restaurant_id", UUID.class);
        UUID creatorId = rs.getObject("registered_creator_id", UUID.class);
        UUID videoId = rs.getObject("registered_video_id", UUID.class);
        UUID visitId = rs.getObject("registered_visit_id", UUID.class);
        return new RegisteredContent(restaurantId, Boolean.TRUE.equals(rs.getObject("restaurant_created", Boolean.class)),
                creatorId, Boolean.TRUE.equals(rs.getObject("creator_created", Boolean.class)), videoId,
                Boolean.TRUE.equals(rs.getObject("video_created", Boolean.class)), visitId,
                Boolean.TRUE.equals(rs.getObject("visit_created", Boolean.class)),
                rs.getObject("registration_snapshot_id", UUID.class));
    }
}
