package com.masiton.personalization.infrastructure.scheduling;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.masiton.personalization.application.port.in.CleanupRecentRestaurantViewsUseCase;

@Component
public class RecentRestaurantViewCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecentRestaurantViewCleanupScheduler.class);

    private final CleanupRecentRestaurantViewsUseCase cleanupUseCase;

    public RecentRestaurantViewCleanupScheduler(CleanupRecentRestaurantViewsUseCase cleanupUseCase) {
        this.cleanupUseCase = cleanupUseCase;
    }

    @Scheduled(
            cron = "${masiton.personalization.recent-cleanup.cron:0 0 3 * * *}",
            zone = "${masiton.personalization.recent-cleanup.zone:UTC}"
    )
    public void cleanup() {
        long startedAt = System.nanoTime();
        try {
            int deletedCount = cleanupUseCase.cleanupExpiredViews();
            log.info(
                    "recent restaurant view cleanup succeeded: deletedCount={}, elapsedMs={}",
                    deletedCount,
                    elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            log.error(
                    "recent restaurant view cleanup failed: elapsedMs={}",
                    elapsedMillis(startedAt),
                    exception);
            throw exception;
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
