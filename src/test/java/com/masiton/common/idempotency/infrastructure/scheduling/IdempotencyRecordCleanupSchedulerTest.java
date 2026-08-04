package com.masiton.common.idempotency.infrastructure.scheduling;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import com.masiton.common.idempotency.application.port.in.CleanupIdempotencyRecordsUseCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("멱등 기록 보존 정리 스케줄러")
class IdempotencyRecordCleanupSchedulerTest {

    private final CleanupIdempotencyRecordsUseCase cleanupUseCase =
            mock(CleanupIdempotencyRecordsUseCase.class);
    private final IdempotencyRecordCleanupScheduler scheduler =
            new IdempotencyRecordCleanupScheduler(cleanupUseCase);

    @Test
    @DisplayName("기본 스케줄은 매시 15분에 실행한다")
    void 스케줄설정_기본값_매시15분이다() throws NoSuchMethodException {
        // given
        Method cleanup = IdempotencyRecordCleanupScheduler.class.getMethod("cleanup");

        // when
        Scheduled scheduled = cleanup.getAnnotation(Scheduled.class);

        // then
        assertThat(scheduled.cron()).isEqualTo("${masiton.idempotency.cleanup.cron:0 15 * * * *}");
    }

    @Test
    @DisplayName("정리 실패를 성공 처리하지 않고 다음 실행에서 다시 시도한다")
    void 스케줄실행_첫실패후_예외전파하고재시도한다() {
        // given
        when(cleanupUseCase.cleanupExpiredRecords())
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(1);

        // when & then
        assertThatThrownBy(scheduler::cleanup)
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(scheduler::cleanup).doesNotThrowAnyException();
        verify(cleanupUseCase, times(2)).cleanupExpiredRecords();
    }
}
