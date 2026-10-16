package com.masiton.ai.application.port.out;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** 단위별 자동 보완의 단일 claim과 암호화 입력 수명주기를 저장한다. */
public interface AiRegistrationUnitRetryStore {

    /** 같은 작업·원본 등록 단위에 대한 claim은 최대 한 번 성공한다. */
    Optional<RetryClaim> claim(RetryClaimRequest request);

    /** 재기동 시 동일 논리 단위의 기존 자동 보완 claim을 확인한다. */
    Optional<RetryClaim> findByJobAndCandidateUnitId(UUID jobId, String candidateUnitId);

    /** Provider 응답을 한 번만 암호화 checkpoint로 저장한다. 현재 lease·예약 시도 번호와 일치해야 한다. */
    boolean storeProviderResult(UUID retryId, UUID jobId, String workerId, int attemptNo,
                                String providerRequestId, OffsetDateTime attemptStartedAt,
                                byte[] ciphertext, String keyId, OffsetDateTime savedAt);

    /** 처리 종료를 기록하며 원본 행은 유지한다. */
    boolean complete(UUID retryId, String outcome, UUID resultSnapshotId, OffsetDateTime finishedAt);

    /** 종료되었거나 보존 기간이 지난 암호문만 제거한다. claim 이력은 남긴다. */
    int deleteExpiredCiphertext(OffsetDateTime now);

    record RetryClaimRequest(UUID id, UUID jobId, UUID sourceSnapshotId, UUID sourceRegistrationUnitId,
                             String candidateUnitId,
                             String failureReason, String approvedSourceIdentifiersJson, byte[] inputHash,
                             byte[] ciphertext, String encryptionKeyId, OffsetDateTime expiresAt,
                             OffsetDateTime createdAt) {
    }

    record RetryClaim(UUID id, UUID jobId, UUID sourceSnapshotId, UUID sourceRegistrationUnitId,
                      String candidateUnitId,
                      String failureReason, String approvedSourceIdentifiersJson, byte[] inputHash,
                      byte[] ciphertext, String encryptionKeyId, OffsetDateTime expiresAt,
                      Integer providerAttemptNo, String outcome, UUID resultSnapshotId, OffsetDateTime createdAt,
                      OffsetDateTime finishedAt, byte[] providerResultCiphertext, String providerResultKeyId,
                      OffsetDateTime providerResultAt, String providerAttemptOutcome) {
        public RetryClaim(UUID id, UUID jobId, UUID sourceSnapshotId, UUID sourceRegistrationUnitId,
                          String candidateUnitId, String failureReason, String approvedSourceIdentifiersJson,
                          byte[] inputHash, byte[] ciphertext, String encryptionKeyId, OffsetDateTime expiresAt,
                          String outcome, UUID resultSnapshotId, OffsetDateTime createdAt,
                          OffsetDateTime finishedAt) {
            this(id, jobId, sourceSnapshotId, sourceRegistrationUnitId, candidateUnitId, failureReason,
                    approvedSourceIdentifiersJson, inputHash, ciphertext, encryptionKeyId, expiresAt,
                    null, outcome, resultSnapshotId, createdAt, finishedAt, null, null, null, null);
        }

        public RetryClaim(UUID id, UUID jobId, UUID sourceSnapshotId, UUID sourceRegistrationUnitId,
                          String candidateUnitId, String failureReason, String approvedSourceIdentifiersJson,
                          byte[] inputHash, byte[] ciphertext, String encryptionKeyId, OffsetDateTime expiresAt,
                          Integer providerAttemptNo, String outcome, UUID resultSnapshotId, OffsetDateTime createdAt,
                          OffsetDateTime finishedAt) {
            this(id, jobId, sourceSnapshotId, sourceRegistrationUnitId, candidateUnitId, failureReason,
                    approvedSourceIdentifiersJson, inputHash, ciphertext, encryptionKeyId, expiresAt,
                    providerAttemptNo, outcome, resultSnapshotId, createdAt, finishedAt, null, null, null, null);
        }
    }
}
