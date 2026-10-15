package com.masiton.ai.infrastructure.persistence;

import static com.masiton.test.IntegrationTestFixtures.sha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.masiton.ai.application.port.out.AiRegistrationUnitRetryStore;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;
import com.masiton.ai.application.port.out.AiExtractionWorkerStore;
import com.masiton.test.FullContextIntegrationTest;

@SpringBootTest
@DisplayName("AI 등록 단위 자동 보완 claim PostgreSQL 저장소")
class JdbcAiRegistrationUnitRetryStoreIntegrationTest extends FullContextIntegrationTest {

    @Autowired
    private AiRegistrationUnitRetryStore store;

    @Autowired
    private AiRegistrationUnitStore registrationUnitStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiExtractionWorkerStore workerStore;

    private UUID jobId;
    private UUID snapshotId;
    private UUID unitId;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        snapshotId = UUID.randomUUID();
        now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        jdbcTemplate.update("""
                INSERT INTO ai_extraction_job (
                    id, source, priority, youtube_channel_id, youtube_video_id, video_url,
                    input_mode, input_hash, provider, model_version, prompt_version, schema_version,
                    execution_status, attempt_count, created_at
                ) VALUES (?, 'WEBHOOK', 'REALTIME', ?, ?, ?, 'GEMINI_VIDEO_URL', ?, 'GOOGLE_GEMINI',
                          'gemini-3.5-flash-lite', 'P9', 'S3', 'QUEUED', 0, ?)
                """, jobId, "channel-" + jobId, "video-" + jobId,
                "https://www.youtube.com/watch?v=" + jobId, sha256(jobId.toString()), now);
        jdbcTemplate.update("""
                INSERT INTO ai_candidate_snapshot (
                    id, job_id, snapshot_version, candidate_fields, candidate_tags, field_confidences,
                    evidence, missing_fields, review_status, reviewed_at, created_at
                ) VALUES (?, ?, 1, '{}'::jsonb, '[]'::jsonb, '{}'::jsonb, '{}'::jsonb, '[]'::jsonb,
                          'AUTO_BLOCKED', ?, ?)
                """, snapshotId, jobId, now, now);
        unitId = registrationUnitStore.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "행복식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null,
                null, null, null, null, null, "WORKER", now));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM ai_registration_unit_retry WHERE job_id = ?", jobId);
        jdbcTemplate.update("DELETE FROM ai_extraction_job WHERE id = ?", jobId);
    }

    @Test
    @DisplayName("claim은 출처와 암호화 입력을 저장하고 성공 종료 후 암호문만 제거한다")
    void claim_출처와암호화입력저장_종료후암호문만삭제된다() {
        byte[] ciphertext = new byte[]{1, 3, 5, 7};
        AiRegistrationUnitRetryStore.RetryClaim claim = store.claim(request(UUID.randomUUID(), ciphertext))
                .orElseThrow();

        assertThat(claim.sourceSnapshotId()).isEqualTo(snapshotId);
        assertThat(claim.sourceRegistrationUnitId()).isEqualTo(unitId);
        assertThat(claim.candidateUnitId()).isEqualTo("candidate-1");
        assertThat(claim.approvedSourceIdentifiersJson()).contains("video-title", "unit-location");
        assertThat(claim.inputHash()).containsExactly(sha256("public-title-description-location"));
        assertThat(claim.ciphertext()).containsExactly(ciphertext);
        assertThat(claim.encryptionKeyId()).isEqualTo("test-key-v1");
        assertThat(store.findByJobAndCandidateUnitId(jobId, "candidate-1")).get()
                .extracting(AiRegistrationUnitRetryStore.RetryClaim::id).isEqualTo(claim.id());
        assertThat(claim.providerAttemptNo()).isNull();
        jdbcTemplate.update("""
                UPDATE ai_extraction_job
                   SET execution_status = 'RUNNING', started_at = ?, attempt_count = 1,
                       lease_owner = 'retry-worker', lease_expires_at = ?
                 WHERE id = ?
                """, now, now.plusMinutes(5), jobId);
        assertThat(workerStore.reserveSupplementProviderCall(jobId, "retry-worker", claim.id(), now.plusSeconds(1),
                now.plusMinutes(6), now.minusDays(1), 100)).contains(2);
        assertThat(workerStore.reserveSupplementProviderCall(jobId, "retry-worker", claim.id(), now.plusSeconds(2),
                now.plusMinutes(6), now.minusDays(1), 100)).isEmpty();
        jdbcTemplate.update("""
                INSERT INTO ai_extraction_attempt (
                    id, job_id, attempt_no, started_at, finished_at, outcome, error_category
                ) VALUES (?, ?, 2, ?, ?, 'FAILED', 'TIMEOUT')
                """, UUID.randomUUID(), jobId, now, now.plusSeconds(1));
        assertThat(store.findByJobAndCandidateUnitId(jobId, "candidate-1")).get()
                .extracting(AiRegistrationUnitRetryStore.RetryClaim::providerAttemptOutcome)
                .isEqualTo("FAILED");
        assertThat(workerStore.reserveSupplementProviderCall(jobId, "retry-worker", claim.id(), now.plusSeconds(2),
                now.plusMinutes(6), now.minusDays(1), 100)).contains(3);
        assertThat(store.storeProviderResult(claim.id(), jobId, "wrong-worker", 3,
                "request-3", now.plusSeconds(2), new byte[]{9, 8, 7}, "result-key-v1",
                now.plusSeconds(3))).isFalse();
        assertThat(store.storeProviderResult(claim.id(), jobId, "retry-worker", 3,
                "request-3", now.plusSeconds(2), new byte[]{9, 8, 7}, "result-key-v1",
                now.plusSeconds(3))).isTrue();
        assertThat(store.storeProviderResult(claim.id(), jobId, "retry-worker", 3,
                "request-duplicate", now.plusSeconds(2), new byte[]{6}, "result-key-v1",
                now.plusSeconds(3))).isFalse();
        assertThat(jdbcTemplate.queryForMap("""
                SELECT attempt_no, provider_request_id, outcome
                  FROM ai_extraction_attempt WHERE job_id = ? AND attempt_no = 3
                """, jobId)).containsEntry("attempt_no", 3)
                .containsEntry("provider_request_id", "request-3")
                .containsEntry("outcome", "SUCCEEDED");
        AiRegistrationUnitRetryStore.RetryClaim checkpoint = store
                .findByJobAndCandidateUnitId(jobId, "candidate-1").orElseThrow();
        assertThat(checkpoint.providerAttemptNo()).isEqualTo(3);
        assertThat(checkpoint.providerResultCiphertext()).containsExactly(9, 8, 7);
        assertThat(checkpoint.providerResultKeyId()).isEqualTo("result-key-v1");
        assertThat(checkpoint.providerResultAt()).isEqualTo(now.plusSeconds(3).withOffsetSameInstant(ZoneOffset.UTC));

        UUID resultSnapshotId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_candidate_snapshot (
                    id, job_id, snapshot_version, candidate_fields, candidate_tags, field_confidences,
                    evidence, missing_fields, review_status, reviewed_at, created_at
                ) VALUES (?, ?, 2, '{}'::jsonb, '[]'::jsonb, '{}'::jsonb, '{}'::jsonb, '[]'::jsonb,
                          'AUTO_BLOCKED', ?, ?)
                """, resultSnapshotId, jobId, now.plusSeconds(1), now.plusSeconds(1));
        assertThat(store.complete(claim.id(), "BLOCKED", resultSnapshotId, now.plusSeconds(1))).isTrue();
        assertThat(store.deleteExpiredCiphertext(now.plusSeconds(2))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT outcome || ':' || (ciphertext IS NULL)::text || ':' || (input_hash IS NOT NULL)::text
                       || ':' || (provider_result_ciphertext IS NULL)::text
                  FROM ai_registration_unit_retry WHERE id = ?
                """, String.class, claim.id())).isEqualTo("BLOCKED:true:true:true");
    }

    @Test
    @DisplayName("동시 claim은 같은 작업 등록 단위에서 한 건만 성공한다")
    void claim_동시요청_한번만획득된다() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return store.claim(request(UUID.randomUUID(), new byte[]{1}));
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return store.claim(request(UUID.randomUUID(), new byte[]{2}));
            });
            ready.await();
            start.countDown();
            assertThat(java.util.stream.Stream.of(first.get(), second.get()).filter(java.util.Optional::isPresent))
                    .hasSize(1);
        }
    }

    @Test
    @DisplayName("재추출로 새 등록 단위 행이 생겨도 같은 candidateUnitId는 다시 claim할 수 없다")
    void claim_재시작후다른등록단위행_논리단위claim은중복되지않는다() {
        store.claim(request(UUID.randomUUID(), unitId, new byte[]{1})).orElseThrow();
        UUID replacementUnitId = registrationUnitStore.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 2, "행복식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null,
                null, null, null, null, null, "WORKER", now.plusSeconds(1)));

        assertThat(store.claim(request(UUID.randomUUID(), replacementUnitId, new byte[]{2}))).isEmpty();
        assertThat(store.findByJobAndCandidateUnitId(jobId, "candidate-1")).isPresent();
    }

    @Test
    @DisplayName("성공 Attempt 삽입이 실패하면 Provider 응답 checkpoint도 저장되지 않는다")
    void storeProviderResult_성공시도중복_응답checkpoint도롤백된다() {
        UUID retryId = UUID.randomUUID();
        AiRegistrationUnitRetryStore.RetryClaim claim = store.claim(request(retryId, unitId, "candidate-rollback",
                new byte[]{1})).orElseThrow();
        jdbcTemplate.update("""
                UPDATE ai_extraction_job
                   SET execution_status = 'RUNNING', started_at = ?, attempt_count = 1,
                       lease_owner = 'retry-worker', lease_expires_at = ?
                 WHERE id = ?
                """, now, now.plusMinutes(5), jobId);
        assertThat(workerStore.reserveSupplementProviderCall(jobId, "retry-worker", retryId, now.plusSeconds(1),
                now.plusMinutes(6), now.minusDays(1), 100)).contains(2);
        jdbcTemplate.update("""
                INSERT INTO ai_extraction_attempt (
                    id, job_id, attempt_no, started_at, finished_at, outcome
                ) VALUES (?, ?, 2, ?, ?, 'SUCCEEDED')
                """, UUID.randomUUID(), jobId, now.plusSeconds(1), now.plusSeconds(2));

        assertThatThrownBy(() -> store.storeProviderResult(retryId, jobId, "retry-worker", 2,
                "request-duplicate-ledger", now.plusSeconds(1), new byte[]{5}, "result-key-v1",
                now.plusSeconds(2))).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT provider_result_ciphertext IS NULL FROM ai_registration_unit_retry WHERE id = ?
                """, Boolean.class, claim.id())).isTrue();
    }

    private AiRegistrationUnitRetryStore.RetryClaimRequest request(UUID retryId, byte[] ciphertext) {
        return request(retryId, unitId, "candidate-1", ciphertext);
    }

    private AiRegistrationUnitRetryStore.RetryClaimRequest request(UUID retryId, UUID sourceUnitId,
                                                                    byte[] ciphertext) {
        return request(retryId, sourceUnitId, "candidate-1", ciphertext);
    }

    private AiRegistrationUnitRetryStore.RetryClaimRequest request(UUID retryId, UUID sourceUnitId,
                                                                    String candidateUnitId, byte[] ciphertext) {
        return new AiRegistrationUnitRetryStore.RetryClaimRequest(retryId, jobId, snapshotId, sourceUnitId,
                candidateUnitId, "PLACE_NOT_FOUND", "[\"video-title\",\"video-description\",\"unit-location\"]",
                sha256("public-title-description-location"), ciphertext, "test-key-v1", now.plusHours(24), now);
    }
}
