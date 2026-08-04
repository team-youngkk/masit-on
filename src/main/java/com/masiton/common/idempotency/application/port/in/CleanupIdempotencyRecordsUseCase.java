package com.masiton.common.idempotency.application.port.in;

public interface CleanupIdempotencyRecordsUseCase {

    int cleanupExpiredRecords();
}
