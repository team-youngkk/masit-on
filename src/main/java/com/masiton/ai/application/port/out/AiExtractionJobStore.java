package com.masiton.ai.application.port.out;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import com.masiton.ai.application.port.out.dto.AiExtractionJobView;

public interface AiExtractionJobStore {
    Optional<AiExtractionJobView> findByVideoIdAndInputMode(String videoId, String inputMode,
                                                            String provider, String modelVersion,
                                                            String promptVersion, String schemaVersion);

    Optional<AiExtractionJobView> findByVideoIdAndInputHash(String videoId, byte[] inputHash,
                                                            String provider, String modelVersion,
                                                            String promptVersion, String schemaVersion);

    Optional<AiExtractionJobView> find(String channelId, String videoId, byte[] inputHash,
                                       String provider, String modelVersion, String promptVersion, String schemaVersion);

    Optional<AiExtractionJobView> insert(AiExtractionJobDraft draft);

    void storeTemporaryInput(UUID jobId, byte[] ciphertext, String encryptionKeyId, java.time.OffsetDateTime expiresAt);

    int deleteTemporaryInput(UUID jobId);

    int deleteExpiredTemporaryInputs(java.time.OffsetDateTime cutoff);

    record AiExtractionJobDraft(UUID jobId, String source, String priority, String channelId, String videoId,
                                URI videoUrl, String inputMode, byte[] inputHash, String provider,
                                String modelVersion, String promptVersion, String schemaVersion,
                                java.time.OffsetDateTime createdAt, String retryReason) {
        public AiExtractionJobDraft(UUID jobId, String source, String priority, String channelId, String videoId,
                                    URI videoUrl, String inputMode, byte[] inputHash, String provider,
                                    String modelVersion, String promptVersion, String schemaVersion,
                                    java.time.OffsetDateTime createdAt) {
            this(jobId, source, priority, channelId, videoId, videoUrl, inputMode, inputHash, provider,
                    modelVersion, promptVersion, schemaVersion, createdAt, null);
        }
    }
}
