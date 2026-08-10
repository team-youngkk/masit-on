package com.masiton.ai.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.masiton.ai.application.port.out.AiExtractionJobStore;

@Component
public class AiTemporaryInputCleanupScheduler {
    private final AiExtractionJobStore store;

    public AiTemporaryInputCleanupScheduler(AiExtractionJobStore store) {
        this.store = store;
    }

    @Scheduled(fixedDelayString = "PT15M")
    public void deleteExpiredInputs() {
        store.deleteExpiredTemporaryInputs(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
