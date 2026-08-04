package com.masiton.common.idempotency.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import com.masiton.common.idempotency.application.port.out.IdempotencyRecordStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("멱등 기록 보존 정리 서비스")
class IdempotencyRecordCleanupServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-04T10:00:00Z");

    private final IdempotencyRecordStore store = mock(IdempotencyRecordStore.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final IdempotencyRecordCleanupService service = new IdempotencyRecordCleanupService(
            store, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC), transactionManager);

    @BeforeEach
    void setUpTransactionManager() {
        when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
    }

    @Test
    @DisplayName("고정 cutoff로 1000건씩 별도 커밋하고 마지막 부분 배치까지 정리한다")
    void 만료정리_1000건초과_배치별커밋한다() {
        // given
        when(store.deleteExpiredBatch(NOW, 1_000)).thenReturn(1_000, 250);

        // when
        int deletedCount = service.cleanupExpiredRecords();

        // then
        assertThat(deletedCount).isEqualTo(1_250);
        verify(store, times(2)).deleteExpiredBatch(NOW, 1_000);
        verify(transactionManager, times(2)).commit(transactionStatus);
    }

    @Test
    @DisplayName("정리 대상이 없어도 성공하고 같은 cutoff 재실행은 0건으로 수렴한다")
    void 만료정리_대상없음_멱등성공한다() {
        // given
        when(store.deleteExpiredBatch(NOW, 1_000)).thenReturn(0);

        // when
        int first = service.cleanupExpiredRecords();
        int second = service.cleanupExpiredRecords();

        // then
        assertThat(first).isZero();
        assertThat(second).isZero();
        verify(store, times(2)).deleteExpiredBatch(NOW, 1_000);
    }
}
