package com.masiton.personalization.infrastructure.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.personalization.application.port.in.CleanupRecentRestaurantViewsUseCase;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("최근 본 맛집 보존 정리 스케줄러")
class RecentRestaurantViewCleanupSchedulerTest {

    private final CleanupRecentRestaurantViewsUseCase cleanupUseCase =
            mock(CleanupRecentRestaurantViewsUseCase.class);
    private final RecentRestaurantViewCleanupScheduler scheduler =
            new RecentRestaurantViewCleanupScheduler(cleanupUseCase);

    @Test
    @DisplayName("스케줄 실행은 보존 정리 Command를 한 번 호출한다")
    void 스케줄실행_정상처리_Command를한번호출한다() {
        // given
        when(cleanupUseCase.cleanupExpiredViews()).thenReturn(2);

        // when
        scheduler.cleanup();

        // then
        verify(cleanupUseCase).cleanupExpiredViews();
    }

    @Test
    @DisplayName("정리 실패를 성공 처리하지 않고 다음 호출에서 다시 시도할 수 있다")
    void 스케줄실행_첫실패후_예외를전파하고다음실행에서재시도한다() {
        // given
        when(cleanupUseCase.cleanupExpiredViews())
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(1);

        // when & then
        assertThatThrownBy(scheduler::cleanup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        assertThatCode(scheduler::cleanup).doesNotThrowAnyException();
        verify(cleanupUseCase, times(2)).cleanupExpiredViews();
    }
}
