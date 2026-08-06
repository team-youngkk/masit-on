package com.masiton.common.idempotency.infrastructure.scheduling;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.masiton.common.idempotency.application.port.in.CleanupIdempotencyRecordsUseCase;

@Component
public class IdempotencyRecordCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyRecordCleanupScheduler.class);

    private final CleanupIdempotencyRecordsUseCase cleanupUseCase;

    public IdempotencyRecordCleanupScheduler(CleanupIdempotencyRecordsUseCase cleanupUseCase) {
        this.cleanupUseCase = cleanupUseCase;
    }

    @Scheduled(
            cron = "${masiton.idempotency.cleanup.cron:0 15 * * * *}",
            zone = "${masiton.idempotency.cleanup.zone:UTC}"
    )
    public void cleanup() {
        long startedAt = System.nanoTime();
        try {
            int deletedCount = cleanupUseCase.cleanupExpiredRecords();
            log.info(
                    "idempotency record cleanup succeeded: deletedCount={}, elapsedMs={}",
                    deletedCount,
                    elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            log.error(
                    "idempotency record cleanup failed: elapsedMs={}, failureType={}",
                    elapsedMillis(startedAt),
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
