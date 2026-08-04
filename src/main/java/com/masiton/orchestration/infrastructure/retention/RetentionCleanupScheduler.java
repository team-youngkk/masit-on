package com.masiton.orchestration.infrastructure.retention;

import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.masiton.orchestration.application.retention.port.in.RetentionCleanupUseCase;

@Component
public class RetentionCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupScheduler.class);

    private final RetentionCleanupUseCase cleanup;

    public RetentionCleanupScheduler(RetentionCleanupUseCase cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(
            cron = "${masiton.retention.participation-unlink.cron:0 0 4 * * *}",
            zone = "${masiton.retention.zone:Asia/Seoul}"
    )
    public void unlinkExpiredParticipationMembers() {
        execute("participation member unlink", cleanup::unlinkExpiredParticipationMemberReferences);
    }

    @Scheduled(
            cron = "${masiton.retention.notification-cleanup.cron:0 30 3 * * *}",
            zone = "${masiton.retention.zone:Asia/Seoul}"
    )
    public void deleteExpiredNotifications() {
        execute("notification retention cleanup", cleanup::deleteExpiredNotifications);
    }

    private void execute(String operation, IntSupplier action) {
        long startedAt = System.nanoTime();
        try {
            int processedCount = action.getAsInt();
            log.info("{} succeeded: processedCount={}, elapsedMs={}", operation, processedCount, elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            log.error("{} failed: elapsedMs={}", operation, elapsedMillis(startedAt), exception);
            throw exception;
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
