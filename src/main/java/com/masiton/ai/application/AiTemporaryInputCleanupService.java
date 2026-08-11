package com.masiton.ai.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.out.AiExtractionJobStore;

@Service
public class AiTemporaryInputCleanupService {
    private final AiExtractionJobStore store;

    public AiTemporaryInputCleanupService(AiExtractionJobStore store) {
        this.store = store;
    }

    @Transactional
    public int deleteExpiredInputs() {
        return store.deleteExpiredTemporaryInputs(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
