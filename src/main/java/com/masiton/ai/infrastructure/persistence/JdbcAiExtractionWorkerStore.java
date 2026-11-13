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
                       AND NOT EXISTS (
                           SELECT 1 FROM ai_registration_unit_retry retry
                            WHERE retry.job_id = job.id AND retry.outcome = 'PENDING'
                       )
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
    public Optional<ClaimedJob> claimRecovery(String workerId, OffsetDateTime now,
                                               OffsetDateTime leaseExpiresAt) {
        List<ClaimedJob> rows = jdbcTemplate.query("""
                WITH candidate AS (
                    SELECT job.id, job.video_url, job.attempt_count
                      FROM ai_extraction_job job
                     WHERE job.execution_status = 'RUNNING' AND job.lease_expires_at <= ?
                       AND job.attempt_count BETWEEN 0 AND 4
                       AND EXISTS (
                           SELECT 1 FROM ai_registration_unit_retry retry
                           JOIN LATERAL (
                               SELECT snapshot.id
                                 FROM ai_candidate_snapshot snapshot
                                WHERE snapshot.job_id = job.id
                                ORDER BY snapshot.snapshot_version DESC, snapshot.created_at DESC, snapshot.id DESC
                                LIMIT 1
                           ) latest ON latest.id = retry.source_snapshot_id
                            WHERE retry.job_id = job.id AND retry.outcome = 'PENDING'
                       )
                     ORDER BY job.lease_expires_at, job.priority DESC, job.created_at, job.id
                     FOR UPDATE OF job SKIP LOCKED
                     LIMIT 1
                ), claimed AS (
                    UPDATE ai_extraction_job job
                       SET lease_owner = ?, lease_expires_at = ?
                      FROM candidate
                     WHERE job.id = candidate.id
                    RETURNING job.id, candidate.video_url, candidate.attempt_count
                )
                SELECT claimed.id, claimed.video_url, claimed.attempt_count,
                       NULL::bytea AS ciphertext, NULL::varchar AS encryption_key_id
                  FROM claimed
                """, this::claimedJob, now, workerId, leaseExpiresAt);
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
        return recordRetryableFailure(jobId, workerId, attemptNo, startedAt, finishedAt, errorCategory, null);
    }

    @Override
    public boolean recordRetryableFailure(UUID jobId, String workerId, int attemptNo,
                                          OffsetDateTime startedAt, OffsetDateTime finishedAt,
                                          String errorCategory, Integer httpStatus) {
        return insertOwnedAttempt(jobId, workerId, attemptNo, startedAt, finishedAt,
                "FAILED", errorCategory, null, httpStatus);
    }

    @Override
    public boolean recordSuccessfulSupplementAttempt(UUID jobId, String workerId, int attemptNo,
                                                      OffsetDateTime startedAt, OffsetDateTime finishedAt,
                                                      String providerRequestId) {
        Integer changed = jdbcTemplate.queryForObject("""
                WITH owned AS (
                    SELECT id FROM ai_extraction_job
                     WHERE id = ? AND execution_status = 'RUNNING' AND lease_owner = ?
                       AND lease_expires_at > ? AND attempt_count = ?
                ), recorded AS (
                    INSERT INTO ai_extraction_attempt (
                        id, job_id, attempt_no, provider_request_id, started_at, finished_at, outcome
                    )
                    SELECT gen_random_uuid(), owned.id, ?, ?, ?, ?, 'SUCCEEDED' FROM owned
                    RETURNING id
                )
                SELECT count(*) FROM recorded
                """, Integer.class, jobId, workerId, finishedAt, attemptNo, attemptNo,
                providerRequestId, startedAt, finishedAt);
        return changed != null && changed == 1;
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
    public Optional<Integer> reserveSupplementProviderCall(UUID jobId, String workerId, OffsetDateTime now,
                                                            OffsetDateTime leaseExpiresAt,
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
                   AND job.attempt_count < 4 AND usage.used < ?
                RETURNING job.attempt_count
                """, (rs, rowNum) -> rs.getInt(1), CLAIM_LOCK_KEY, quotaWindowStart,
                leaseExpiresAt, jobId, workerId, now, quotaLimit);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<Integer> reserveSupplementProviderCall(UUID jobId, String workerId, UUID retryId,
                                                            OffsetDateTime now, OffsetDateTime leaseExpiresAt,
                                                            OffsetDateTime quotaWindowStart, long quotaLimit) {
        List<Integer> rows = jdbcTemplate.query("""
                WITH quota_guard AS (
                    SELECT pg_advisory_xact_lock(?)
                ), claim_guard AS (
                    SELECT retry.id
                      FROM ai_registration_unit_retry retry
                     WHERE retry.id = ? AND retry.job_id = ? AND retry.outcome = 'PENDING'
                       AND (retry.provider_attempt_no IS NULL OR EXISTS (
                           SELECT 1 FROM ai_extraction_attempt previous_attempt
                            WHERE previous_attempt.job_id = retry.job_id
                              AND previous_attempt.attempt_no = retry.provider_attempt_no
                              AND previous_attempt.outcome = 'FAILED'
                       ))
                     FOR UPDATE
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
                ), reserved AS (
                    UPDATE ai_extraction_job job
                       SET attempt_count = job.attempt_count + 1, lease_expires_at = ?
                      FROM usage, claim_guard
                     WHERE job.id = ? AND job.execution_status = 'RUNNING'
                       AND job.lease_owner = ? AND job.lease_expires_at > ?
                       AND job.attempt_count < 4 AND usage.used < ?
                    RETURNING job.id, job.attempt_count
                ), linked AS (
                    UPDATE ai_registration_unit_retry retry
                       SET provider_attempt_no = reserved.attempt_count
                      FROM reserved, claim_guard
                     WHERE retry.id = claim_guard.id AND retry.job_id = reserved.id
                    RETURNING retry.provider_attempt_no
                )
                SELECT linked.provider_attempt_no FROM linked
                """, (rs, rowNum) -> rs.getInt(1), CLAIM_LOCK_KEY, retryId, jobId,
                quotaWindowStart, leaseExpiresAt, jobId, workerId, now, quotaLimit);
        return rows.stream().findFirst();
    }

    @Override
    public boolean completeFailure(UUID jobId, String workerId, int attemptNo, OffsetDateTime startedAt,
                                   OffsetDateTime finishedAt, String errorCategory) {
        return completeFailureWithAttempt(jobId, workerId, attemptNo, startedAt, finishedAt, errorCategory, null);
    }

    @Override
    public boolean completeFailure(UUID jobId, String workerId, int attemptNo, OffsetDateTime startedAt,
                                   OffsetDateTime finishedAt, String errorCategory, Integer httpStatus) {
        return completeFailureWithAttempt(jobId, workerId, attemptNo, startedAt, finishedAt, errorCategory, httpStatus);
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
    public int failQueuedForQuota(OffsetDateTime finishedAt) {
        return jdbcTemplate.update("""
                UPDATE ai_extraction_job
                   SET execution_status = 'FAILED', started_at = ?, finished_at = ?,
                       error_category = 'QUOTA_HARD_STOP'
                 WHERE execution_status = 'QUEUED'
                """, finishedAt, finishedAt);
    }

    @Override
    public int failExpiredExhausted(OffsetDateTime now, int maxAttempts) {
        Integer changed = jdbcTemplate.queryForObject("""
                WITH exhausted AS (
                    SELECT job.id, job.attempt_count, job.started_at, job.lease_expires_at
                      FROM ai_extraction_job job
                     WHERE job.execution_status = 'RUNNING' AND job.lease_expires_at <= ?
                       AND job.attempt_count >= ?
                       AND NOT EXISTS (
                           SELECT 1 FROM ai_registration_unit_retry retry
                            WHERE retry.job_id = job.id AND retry.outcome = 'PENDING'
                       )
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
                                               String errorCategory, Integer httpStatus) {
        Integer changed = jdbcTemplate.queryForObject("""
                WITH owned AS (
                    UPDATE ai_extraction_job
                       SET execution_status = 'FAILED', result_completeness = NULL, error_category = ?,
                           failure_http_status = ?,
                           finished_at = ?, lease_owner = NULL, lease_expires_at = NULL
                     WHERE id = ? AND execution_status = 'RUNNING' AND lease_owner = ?
                       AND lease_expires_at > ?
                    RETURNING id
                ), recorded AS (
                    INSERT INTO ai_extraction_attempt (
                        id, job_id, attempt_no, provider_request_id, started_at, finished_at, outcome, error_category,
                        failure_http_status
                    )
                    SELECT gen_random_uuid(), owned.id, ?, ?, ?, ?, ?, ?, ? FROM owned
                    RETURNING id
                )
                SELECT count(*) FROM recorded
                """, Integer.class, errorCategory, httpStatus, finishedAt, jobId, workerId, finishedAt, attemptNo,
                null, startedAt, finishedAt, "FAILED", errorCategory, httpStatus);
        return changed != null && changed == 1;
    }

    private boolean insertOwnedAttempt(UUID jobId, String workerId, int attemptNo,
                                       OffsetDateTime startedAt, OffsetDateTime finishedAt,
                                       String outcome, String errorCategory, String providerRequestId,
                                       Integer httpStatus) {
        Integer changed = jdbcTemplate.queryForObject("""
                WITH owned AS (
                    SELECT id FROM ai_extraction_job
                     WHERE id = ? AND execution_status = 'RUNNING' AND lease_owner = ?
                       AND lease_expires_at > ?
                ), recorded AS (
                    INSERT INTO ai_extraction_attempt (
                        id, job_id, attempt_no, provider_request_id, started_at, finished_at, outcome, error_category,
                        failure_http_status
                    )
                    SELECT gen_random_uuid(), owned.id, ?, ?, ?, ?, ?, ?, ? FROM owned
                    ON CONFLICT (job_id, attempt_no) DO NOTHING
                    RETURNING id
                )
                SELECT count(*) FROM recorded
                """, Integer.class, jobId, workerId, finishedAt, attemptNo, providerRequestId,
                startedAt, finishedAt, outcome, errorCategory, httpStatus);
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
