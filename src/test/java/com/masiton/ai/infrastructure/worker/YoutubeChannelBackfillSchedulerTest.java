package com.masiton.ai.infrastructure.worker;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.port.in.YoutubeChannelBackfillUseCase;

@DisplayName("YouTube 자동 보정 스케줄러")
class YoutubeChannelBackfillSchedulerTest {
    @Test
    @DisplayName("주기별 실행을 먼저 접수한 뒤 기존 페이지 워커를 호출한다")
    void 폴링_예약도래_자동접수후페이지처리한다() {
        // Given
        YoutubeChannelBackfillUseCase backfill = mock(YoutubeChannelBackfillUseCase.class);
        YoutubeChannelBackfillScheduler scheduler = new YoutubeChannelBackfillScheduler(backfill);
        // When
        scheduler.poll();
        // Then
        var order = inOrder(backfill);
        order.verify(backfill).scheduleDue();
        order.verify(backfill).poll();
    }
}
