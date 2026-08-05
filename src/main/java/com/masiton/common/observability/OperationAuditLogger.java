package com.masiton.common.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 공통 운영 감사 로그를 트랜잭션 커밋 이후 기록한다. */
public final class OperationAuditLogger {

    private static final Logger AUDIT = LoggerFactory.getLogger("OPERATION_AUDIT");

    private OperationAuditLogger() {
    }

    public static void write(Entry entry) {
        Runnable logEntry = () -> AUDIT.info(
                "action={} actorType={} actorId={} targetType={} targetId={} before={} after={} reason={} relatedCount={} traceId={}",
                entry.action(), entry.actorType(), entry.actorId(), entry.targetType(), entry.targetId(),
                entry.before(), entry.after(), entry.reason(), entry.relatedCount(), entry.traceId());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logEntry.run();
                }
            });
            return;
        }
        logEntry.run();
    }

    public record Entry(
            String action,
            String actorType,
            Object actorId,
            String targetType,
            Object targetId,
            String before,
            String after,
            String reason,
            Integer relatedCount,
            String traceId) {
    }
}
