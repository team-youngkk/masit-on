package com.masiton.common.idempotency.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.masiton.common.idempotency.application.port.in.CleanupIdempotencyRecordsUseCase;
import com.masiton.common.idempotency.application.port.out.IdempotencyRecordStore;

@Service
public class IdempotencyRecordCleanupService implements CleanupIdempotencyRecordsUseCase {

    static final int BATCH_SIZE = 1_000;

    private final IdempotencyRecordStore store;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public IdempotencyRecordCleanupService(
            IdempotencyRecordStore store,
            @Qualifier("idempotencyClock") Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.store = store;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public int cleanupExpiredRecords() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock);
        int totalDeleted = 0;
        int deleted;
        do {
            deleted = requireCount(transactions.execute(
                    status -> store.deleteExpiredBatch(cutoff, BATCH_SIZE)));
            totalDeleted = Math.addExact(totalDeleted, deleted);
        } while (deleted == BATCH_SIZE);
        return totalDeleted;
    }

    private int requireCount(Integer count) {
        if (count == null) {
            throw new IllegalStateException("Idempotency cleanup transaction completed without a result");
        }
        return count;
    }
}
