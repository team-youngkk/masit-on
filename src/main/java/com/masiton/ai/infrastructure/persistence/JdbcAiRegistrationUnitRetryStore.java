package com.masiton.ai.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.ai.application.port.out.AiRegistrationUnitRetryStore;

@Repository
public class JdbcAiRegistrationUnitRetryStore implements AiRegistrationUnitRetryStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAiRegistrationUnitRetryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RetryClaim> claim(RetryClaimRequest request) {
        List<RetryClaim> rows = jdbcTemplate.query("""
                INSERT INTO ai_registration_unit_retry (
                    id, job_id, source_snapshot_id, source_registration_unit_id, candidate_unit_id, failure_reason,
                    approved_source_identifiers, input_hash, ciphertext, encryption_key_id, expires_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                ON CONFLICT ON CONSTRAINT ux_ai_registration_unit_retry__job_candidate_unit DO NOTHING
                RETURNING id, job_id, source_snapshot_id, source_registration_unit_id, failure_reason,
                          candidate_unit_id, approved_source_identifiers::text, input_hash, ciphertext, encryption_key_id,
                          expires_at, provider_attempt_no, outcome, result_snapshot_id, created_at, finished_at,
                          provider_result_ciphertext, provider_result_key_id, provider_result_at,
                          NULL::text AS provider_attempt_outcome
                """, this::map, request.id(), request.jobId(), request.sourceSnapshotId(),
                request.sourceRegistrationUnitId(), request.candidateUnitId(), request.failureReason(),
                request.approvedSourceIdentifiersJson(),
                request.inputHash(), request.ciphertext(), request.encryptionKeyId(), request.expiresAt(),
                request.createdAt());
        return rows.stream().findFirst();
    }

    @Override
    public Optional<RetryClaim> findByJobAndCandidateUnitId(UUID jobId, String candidateUnitId) {
        List<RetryClaim> rows = jdbcTemplate.query("""
                SELECT retry.id, retry.job_id, retry.source_snapshot_id, retry.source_registration_unit_id,
                       retry.candidate_unit_id, retry.failure_reason,
                       retry.approved_source_identifiers::text, retry.input_hash, retry.ciphertext,
                       retry.encryption_key_id, retry.expires_at, retry.provider_attempt_no, retry.outcome,
                       retry.result_snapshot_id, retry.created_at, retry.finished_at,
                       retry.provider_result_ciphertext, retry.provider_result_key_id,
                       provider_result_at, attempt.outcome AS provider_attempt_outcome
                  FROM ai_registration_unit_retry retry
                  LEFT JOIN ai_extraction_attempt attempt
                    ON attempt.job_id = retry.job_id AND attempt.attempt_no = retry.provider_attempt_no
                 WHERE retry.job_id = ? AND retry.candidate_unit_id = ?
                """, this::map, jobId, candidateUnitId);
        return rows.stream().findFirst();
    }

    @Override
    public boolean storeProviderResult(UUID retryId, UUID jobId, String workerId, int attemptNo,
                                       String providerRequestId, java.time.OffsetDateTime attemptStartedAt,
                                       byte[] ciphertext, String keyId,
                                       java.time.OffsetDateTime savedAt) {
        Integer recorded = jdbcTemplate.queryForObject("""
                WITH claim_guard AS (
                    SELECT retry.id, retry.job_id
                      FROM ai_registration_unit_retry retry
                     WHERE retry.id = ? AND retry.job_id = ? AND retry.outcome = 'PENDING'
                       AND retry.provider_attempt_no = ? AND retry.provider_result_ciphertext IS NULL
                     FOR UPDATE
                ), owned AS (
                    SELECT job.id
                      FROM ai_extraction_job job
                      JOIN claim_guard ON claim_guard.job_id = job.id
                     WHERE job.execution_status = 'RUNNING' AND job.lease_owner = ?
                       AND job.lease_expires_at > ? AND job.attempt_count = ?
                ), recorded AS (
                    INSERT INTO ai_extraction_attempt (
                        id, job_id, attempt_no, provider_request_id, started_at, finished_at, outcome
                    )
                    SELECT gen_random_uuid(), owned.id, ?, ?, ?, ?, 'SUCCEEDED' FROM owned
                    RETURNING job_id, attempt_no
                ), stored AS (
                    UPDATE ai_registration_unit_retry retry
                       SET provider_result_ciphertext = ?, provider_result_key_id = ?, provider_result_at = ?
                      FROM claim_guard, recorded
                     WHERE retry.id = claim_guard.id AND recorded.job_id = retry.job_id
                       AND recorded.attempt_no = retry.provider_attempt_no
                    RETURNING recorded.attempt_no
                )
                SELECT count(*) FROM stored
                """, Integer.class, retryId, jobId, attemptNo, workerId, savedAt, attemptNo,
                attemptNo, providerRequestId, attemptStartedAt, savedAt, ciphertext, keyId, savedAt);
        return recorded != null && recorded == 1;
    }

    @Override
    public boolean complete(UUID retryId, String outcome, UUID resultSnapshotId,
                             java.time.OffsetDateTime finishedAt) {
        return jdbcTemplate.update("""
                UPDATE ai_registration_unit_retry
                   SET outcome = ?, result_snapshot_id = ?, finished_at = ?
                 WHERE id = ? AND outcome = 'PENDING'
                """, outcome, resultSnapshotId, finishedAt, retryId) == 1;
    }

    @Override
    public int deleteExpiredCiphertext(java.time.OffsetDateTime now) {
        return jdbcTemplate.update("""
                UPDATE ai_registration_unit_retry
                   SET ciphertext = NULL, encryption_key_id = NULL,
                       provider_result_ciphertext = NULL, provider_result_key_id = NULL, provider_result_at = NULL
                 WHERE (ciphertext IS NOT NULL OR provider_result_ciphertext IS NOT NULL)
                   AND (outcome IN ('SUCCEEDED', 'BLOCKED') OR expires_at <= ?)
                """, now);
    }

    private RetryClaim map(ResultSet rs, int rowNum) throws SQLException {
        Number providerAttemptNo = (Number) rs.getObject("provider_attempt_no");
        return new RetryClaim(rs.getObject("id", UUID.class), rs.getObject("job_id", UUID.class),
                rs.getObject("source_snapshot_id", UUID.class),
                rs.getObject("source_registration_unit_id", UUID.class), rs.getString("candidate_unit_id"),
                rs.getString("failure_reason"),
                rs.getString("approved_source_identifiers"), rs.getBytes("input_hash"),
                rs.getBytes("ciphertext"), rs.getString("encryption_key_id"),
                rs.getObject("expires_at", java.time.OffsetDateTime.class),
                providerAttemptNo == null ? null : providerAttemptNo.intValue(), rs.getString("outcome"),
                rs.getObject("result_snapshot_id", UUID.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("finished_at", java.time.OffsetDateTime.class),
                rs.getBytes("provider_result_ciphertext"), rs.getString("provider_result_key_id"),
                rs.getObject("provider_result_at", java.time.OffsetDateTime.class),
                rs.getString("provider_attempt_outcome"));
    }
}
