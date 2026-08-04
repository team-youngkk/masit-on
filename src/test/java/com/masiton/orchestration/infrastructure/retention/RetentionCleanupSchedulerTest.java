package com.masiton.orchestration.infrastructure.retention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.masiton.orchestration.application.retention.port.in.RetentionCleanupUseCase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("2차 확장 보존 정리 Scheduler")
class RetentionCleanupSchedulerTest {
    @Mock
    private RetentionCleanupUseCase cleanup;

    @InjectMocks
    private RetentionCleanupScheduler scheduler;

    @Test
    @DisplayName("각 스케줄은 대응하는 Application Command만 호출한다")
    void 각스케줄_대응Command호출() {
        scheduler.unlinkExpiredParticipationMembers();
        scheduler.deleteExpiredNotifications();

        verify(cleanup).unlinkExpiredParticipationMemberReferences();
        verify(cleanup).deleteExpiredNotifications();
    }

    @Test
    @DisplayName("정리 실패를 삼키지 않아 운영 실패 감지와 다음 실행 재시도를 허용한다")
    void 정리실패_예외전파() {
        given(cleanup.deleteExpiredNotifications()).willThrow(new IllegalStateException("injected failure"));

        assertThatThrownBy(scheduler::deleteExpiredNotifications)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected failure");
    }
}
