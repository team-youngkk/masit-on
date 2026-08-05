package com.masiton.participation.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.masiton.participation.application.ParticipationException;
import com.masiton.participation.application.ParticipationView;
import com.masiton.participation.application.port.out.ParticipationStore;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;
import com.masiton.participation.domain.ReportType;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcParticipationStore implements ParticipationStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<ParticipationView.Submission> submissionMapper = (result, row) ->
            new ParticipationView.Submission(
                    result.getObject("id", UUID.class),
                    ParticipationTargetType.valueOf(result.getString("target_type")),
                    candidate(result.getString("candidate")),
                    result.getString("description"),
                    result.getString("evidence_url"),
                    ParticipationStatus.valueOf(result.getString("status")),
                    result.getString("member_reason"),
                    result.getObject("created_at", OffsetDateTime.class),
                    result.getObject("updated_at", OffsetDateTime.class));
    private final RowMapper<ParticipationView.Report> reportMapper = (result, row) ->
            new ParticipationView.Report(
                    result.getObject("id", UUID.class),
                    ParticipationTargetType.valueOf(result.getString("target_type")),
                    result.getObject("target_id", UUID.class),
                    ReportType.valueOf(result.getString("report_type")),
                    result.getString("description"),
                    result.getString("evidence_url"),
                    ParticipationStatus.valueOf(result.getString("status")),
                    result.getString("member_reason"),
                    result.getObject("created_at", OffsetDateTime.class),
                    result.getObject("updated_at", OffsetDateTime.class));

    public JdbcParticipationStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void lockMember(UUID memberId) {
        List<UUID> ids = jdbcTemplate.query(
                "SELECT id FROM member_account WHERE id = ? FOR UPDATE",
                (result, row) -> result.getObject(1, UUID.class), memberId);
        if (ids.isEmpty()) {
            throw new ParticipationException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "인증이 필요합니다.");
        }
    }

    @Override
    public long countCreated(UUID memberId, OffsetDateTime from, OffsetDateTime until) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT count(*) FROM submission
                      WHERE member_id = ? AND created_at >= ? AND created_at < ?)
                  + (SELECT count(*) FROM report
                      WHERE member_id = ? AND created_at >= ? AND created_at < ?)
                """, Long.class, memberId, from, until, memberId, from, until);
        return count == null ? 0 : count;
    }

    @Override
    public Optional<ParticipationView.Submission> findOpenSubmission(
            UUID memberId, ParticipationTargetType targetType, byte[] fingerprint
    ) {
        return first(jdbcTemplate.query("""
                SELECT id, target_type, candidate::text AS candidate, description, evidence_url,
                       status, member_reason, created_at, updated_at
                  FROM submission
                 WHERE member_id = ? AND target_type = ? AND target_fingerprint = ?
                   AND status NOT IN ('REJECTED', 'COMPLETED')
                """, submissionMapper, memberId, targetType.name(), fingerprint));
    }

    @Override
    public ParticipationView.Submission insertSubmission(
            UUID id, UUID memberId, ParticipationTargetType targetType, Map<String, Object> candidate,
            byte[] fingerprint, String description, String evidenceUrl, OffsetDateTime now
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO submission
                    (id, member_id, target_type, candidate, target_fingerprint, description,
                     evidence_url, status, created_at, updated_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?, 'RECEIVED', ?, ?)
                RETURNING id, target_type, candidate::text AS candidate, description, evidence_url,
                          status, member_reason, created_at, updated_at
                """, submissionMapper, id, memberId, targetType.name(), json(candidate), fingerprint,
                description, evidenceUrl, now, now);
    }

    @Override
    public Optional<ParticipationView.Report> findOpenReport(
            UUID memberId, ParticipationTargetType targetType, UUID targetId, ReportType reportType
    ) {
        return first(jdbcTemplate.query("""
                SELECT id, target_type, target_id, report_type, description, evidence_url,
                       status, member_reason, created_at, updated_at
                  FROM report
                 WHERE member_id = ? AND target_type = ? AND target_id = ? AND report_type = ?
                   AND status NOT IN ('REJECTED', 'COMPLETED')
                """, reportMapper, memberId, targetType.name(), targetId, reportType.name()));
    }

    @Override
    public ParticipationView.Report insertReport(
            UUID id, UUID memberId, ParticipationTargetType targetType, UUID targetId,
            ReportType reportType, String description, String evidenceUrl, OffsetDateTime now
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO report
                    (id, member_id, target_type, target_id, report_type, description,
                     evidence_url, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'RECEIVED', ?, ?)
                RETURNING id, target_type, target_id, report_type, description, evidence_url,
                          status, member_reason, created_at, updated_at
                """, reportMapper, id, memberId, targetType.name(), targetId, reportType.name(),
                description, evidenceUrl, now, now);
    }

    @Override
    public List<ParticipationView.Submission> findSubmissions(
            UUID memberId, ParticipationStatus status, int limit, long offset
    ) {
        if (status == null) {
            return jdbcTemplate.query("""
                    SELECT id, target_type, candidate::text AS candidate, description, evidence_url,
                           status, member_reason, created_at, updated_at
                      FROM submission WHERE member_id = ?
                     ORDER BY created_at DESC, id ASC LIMIT ? OFFSET ?
                    """, submissionMapper, memberId, limit, offset);
        }
        return jdbcTemplate.query("""
                SELECT id, target_type, candidate::text AS candidate, description, evidence_url,
                       status, member_reason, created_at, updated_at
                  FROM submission WHERE member_id = ? AND status = ?
                 ORDER BY created_at DESC, id ASC LIMIT ? OFFSET ?
                """, submissionMapper, memberId, status.name(), limit, offset);
    }

    @Override
    public long countSubmissions(UUID memberId, ParticipationStatus status) {
        return count("submission", memberId, status);
    }

    @Override
    public Optional<ParticipationView.Submission> findSubmission(UUID memberId, UUID requestId) {
        return first(jdbcTemplate.query("""
                SELECT id, target_type, candidate::text AS candidate, description, evidence_url,
                       status, member_reason, created_at, updated_at
                  FROM submission WHERE member_id = ? AND id = ?
                """, submissionMapper, memberId, requestId));
    }

    @Override
    public List<ParticipationView.Report> findReports(
            UUID memberId, ParticipationStatus status, int limit, long offset
    ) {
        if (status == null) {
            return jdbcTemplate.query("""
                    SELECT id, target_type, target_id, report_type, description, evidence_url,
                           status, member_reason, created_at, updated_at
                      FROM report WHERE member_id = ?
                     ORDER BY created_at DESC, id ASC LIMIT ? OFFSET ?
                    """, reportMapper, memberId, limit, offset);
        }
        return jdbcTemplate.query("""
                SELECT id, target_type, target_id, report_type, description, evidence_url,
                       status, member_reason, created_at, updated_at
                  FROM report WHERE member_id = ? AND status = ?
                 ORDER BY created_at DESC, id ASC LIMIT ? OFFSET ?
                """, reportMapper, memberId, status.name(), limit, offset);
    }

    @Override
    public long countReports(UUID memberId, ParticipationStatus status) {
        return count("report", memberId, status);
    }

    @Override
    public Optional<ParticipationView.Report> findReport(UUID memberId, UUID requestId) {
        return first(jdbcTemplate.query("""
                SELECT id, target_type, target_id, report_type, description, evidence_url,
                       status, member_reason, created_at, updated_at
                  FROM report WHERE member_id = ? AND id = ?
                """, reportMapper, memberId, requestId));
    }

    private long count(String table, UUID memberId, ParticipationStatus status) {
        Long value;
        if (status == null) {
            value = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + table + " WHERE member_id = ?", Long.class, memberId);
        } else {
            value = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + table + " WHERE member_id = ? AND status = ?",
                    Long.class, memberId, status.name());
        }
        return value == null ? 0 : value;
    }

    private Map<String, Object> candidate(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored submission candidate is invalid", exception);
        }
    }

    private String json(Map<String, Object> candidate) {
        try {
            return objectMapper.writeValueAsString(candidate);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Submission candidate cannot be encoded", exception);
        }
    }

    private <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }
}
