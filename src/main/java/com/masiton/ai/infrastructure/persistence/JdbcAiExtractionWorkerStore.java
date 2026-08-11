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

import com.masiton.ai.application.port.out.AiExtractionWorkerStore;
import com.masiton.ai.application.port.out.TemporaryInputCipher.EncryptedInput;

@Repository
public class JdbcAiExtractionWorkerStore implements AiExtractionWorkerStore {

    private static final long CLAIM_LOCK_KEY = 5_867_903_158L;
    private final JdbcTemplate jdbcTemplate;

    public JdbcAiExtractionWorkerStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ClaimedJob> claim(String workerId, OffsetDateTime now, OffsetDateTime leaseExpiresAt,
                                      int maxAttempts, OffsetDateTime quotaWindowStart, long quotaLimit) {
        List<ClaimedJob> rows = jdbcTemplate.query("""
                WITH quota_guard AS (
                    SELECT pg_advisory_xact_lock(?)
                ), usage AS (
                    SELECT (
                        SELECT count(*) FROM ai_extraction_attempt WHERE finished_at >= ?
                    ) + (
                        SELECT count(*) FROM ai_extraction_job job
                         WHERE job.execution_status = 'RUNNING'
                           AND NOT EXISTS (
                               SELECT 1 FROM ai_extraction_attempt attempt
                                WHERE attempt.job_id = job.id AND attempt.attempt_no = job.attempt_count
                           )
                    ) AS used
                    FROM quota_guard
                ), candidate AS (
                    SELECT job.id, job.execution_status, job.attempt_count,
                           job.started_at, job.lease_expires_at
                      FROM ai_extraction_job job, usage
                     WHERE usage.used < ?
                       AND job.attempt_count < ?
                       AND (job.execution_status = 'QUEUED'
                            OR (job.execution_status = 'RUNNING' AND job.lease_expires_at <= ?))
                     ORDER BY CASE WHEN job.execution_status = 'RUNNING' THEN 0 ELSE 1 END,
                              job.priority DESC, COALESCE(job.lease_expires_at, job.created_at), job.id
                     FOR UPDATE OF job SKIP LOCKED
                     LIMIT 1
                ), recovered AS (
                    INSERT INTO ai_extraction_attempt (
                        id, job_id, attempt_no, started_at, finished_at, outcome, error_category
                    )
                    SELECT gen_random_uuid(), candidate.id, candidate.attempt_count,
                           LEAST(candidate.started_at, candidate.lease_expires_at), ?, 'FAILED', 'LEASE_EXPIRED'
                      FROM candidate
                     WHERE candidate.execution_status = 'RUNNING'
                       AND candidate.attempt_count > 0
                       AND NOT EXISTS (
                           SELECT 1 FROM ai_extraction_attempt attempt
                            WHERE attempt.job_id = candidate.id
                              AND attempt.attempt_no = candidate.attempt_count
                       )
                    RETURNING job_id
                ), claimed AS (
                    UPDATE ai_extraction_job job
                       SET execution_status = 'RUNNING',
                           started_at = COALESCE(job.started_at, ?),
                           attempt_count = job.attempt_count + 1,
                           lease_owner = ?, lease_expires_at = ?
                      FROM candidate
                     WHERE job.id = candidate.id
                    RETURNING job.id, job.video_url, job.attempt_count
                )
                SELECT claimed.id, claimed.video_url, claimed.attempt_count,
                       input.ciphertext, input.encryption_key_id
                  FROM claimed
                  LEFT JOIN ai_extraction_temporary_input input ON input.job_id = claimed.id
                """, this::claimedJob, CLAIM_LOCK_KEY, quotaWindowStart, quotaLimit, maxAttempts, now,
                now, now, workerId, leaseExpiresAt);
        return rows.stream().findFirst();
    }

    @Override
    public boolean heartbeat(UUID jobId, String workerId, OffsetDateTime now, OffsetDateTime leaseExpiresAt) {
        return jdbcTemplate.update("""
                UPDATE ai_extraction_job
                   SET lease_expires_at = ?
                 WHERE id = ? AND execution_status = 'RUNNING'
                   AND lease_owner = ? AND lease_expires_at > ?
                """, leaseExpiresAt, jobId, workerId, now) == 1;
    }

    @Override
    public boolean recordRetryableFailure(UUID jobId, String workerId, int attemptNo,
                                          OffsetDateTime startedAt, OffsetDateTime finishedAt,
                                          String errorCategory) {
        return insertOwnedAttempt(jobId, workerId, attemptNo, startedAt, finishedAt,
                "FAILED", errorCategory, null);
    }

    @Override
    public Optional<Integer> beginRetry(UUID jobId, String workerId, OffsetDateTime now,
                                        OffsetDateTime leaseExpiresAt, int maxAttempts,
                                        OffsetDateTime quotaWindowStart, long quotaLimit) {
        List<Integer> rows = jdbcTemplate.query("""
                WITH quota_guard AS (
                    SELECT pg_advisory_xact_lock(?)
                ), usage AS (
                    SELECT (
                        SELECT count(*) FROM ai_extraction_attempt WHERE finished_at >= ?
                    ) + (
                        SELECT count(*) FROM ai_extraction_job job
                         WHERE job.execution_status = 'RUNNING'
                           AND NOT EXISTS (
                               SELECT 1 FROM ai_extraction_attempt attempt
                                WHERE attempt.job_id = job.id AND attempt.attempt_no = job.attempt_count
                           )
                    ) AS used
                    FROM quota_guard
                )
                UPDATE ai_extraction_job job
                   SET attempt_count = job.attempt_count + 1, lease_expires_at = ?
                  FROM usage
                 WHERE job.id = ? AND job.execution_status = 'RUNNING'
                   AND job.lease_owner = ? AND job.lease_expires_at > ?
                   AND job.attempt_count < ? AND usage.used < ?
                RETURNING job.attempt_count
                """, (rs, rowNum) -> rs.getInt(1), CLAIM_LOCK_KEY, quotaWindowStart,
                leaseExpiresAt, jobId, workerId, now, maxAttempts, quotaLimit);
        return rows.stream().findFirst();
    }

    @Override
    public boolean completeFailure(UUID jobId, String workerId, int attemptNo, OffsetDateTime startedAt,
                                   OffsetDateTime finishedAt, String errorCategory) {
        return completeFailureWithAttempt(jobId, workerId, attemptNo, startedAt, finishedAt, errorCategory);
    }

    @Override
    public boolean failWithoutAttempt(UUID jobId, String workerId, OffsetDateTime finishedAt,
                                      String errorCategory) {
        return jdbcTemplate.update("""
                UPDATE ai_extraction_job
                   SET execution_status = 'FAILED', finished_at = ?, error_category = ?,
                       lease_owner = NULL, lease_expires_at = NULL
                 WHERE id = ? AND execution_status = 'RUNNING' AND lease_owner = ?
                   AND lease_expires_at > ?
                """, finishedAt, errorCategory, jobId, workerId, finishedAt) == 1;
    }

    @Override
    public int failExpiredExhausted(OffsetDateTime now, int maxAttempts) {
        Integer changed = jdbcTemplate.queryForObject("""
                WITH exhausted AS (
                    SELECT job.id, job.attempt_count, job.started_at, job.lease_expires_at
                      FROM ai_extraction_job job
                     WHERE job.execution_status = 'RUNNING' AND job.lease_expires_at <= ?
                       AND job.attempt_count >= ?
                     FOR UPDATE
                ), recovered AS (
                    INSERT INTO ai_extraction_attempt (
                        id, job_id, attempt_no, started_at, finished_at, outcome, error_category
                    )
                    SELECT gen_random_uuid(), exhausted.id, exhausted.attempt_count,
                           LEAST(exhausted.started_at, exhausted.lease_expires_at), ?, 'FAILED', 'LEASE_EXPIRED'
                      FROM exhausted
                     WHERE NOT EXISTS (
                         SELECT 1 FROM ai_extraction_attempt attempt
                          WHERE attempt.job_id = exhausted.id
                            AND attempt.attempt_no = exhausted.attempt_count
                     )
                    RETURNING job_id
                ), failed AS (
                    UPDATE ai_extraction_job job
                       SET execution_status = 'FAILED', finished_at = ?,
                           error_category = COALESCE((
                               SELECT attempt.error_category FROM ai_extraction_attempt attempt
                                WHERE attempt.job_id = job.id AND attempt.outcome = 'FAILED'
                                ORDER BY attempt.attempt_no DESC LIMIT 1
                           ), 'LEASE_EXPIRED'), lease_owner = NULL, lease_expires_at = NULL
                      FROM exhausted
                     WHERE job.id = exhausted.id
                    RETURNING job.id
                )
                SELECT count(*) FROM failed
                """, Integer.class, now, maxAttempts, now, now);
        return changed == null ? 0 : changed;
    }

    @Override
    public long quotaUsage(OffsetDateTime quotaWindowStart) {
        Long usage = jdbcTemplate.queryForObject("""
                SELECT (SELECT count(*) FROM ai_extraction_attempt WHERE finished_at >= ?)
                     + (SELECT count(*) FROM ai_extraction_job job
                         WHERE job.execution_status = 'RUNNING'
                           AND NOT EXISTS (
                               SELECT 1 FROM ai_extraction_attempt attempt
                                WHERE attempt.job_id = job.id AND attempt.attempt_no = job.attempt_count
                           ))
                """, Long.class, quotaWindowStart);
        return usage == null ? 0 : usage;
    }

    private boolean completeFailureWithAttempt(UUID jobId, String workerId, int attemptNo,
                                               OffsetDateTime startedAt, OffsetDateTime finishedAt,
                                               String errorCategory) {
        Integer changed = jdbcTemplate.queryForObject("""
                WITH owned AS (
                    UPDATE ai_extraction_job
                       SET execution_status = 'FAILED', result_completeness = NULL, error_category = ?,
                           finished_at = ?, lease_owner = NULL, lease_expires_at = NULL
                     WHERE id = ? AND execution_status = 'RUNNING' AND lease_owner = ?
                       AND lease_expires_at > ?
                    RETURNING id
                ), recorded AS (
                    INSERT INTO ai_extraction_attempt (
                        id, job_id, attempt_no, provider_request_id, started_at, finished_at, outcome, error_category
                    )
                    SELECT gen_random_uuid(), owned.id, ?, ?, ?, ?, ?, ? FROM owned
                    RETURNING id
                )
                SELECT count(*) FROM recorded
                """, Integer.class, errorCategory, finishedAt, jobId, workerId, finishedAt, attemptNo,
                null, startedAt, finishedAt, "FAILED", errorCategory);
        return changed != null && changed == 1;
    }

    private boolean insertOwnedAttempt(UUID jobId, String workerId, int attemptNo,
                                       OffsetDateTime startedAt, OffsetDateTime finishedAt,
                                       String outcome, String errorCategory, String providerRequestId) {
        Integer changed = jdbcTemplate.queryForObject("""
                WITH owned AS (
                    SELECT id FROM ai_extraction_job
                     WHERE id = ? AND execution_status = 'RUNNING' AND lease_owner = ?
                       AND lease_expires_at > ?
                ), recorded AS (
                    INSERT INTO ai_extraction_attempt (
                        id, job_id, attempt_no, provider_request_id, started_at, finished_at, outcome, error_category
                    )
                    SELECT gen_random_uuid(), owned.id, ?, ?, ?, ?, ?, ? FROM owned
                    ON CONFLICT (job_id, attempt_no) DO NOTHING
                    RETURNING id
                )
                SELECT count(*) FROM recorded
                """, Integer.class, jobId, workerId, finishedAt, attemptNo, providerRequestId,
                startedAt, finishedAt, outcome, errorCategory);
        return changed != null && changed == 1;
    }

    private ClaimedJob claimedJob(ResultSet rs, int rowNum) throws SQLException {
        byte[] ciphertext = rs.getBytes("ciphertext");
        EncryptedInput input = ciphertext == null ? null
                : new EncryptedInput(ciphertext, rs.getString("encryption_key_id"));
        return new ClaimedJob(rs.getObject("id", UUID.class), URI.create(rs.getString("video_url")),
                input, rs.getInt("attempt_count"));
    }
}
