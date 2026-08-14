package com.masiton.ai.infrastructure.persistence;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.masiton.ai.application.AiTemporaryInputCleanupService;

@Component
public class AiTemporaryInputCleanupScheduler {
    private final AiTemporaryInputCleanupService cleanupService;

    public AiTemporaryInputCleanupScheduler(AiTemporaryInputCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(fixedDelayString = "PT15M")
    public void deleteExpiredInputs() {
        cleanupService.deleteExpiredInputs();
    }
}
