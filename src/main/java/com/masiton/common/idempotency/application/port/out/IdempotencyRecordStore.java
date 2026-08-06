package com.masiton.common.idempotency.application.port.out;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.masiton.common.idempotency.application.IdempotencyRecord;
import com.masiton.common.idempotency.application.IdempotencyRequest;

public interface IdempotencyRecordStore {

    Optional<IdempotencyRecord> find(IdempotencyRequest request);

    int deleteIfExpired(IdempotencyRequest request, OffsetDateTime cutoff);

    void save(IdempotencyRecord record);

    int deleteExpiredBatch(OffsetDateTime cutoff, int batchSize);
}
