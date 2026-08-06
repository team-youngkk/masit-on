package com.masiton.orchestration.application.retention;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.orchestration.application.retention.port.out.RetentionCleanupStore;

@Service
public class RetentionCleanupBatchCommand {
    private static final int BATCH_SIZE = 1_000;

    private final RetentionCleanupStore store;

    public RetentionCleanupBatchCommand(RetentionCleanupStore store) {
        this.store = store;
    }

    @Transactional
    public int unlinkExpiredSubmissionMembers(OffsetDateTime cutoff, OffsetDateTime unlinkedAt) {
        return store.unlinkExpiredSubmissionMembers(cutoff, unlinkedAt, BATCH_SIZE);
    }

    @Transactional
    public int unlinkExpiredReportMembers(OffsetDateTime cutoff, OffsetDateTime unlinkedAt) {
        return store.unlinkExpiredReportMembers(cutoff, unlinkedAt, BATCH_SIZE);
    }

    @Transactional
    public int deleteExpiredNotifications(OffsetDateTime cutoff) {
        return store.deleteExpiredNotifications(cutoff, BATCH_SIZE);
    }

}
