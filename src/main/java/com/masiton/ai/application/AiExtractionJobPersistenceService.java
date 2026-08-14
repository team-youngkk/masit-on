package com.masiton.ai.application;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.out.AiExtractionJobStore;
import com.masiton.ai.application.port.out.TemporaryInputCipher.EncryptedInput;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;

@Service
public class AiExtractionJobPersistenceService {

    private final AiExtractionJobStore store;

    public AiExtractionJobPersistenceService(AiExtractionJobStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public Optional<AiExtractionJobView> findByVideoIdAndInputMode(String videoId, String inputMode,
                                                                    String provider, String modelVersion,
                                                                    String promptVersion, String schemaVersion) {
        return store.findByVideoIdAndInputMode(videoId, inputMode, provider, modelVersion, promptVersion, schemaVersion)
                .map(this::reused);
    }

    @Transactional(readOnly = true)
    public Optional<AiExtractionJobView> findByVideoIdAndInputHash(String videoId, byte[] inputHash,
                                                                    String provider, String modelVersion,
                                                                    String promptVersion, String schemaVersion) {
        return store.findByVideoIdAndInputHash(videoId, inputHash, provider, modelVersion, promptVersion, schemaVersion)
                .map(this::reused);
    }

    @Transactional
    public AiExtractionJobView create(AiExtractionJobStore.AiExtractionJobDraft draft,
                                      Optional<EncryptedInput> temporaryInput) {
        Optional<AiExtractionJobView> existing = store.find(draft.channelId(), draft.videoId(), draft.inputHash(),
                draft.provider(), draft.modelVersion(), draft.promptVersion(), draft.schemaVersion());
        if (existing.isPresent()) {
            return reused(existing.get());
        }
        Optional<AiExtractionJobView> inserted = store.insert(draft);
        if (inserted.isEmpty()) {
            AiExtractionJobView concurrent = store.find(draft.channelId(), draft.videoId(), draft.inputHash(),
                    draft.provider(), draft.modelVersion(), draft.promptVersion(), draft.schemaVersion())
                    .orElseThrow(() -> new IllegalStateException("AI extraction job conflict winner was not found."));
            return reused(concurrent);
        }
        if (temporaryInput.isPresent()) {
            EncryptedInput input = temporaryInput.get();
            store.storeTemporaryInput(inserted.get().jobId(), input.ciphertext(), input.keyId(),
                    inserted.get().createdAt().plusHours(24));
        }
        return inserted.get();
    }

    private AiExtractionJobView reused(AiExtractionJobView view) {
        return new AiExtractionJobView(view.jobId(), view.source(), view.channelId(), view.videoId(), view.videoUrl(),
                view.executionStatus(), view.resultCompleteness(), view.reviewStatus(), view.provider(),
                view.modelVersion(), view.promptVersion(), view.schemaVersion(), view.attemptCount(),
                view.createdAt(), view.startedAt(), view.finishedAt(), true);
    }
}
