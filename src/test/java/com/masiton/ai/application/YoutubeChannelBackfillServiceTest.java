package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillRunStore;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillRunStore.Run;
import com.masiton.ai.application.port.out.YoutubeChannelVideoQueryPort;
import com.masiton.ai.application.port.in.AiExtractionJobUseCase;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillMetrics;
import com.masiton.ai.infrastructure.worker.YoutubeChannelBackfillProperties;
import com.masiton.common.web.BusinessException;

@DisplayName("YouTube 채널 백필 서비스")
class YoutubeChannelBackfillServiceTest {
    private final YoutubeChannelBackfillRunStore runs = mock(YoutubeChannelBackfillRunStore.class);
    private final YoutubeChannelVideoQueryPort videos = mock(YoutubeChannelVideoQueryPort.class);
    private final AiExtractionJobUseCase jobs = mock(AiExtractionJobUseCase.class);
    private final YoutubeChannelBackfillMetrics metrics = mock(YoutubeChannelBackfillMetrics.class);
    private final YoutubeChannelBackfillProperties properties = new YoutubeChannelBackfillProperties();
    private final UUID creatorId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.ofInstant(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC);
    private final YoutubeChannelBackfillService service = new YoutubeChannelBackfillService(runs,
            videos, jobs, properties,
            Clock.fixed(now.toInstant(), ZoneOffset.UTC), metrics);
    @Test @DisplayName("활성 검증 감시가 아니면 충돌 오류로 차단한다")
    void 접수_비활성감시_충돌오류로차단한다() {
        properties.setEnabled(true);
        when(runs.createOrReuse(creatorId, now)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.start(creatorId)).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).status()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test @DisplayName("활성 run은 새 작업을 만들지 않고 재사용한다")
    void 접수_활성run_재사용한다() {
        properties.setEnabled(true);
        UUID runId = UUID.randomUUID(); Run run = new Run(runId, creatorId, "RUNNING", 1, 1, 0, null, now, now);
        when(runs.createOrReuse(creatorId, now))
                .thenReturn(Optional.of(new YoutubeChannelBackfillRunStore.StartRun(run, true)));
        assertThat(service.start(creatorId)).extracting("runId", "reused").containsExactly(runId, true);
    }

    @Test
    @DisplayName("보정 기능이 비활성화되면 실행을 만들지 않는다")
    void 접수_보정기능비활성화_서비스불가오류로차단한다() {
        assertThatThrownBy(() -> service.start(creatorId)).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(runs, org.mockito.Mockito.never()).createOrReuse(any(), any());
    }

    @Test
    @DisplayName("한 페이지의 신규·중복 영상을 기존 AI 작업으로 수렴시키고 다음 커서를 저장한다")
    void 폴링_한페이지신규중복영상_집계와다음커서를저장한다() {
        properties.setEnabled(true);
        UUID runId = UUID.randomUUID();
        YoutubeChannelBackfillRunStore.ClaimedRun claimed = new YoutubeChannelBackfillRunStore.ClaimedRun(
                runId, creatorId, "channel-id", "page-1", 0, 0, "owner");
        when(runs.claim(any(), any(), any())).thenReturn(Optional.of(claimed));
        when(runs.isClaimActive(any(), any(), any())).thenReturn(true);
        when(videos.query("channel-id", "page-1"))
                .thenReturn(new YoutubeChannelVideoQueryPort.VideoPage(List.of("video-1", "video-2"), "page-2"));
        AiExtractionJobView created = mock(AiExtractionJobView.class);
        AiExtractionJobView reused = mock(AiExtractionJobView.class);
        when(created.reused()).thenReturn(false);
        when(reused.reused()).thenReturn(true);
        when(jobs.submitBackfillIfClaimActive(runId, "owner", "channel-id", "video-1"))
                .thenReturn(Optional.of(created));
        when(jobs.submitBackfillIfClaimActive(runId, "owner", "channel-id", "video-2"))
                .thenReturn(Optional.of(reused));

        service.poll();

        verify(runs).completePage(runId, "owner", 2, 1, 1, "page-2", false, now);
    }

    @Test
    @DisplayName("페이지 조회 중 run이 중지되면 영상을 접수하지 않는다")
    void 폴링_페이지조회중Run중지_영상접수를중단한다() {
        properties.setEnabled(true);
        UUID runId = UUID.randomUUID();
        YoutubeChannelBackfillRunStore.ClaimedRun claimed = new YoutubeChannelBackfillRunStore.ClaimedRun(
                runId, creatorId, "channel-id", null, 0, 0, "owner");
        when(runs.claim(any(), any(), any())).thenReturn(Optional.of(claimed));
        when(runs.isClaimActive(any(), any(), any())).thenReturn(true, false);
        when(videos.query("channel-id", null))
                .thenReturn(new YoutubeChannelVideoQueryPort.VideoPage(List.of("video-1"), null));

        service.poll();

        verifyNoInteractions(jobs);
        verify(runs, org.mockito.Mockito.never()).completePage(any(), any(), anyInt(), anyInt(), anyInt(), any(),
                anyBoolean(), any());
    }

    @Test
    @DisplayName("YouTube 목록 조회 실패는 외부 오류 범주로 run을 실패 처리한다")
    void 폴링_YouTube목록조회실패_오류범주로실패처리한다() {
        properties.setEnabled(true);
        UUID runId = UUID.randomUUID();
        YoutubeChannelBackfillRunStore.ClaimedRun claimed = new YoutubeChannelBackfillRunStore.ClaimedRun(
                runId, creatorId, "channel-id", null, 0, 0, "owner");
        when(runs.claim(any(), any(), any())).thenReturn(Optional.of(claimed));
        when(runs.isClaimActive(any(), any(), any())).thenReturn(true);
        when(videos.query("channel-id", null)).thenThrow(new YoutubeChannelVideoQueryException("YOUTUBE_RATE_LIMIT"));

        service.poll();

        verify(runs).fail(runId, "owner", "YOUTUBE_RATE_LIMIT", now);
    }
}
