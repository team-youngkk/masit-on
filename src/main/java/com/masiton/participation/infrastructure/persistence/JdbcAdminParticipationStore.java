package com.masiton.participation.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.participation.application.AdminParticipationView;
import com.masiton.participation.application.port.out.AdminParticipationStore;
import com.masiton.participation.domain.ModerationActionType;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;
import com.masiton.participation.domain.ReportType;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcAdminParticipationStore implements AdminParticipationStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAdminParticipationStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AdminParticipationView.Submission> findSubmissions(
            ParticipationStatus status, ParticipationTargetType targetType, int limit, long offset) {
        Query query = filtered("""
                SELECT id, member_id, target_type, candidate::text AS candidate, description, evidence_url,
                       status, member_reason, internal_note, result_action_type, result_target_type,
                       result_target_id, created_at, updated_at
                  FROM submission
                """, status, targetType);
        query.sql.append(" ORDER BY created_at ASC, id ASC LIMIT ? OFFSET ?");
        query.args.add(limit);
        query.args.add(offset);
        return jdbcTemplate.query(query.sql.toString(), this::submission, query.args.toArray());
    }

    @Override
    public long countSubmissions(ParticipationStatus status, ParticipationTargetType targetType) {
        return count("submission", status, targetType);
    }

    @Override
    public Optional<AdminParticipationView.Submission> findSubmission(UUID requestId, boolean lock) {
        String sql = """
                SELECT id, member_id, target_type, candidate::text AS candidate, description, evidence_url,
                       status, member_reason, internal_note, result_action_type, result_target_type,
                       result_target_id, created_at, updated_at
                  FROM submission WHERE id = ?
                """ + (lock ? " FOR UPDATE" : "");
        return jdbcTemplate.query(sql, this::submission, requestId).stream().findFirst()
                .map(value -> withSubmissionHistory(value, requestId));
    }

    @Override
    public List<AdminParticipationView.Report> findReports(
            ParticipationStatus status, ParticipationTargetType targetType, int limit, long offset) {
        Query query = filtered("""
                SELECT id, member_id, target_type, target_id, report_type, description, evidence_url,
                       status, member_reason, internal_note, result_action_type, result_target_type,
                       result_target_id, created_at, updated_at
                  FROM report
                """, status, targetType);
        query.sql.append(" ORDER BY created_at ASC, id ASC LIMIT ? OFFSET ?");
        query.args.add(limit);
        query.args.add(offset);
        return jdbcTemplate.query(query.sql.toString(), this::report, query.args.toArray());
    }

    @Override
    public long countReports(ParticipationStatus status, ParticipationTargetType targetType) {
        return count("report", status, targetType);
    }

    @Override
    public Optional<AdminParticipationView.Report> findReport(UUID requestId, boolean lock) {
        String sql = """
                SELECT id, member_id, target_type, target_id, report_type, description, evidence_url,
                       status, member_reason, internal_note, result_action_type, result_target_type,
                       result_target_id, created_at, updated_at
                  FROM report WHERE id = ?
                """ + (lock ? " FOR UPDATE" : "");
        return jdbcTemplate.query(sql, this::report, requestId).stream().findFirst()
                .map(value -> withReportHistory(value, requestId));
    }

    @Override
    public void updateSubmission(
            UUID requestId, ParticipationStatus status, String memberReason, String internalNote,
            AdminParticipationView.Result result, OffsetDateTime updatedAt, OffsetDateTime terminalAt) {
        update("submission", requestId, status, memberReason, internalNote, result, updatedAt, terminalAt);
    }

    @Override
    public void updateReport(
            UUID requestId, ParticipationStatus status, String memberReason, String internalNote,
            AdminParticipationView.Result result, OffsetDateTime updatedAt, OffsetDateTime terminalAt) {
        update("report", requestId, status, memberReason, internalNote, result, updatedAt, terminalAt);
    }

    @Override
    public void insertSubmissionHistory(
            UUID requestId, UUID adminId, ParticipationStatus fromStatus, ParticipationStatus toStatus,
            String memberReason, String internalNote, AdminParticipationView.Result result,
            String traceId, OffsetDateTime createdAt) {
        insertHistory("submission_id", requestId, adminId, fromStatus, toStatus,
                memberReason, internalNote, result, traceId, createdAt);
    }

    @Override
    public void insertReportHistory(
            UUID requestId, UUID adminId, ParticipationStatus fromStatus, ParticipationStatus toStatus,
            String memberReason, String internalNote, AdminParticipationView.Result result,
            String traceId, OffsetDateTime createdAt) {
        insertHistory("report_id", requestId, adminId, fromStatus, toStatus,
                memberReason, internalNote, result, traceId, createdAt);
    }

    private void update(
            String table, UUID requestId, ParticipationStatus status, String memberReason,
            String internalNote, AdminParticipationView.Result result,
            OffsetDateTime updatedAt, OffsetDateTime terminalAt) {
        jdbcTemplate.update("UPDATE " + table + " SET status = ?, member_reason = ?, internal_note = ?, "
                        + "result_action_type = ?, result_target_type = ?, result_target_id = ?, "
                        + "updated_at = ?, terminal_at = ? WHERE id = ?",
                status.name(), memberReason, internalNote, action(result), targetType(result), targetId(result),
                updatedAt, terminalAt, requestId);
    }

    private void insertHistory(
            String requestColumn, UUID requestId, UUID adminId, ParticipationStatus fromStatus,
            ParticipationStatus toStatus, String memberReason, String internalNote,
            AdminParticipationView.Result result, String traceId, OffsetDateTime createdAt) {
        jdbcTemplate.update("INSERT INTO moderation_history (id, " + requestColumn + ", admin_account_id, "
                        + "from_status, to_status, member_reason, internal_note, result_action_type, "
                        + "result_target_type, result_target_id, trace_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), requestId, adminId, fromStatus.name(), toStatus.name(), memberReason,
                internalNote, action(result), targetType(result), targetId(result), traceId, createdAt);
    }

    private long count(String table, ParticipationStatus status, ParticipationTargetType targetType) {
        Query query = filtered("SELECT count(*) FROM " + table, status, targetType);
        Long count = jdbcTemplate.queryForObject(query.sql.toString(), Long.class, query.args.toArray());
        return count == null ? 0 : count;
    }

    private Query filtered(String base, ParticipationStatus status, ParticipationTargetType targetType) {
        Query query = new Query(new StringBuilder(base), new ArrayList<>());
        if (status != null || targetType != null) {
            query.sql.append(" WHERE ");
            if (status != null) {
                query.sql.append("status = ?");
                query.args.add(status.name());
            }
            if (targetType != null) {
                if (status != null) query.sql.append(" AND ");
                query.sql.append("target_type = ?");
                query.args.add(targetType.name());
            }
        }
        return query;
    }

    private AdminParticipationView.Submission submission(ResultSet rs, int row) throws SQLException {
        return new AdminParticipationView.Submission(
                rs.getObject("id", UUID.class), rs.getObject("member_id", UUID.class),
                ParticipationTargetType.valueOf(rs.getString("target_type")), candidate(rs.getString("candidate")),
                rs.getString("description"), rs.getString("evidence_url"),
                ParticipationStatus.valueOf(rs.getString("status")), rs.getString("member_reason"),
                rs.getString("internal_note"), result(rs), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class), List.of());
    }

    private AdminParticipationView.Report report(ResultSet rs, int row) throws SQLException {
        return new AdminParticipationView.Report(
                rs.getObject("id", UUID.class), rs.getObject("member_id", UUID.class),
                ParticipationTargetType.valueOf(rs.getString("target_type")), rs.getObject("target_id", UUID.class),
                ReportType.valueOf(rs.getString("report_type")), rs.getString("description"),
                rs.getString("evidence_url"), ParticipationStatus.valueOf(rs.getString("status")),
                rs.getString("member_reason"), rs.getString("internal_note"), result(rs),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
                List.of());
    }

    private AdminParticipationView.Result result(ResultSet rs) throws SQLException {
        String action = rs.getString("result_action_type");
        return action == null ? null : new AdminParticipationView.Result(
                ModerationActionType.valueOf(action),
                ParticipationTargetType.valueOf(rs.getString("result_target_type")),
                rs.getObject("result_target_id", UUID.class));
    }

    private List<AdminParticipationView.History> history(String requestColumn, UUID requestId) {
        return jdbcTemplate.query("SELECT id, admin_account_id, from_status, to_status, member_reason, "
                        + "internal_note, result_action_type, result_target_type, result_target_id, trace_id, created_at "
                        + "FROM moderation_history WHERE " + requestColumn + " = ? ORDER BY created_at ASC, id ASC",
                (rs, row) -> new AdminParticipationView.History(
                        rs.getObject("id", UUID.class), rs.getObject("admin_account_id", UUID.class),
                        ParticipationStatus.valueOf(rs.getString("from_status")),
                        ParticipationStatus.valueOf(rs.getString("to_status")), rs.getString("member_reason"),
                        rs.getString("internal_note"), result(rs), rs.getString("trace_id"),
                        rs.getObject("created_at", OffsetDateTime.class)), requestId);
    }

    private AdminParticipationView.Submission withSubmissionHistory(
            AdminParticipationView.Submission value, UUID id) {
        return new AdminParticipationView.Submission(value.requestId(), value.memberId(), value.targetType(),
                value.candidate(), value.description(), value.evidenceUrl(), value.status(), value.memberReason(),
                value.internalNote(), value.result(), value.createdAt(), value.updatedAt(), history("submission_id", id));
    }

    private AdminParticipationView.Report withReportHistory(AdminParticipationView.Report value, UUID id) {
        return new AdminParticipationView.Report(value.requestId(), value.memberId(), value.targetType(),
                value.targetId(), value.reportType(), value.description(), value.evidenceUrl(), value.status(),
                value.memberReason(), value.internalNote(), value.result(), value.createdAt(), value.updatedAt(),
                history("report_id", id));
    }

    private Map<String, Object> candidate(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored submission candidate is invalid", exception);
        }
    }

    private String action(AdminParticipationView.Result result) {
        return result == null ? null : result.actionType().name();
    }

    private String targetType(AdminParticipationView.Result result) {
        return result == null ? null : result.targetType().name();
    }

    private UUID targetId(AdminParticipationView.Result result) {
        return result == null ? null : result.targetId();
    }

    private record Query(StringBuilder sql, List<Object> args) {
    }
}
