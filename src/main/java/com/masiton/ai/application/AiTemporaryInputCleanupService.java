package com.masiton.ai.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import com.masiton.ai.application.port.out.AiExtractionJobStore;
import com.masiton.ai.application.port.out.AiRegistrationUnitRetryStore;

@Service
public class AiTemporaryInputCleanupService {
    private final AiExtractionJobStore store;
    private final AiRegistrationUnitRetryStore retryStore;

    @Autowired
    public AiTemporaryInputCleanupService(AiExtractionJobStore store, AiRegistrationUnitRetryStore retryStore) {
        this.store = store;
        this.retryStore = retryStore;
    }

    public AiTemporaryInputCleanupService(AiExtractionJobStore store) {
        this.store = store;
        this.retryStore = null;
    }

    @Transactional
    public int deleteExpiredInputs() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int deleted = store.deleteExpiredTemporaryInputs(now);
        return deleted + (retryStore == null ? 0 : retryStore.deleteExpiredCiphertext(now));
    }
}
