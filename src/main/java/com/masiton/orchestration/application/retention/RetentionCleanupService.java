package com.masiton.orchestration.application.retention;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.masiton.orchestration.application.retention.port.in.RetentionCleanupUseCase;

@Service
public class RetentionCleanupService implements RetentionCleanupUseCase {
    static final int BATCH_SIZE = 1_000;
    private static final ZoneId RETENTION_ZONE = ZoneId.of("Asia/Seoul");

    private final RetentionCleanupBatchCommand batches;
    private final Clock clock;

    public RetentionCleanupService(
            RetentionCleanupBatchCommand batches,
            @Qualifier("retentionClock") Clock clock
    ) {
        this.batches = batches;
        this.clock = clock;
    }

    @Override
    public int unlinkExpiredParticipationMemberReferences() {
        OffsetDateTime now = OffsetDateTime.now(clock).atZoneSameInstant(RETENTION_ZONE).toOffsetDateTime();
        OffsetDateTime cutoff = now.minusYears(1);
        return drain(() -> batches.unlinkExpiredSubmissionMembers(cutoff, now))
                + drain(() -> batches.unlinkExpiredReportMembers(cutoff, now));
    }

    @Override
    public int deleteExpiredNotifications() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock)
                .atZoneSameInstant(RETENTION_ZONE)
                .minusDays(90)
                .toOffsetDateTime();
        return drain(() -> batches.deleteExpiredNotifications(cutoff));
    }

    private int drain(Supplier<Integer> batch) {
        int total = 0;
        int processed;
        do {
            processed = batch.get();
            total = Math.addExact(total, processed);
        } while (processed == BATCH_SIZE);
        return total;
    }
}
