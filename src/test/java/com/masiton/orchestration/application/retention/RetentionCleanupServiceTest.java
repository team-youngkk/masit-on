package com.masiton.orchestration.application.retention;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("2차 확장 보존 정리 서비스")
class RetentionCleanupServiceTest {
    private static final Instant NOW = Instant.parse("2028-02-29T19:00:00Z");

    @Mock
    private RetentionCleanupBatchCommand batches;

    private RetentionCleanupService service;

    @BeforeEach
    void setUp() {
        service = new RetentionCleanupService(batches, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("제보와 신고는 서울 시각 기준 1년 cutoff를 고정하고 1,000건씩 끝까지 연결 제거한다")
    void 참여연결제거_서울시각1년Cutoff_천건단위반복() {
        OffsetDateTime now = OffsetDateTime.parse("2028-03-01T04:00:00+09:00");
        OffsetDateTime cutoff = OffsetDateTime.parse("2027-03-01T04:00:00+09:00");
        given(batches.unlinkExpiredSubmissionMembers(cutoff, now)).willReturn(1_000, 1);
        given(batches.unlinkExpiredReportMembers(cutoff, now)).willReturn(0);

        int processed = service.unlinkExpiredParticipationMemberReferences();

        assertThat(processed).isEqualTo(1_001);
        verify(batches).unlinkExpiredReportMembers(cutoff, now);
    }

    @Test
    @DisplayName("알림은 고정한 90일 cutoff로 1,000건 미만 배치가 나올 때까지 삭제한다")
    void 알림_90일Cutoff_천건단위반복() {
        OffsetDateTime cutoff = OffsetDateTime.parse("2027-12-02T04:00:00+09:00");
        given(batches.deleteExpiredNotifications(cutoff)).willReturn(1_000, 1_000, 25);

        int processed = service.deleteExpiredNotifications();

        assertThat(processed).isEqualTo(2_025);
    }

    @Test
    @DisplayName("중간 알림 배치 실패를 성공으로 삼지 않고 다음 실행이 같은 cutoff 후보에 다시 수렴한다")
    void 알림_중간실패_다음실행재수렴() {
        OffsetDateTime cutoff = OffsetDateTime.parse("2027-12-02T04:00:00+09:00");
        given(batches.deleteExpiredNotifications(cutoff))
                .willReturn(1_000)
                .willThrow(new IllegalStateException("injected failure"))
                .willReturn(7);

        assertThatThrownBy(service::deleteExpiredNotifications)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected failure");
        assertThat(service.deleteExpiredNotifications()).isEqualTo(7);
    }
}
