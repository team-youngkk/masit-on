package com.masiton.common.idempotency.application.port.in;

import com.masiton.common.idempotency.application.IdempotencyExecutionResult;
import com.masiton.common.idempotency.application.IdempotencyRequest;
import com.masiton.common.idempotency.application.IdempotencyResponse;

public interface IdempotentCreationUseCase {

    /**
     * Executes the creation action and stores its successful response atomically. The caller must
     * not start a transaction before invoking this method because a unique-key loser has to roll
     * back the entire creation attempt before the winning record can be read in a new transaction.
     */
    IdempotencyExecutionResult execute(IdempotencyRequest request, CreationAction action);

    @FunctionalInterface
    interface CreationAction {

        IdempotencyResponse create();
    }
}
