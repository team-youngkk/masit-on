package com.masiton.common.idempotency.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.masiton.common.idempotency.application.port.in.IdempotentCreationUseCase;
import com.masiton.common.idempotency.application.port.out.IdempotencyRecordStore;

@Service
public class IdempotentCreationService implements IdempotentCreationUseCase {

    private static final int RETENTION_HOURS = 24;

    private final IdempotencyRecordStore store;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public IdempotentCreationService(
            IdempotencyRecordStore store,
            @Qualifier("idempotencyClock") Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.store = store;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public IdempotencyExecutionResult execute(IdempotencyRequest request, CreationAction action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Idempotent creation must own the surrounding transaction");
        }

        try {
            return requireResult(transactions.execute(status -> executeInTransaction(request, action)));
        } catch (IdempotencyRecordAlreadyExistsException exception) {
            return requireResult(transactions.execute(status -> replayWinner(request)));
        }
    }

    private IdempotencyExecutionResult executeInTransaction(
            IdempotencyRequest request,
            CreationAction action
    ) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<IdempotencyRecord> existing = store.find(request);
        if (existing.isPresent() && !existing.orElseThrow().isExpiredAt(now)) {
            return resolve(existing.orElseThrow(), request);
        }
        existing.ifPresent(record -> store.deleteIfExpired(request, now));

        IdempotencyResponse response = action.create();
        store.save(new IdempotencyRecord(
                UUID.randomUUID(),
                request.actorType(),
                request.actorId(),
                request.apiScope(),
                request.keyHash(),
                request.requestHash(),
                response,
                now,
                now.plusHours(RETENTION_HOURS)));
        return IdempotencyExecutionResult.created(response);
    }

    private IdempotencyExecutionResult replayWinner(IdempotencyRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        IdempotencyRecord winner = store.find(request)
                .filter(record -> !record.isExpiredAt(now))
                .orElseThrow(() -> new IllegalStateException(
                        "Winning idempotency record was not available after a unique-key conflict"));
        return resolve(winner, request);
    }

    private IdempotencyExecutionResult resolve(
            IdempotencyRecord record,
            IdempotencyRequest request
    ) {
        if (!record.hasSameRequest(request.requestHash())) {
            throw new IdempotencyKeyReusedException();
        }
        return IdempotencyExecutionResult.replayed(record.response());
    }

    private IdempotencyExecutionResult requireResult(IdempotencyExecutionResult result) {
        if (result == null) {
            throw new IllegalStateException("Idempotency transaction completed without a result");
        }
        return result;
    }
}
