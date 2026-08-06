package com.masiton.orchestration.application.retention;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.masiton.orchestration.application.retention.port.out.RetentionCleanupStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("2차 확장 보존 정리 배치 Command")
class RetentionCleanupBatchCommandTest {
    private static final OffsetDateTime CUTOFF = OffsetDateTime.parse("2026-08-04T04:00:00+09:00");

    @Mock
    private RetentionCleanupStore store;

    @InjectMocks
    private RetentionCleanupBatchCommand command;

    @Test
    @DisplayName("모든 정리 저장소 호출은 최대 1,000건으로 제한한다")
    void 모든정리_천건제한() {
        given(store.unlinkExpiredSubmissionMembers(CUTOFF, CUTOFF, 1_000)).willReturn(1);
        given(store.unlinkExpiredReportMembers(CUTOFF, CUTOFF, 1_000)).willReturn(2);
        given(store.deleteExpiredNotifications(CUTOFF, 1_000)).willReturn(3);

        assertThat(command.unlinkExpiredSubmissionMembers(CUTOFF, CUTOFF)).isEqualTo(1);
        assertThat(command.unlinkExpiredReportMembers(CUTOFF, CUTOFF)).isEqualTo(2);
        assertThat(command.deleteExpiredNotifications(CUTOFF)).isEqualTo(3);
    }
}
