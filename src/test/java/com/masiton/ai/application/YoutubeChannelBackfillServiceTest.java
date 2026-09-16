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
import static org.mockito.ArgumentMatchers.anyString;

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
        when(videos.query("channel-id", "page-1", 50))
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

        verify(jobs).submitBackfillIfClaimActive(runId, "owner", "channel-id", "video-1");
        verify(jobs).submitBackfillIfClaimActive(runId, "owner", "channel-id", "video-2");
        verify(runs).completePage(runId, "owner", 2, "page-2", false, false, now);
    }

    @Test
    @DisplayName("영상 상한에 걸린 페이지도 남은 수만 조회하고 다음 커서를 보존한다")
    void 폴링_영상상한도달_남은수만조회하고커서를보존한다() {
        properties.setEnabled(true);
        properties.setMaxVideosPerRun(75);
        UUID runId = UUID.randomUUID();
        YoutubeChannelBackfillRunStore.ClaimedRun claimed = new YoutubeChannelBackfillRunStore.ClaimedRun(
                runId, creatorId, "channel-id", "page-1", 1, 50, "owner");
        when(runs.claim(any(), any(), any())).thenReturn(Optional.of(claimed));
        when(runs.isClaimActive(any(), any(), any())).thenReturn(true);
        List<String> remaining = java.util.stream.IntStream.rangeClosed(1, 25)
                .mapToObj(index -> "video-" + index).toList();
        when(videos.query("channel-id", "page-1", 25))
                .thenReturn(new YoutubeChannelVideoQueryPort.VideoPage(remaining, "page-3"));
        AiExtractionJobView created = mock(AiExtractionJobView.class);
        when(created.reused()).thenReturn(false);
        when(jobs.submitBackfillIfClaimActive(any(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(created));

        service.poll();

        verify(videos).query("channel-id", "page-1", 25);
        verify(runs).completePage(runId, "owner", 25, "page-3", false, true, now);
    }

    @Test
    @DisplayName("페이지 중간 예외가 나도 이미 접수한 영상의 처리 결과를 기록한다")
    void 폴링_페이지중간예외_이미접수한영상결과를기록한다() {
        properties.setEnabled(true);
        UUID runId = UUID.randomUUID();
        YoutubeChannelBackfillRunStore.ClaimedRun claimed = new YoutubeChannelBackfillRunStore.ClaimedRun(
                runId, creatorId, "channel-id", null, 0, 0, "owner");
        when(runs.claim(any(), any(), any())).thenReturn(Optional.of(claimed));
        when(runs.isClaimActive(any(), any(), any())).thenReturn(true);
        when(videos.query("channel-id", null, 50))
                .thenReturn(new YoutubeChannelVideoQueryPort.VideoPage(List.of("video-1", "video-2"), "next"));
        AiExtractionJobView created = mock(AiExtractionJobView.class);
        when(created.reused()).thenReturn(false);
        when(jobs.submitBackfillIfClaimActive(runId, "owner", "channel-id", "video-1"))
                .thenReturn(Optional.of(created));
        when(jobs.submitBackfillIfClaimActive(runId, "owner", "channel-id", "video-2"))
                .thenThrow(new IllegalStateException("job persistence failed"));

        service.poll();

        verify(jobs).submitBackfillIfClaimActive(runId, "owner", "channel-id", "video-1");
        verify(runs).fail(runId, "owner", "BACKFILL_PROCESSING", now);
        verify(runs, org.mockito.Mockito.never()).completePage(any(), any(), anyInt(), any(), anyBoolean(),
                anyBoolean(), any());
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
        when(videos.query("channel-id", null, 50))
                .thenReturn(new YoutubeChannelVideoQueryPort.VideoPage(List.of("video-1"), null));

        service.poll();

        verifyNoInteractions(jobs);
        verify(runs, org.mockito.Mockito.never()).completePage(any(), any(), anyInt(), any(), anyBoolean(),
                anyBoolean(), any());
    }

    @Test
    @DisplayName("페이지 상한에 도달하면 외부 호출 없이 재개 가능한 사유로 중지한다")
    void 폴링_페이지상한도달_재개가능한사유로중지한다() {
        // Given
        properties.setEnabled(true);
        properties.setMaxPagesPerRun(10);
        UUID runId = UUID.randomUUID();
        when(runs.claim(any(), any(), any())).thenReturn(Optional.of(
                new YoutubeChannelBackfillRunStore.ClaimedRun(
                        runId, creatorId, "channel-id", "page-11", 10, 100, "owner")));

        // When
        service.poll();

        // Then
        verify(runs).stopAtLimit(runId, "owner", "MAX_PAGES_PER_RUN", now);
        verifyNoInteractions(videos, jobs);
    }

    @Test
    @DisplayName("페이지 중간에 lease를 잃으면 다음 영상과 페이지 완료를 진행하지 않는다")
    void 폴링_영상접수중Lease상실_추가접수를중단한다() {
        // Given
        properties.setEnabled(true);
        UUID runId = UUID.randomUUID();
        when(runs.claim(any(), any(), any())).thenReturn(Optional.of(
                new YoutubeChannelBackfillRunStore.ClaimedRun(
                        runId, creatorId, "channel-id", null, 0, 0, "owner")));
        when(runs.isClaimActive(any(), any(), any())).thenReturn(true);
        when(videos.query("channel-id", null, 50)).thenReturn(
                new YoutubeChannelVideoQueryPort.VideoPage(List.of("video-1", "video-2", "video-3"), "next"));
        when(jobs.submitBackfillIfClaimActive(runId, "owner", "channel-id", "video-1"))
                .thenReturn(Optional.of(mock(AiExtractionJobView.class)));
        when(jobs.submitBackfillIfClaimActive(runId, "owner", "channel-id", "video-2"))
                .thenReturn(Optional.empty());

        // When
        service.poll();

        // Then
        verify(jobs, org.mockito.Mockito.never())
                .submitBackfillIfClaimActive(runId, "owner", "channel-id", "video-3");
        verify(runs, org.mockito.Mockito.never()).completePage(any(), any(), anyInt(), any(), anyBoolean(),
                anyBoolean(), any());
    }

    @Test
    @DisplayName("자동 보정은 명시한 주기가 지난 채널의 실행을 만들고 외부 호출은 하지 않는다")
    void 자동접수_운영주기도래_실행만등록한다() {
        // Given
        properties.setEnabled(true);
        properties.setRunIntervalSeconds(3600);
        when(runs.findDueCreatorsForUpdate(now.minusHours(1), 20)).thenReturn(List.of(creatorId));
        Run run = new Run(UUID.randomUUID(), creatorId, "QUEUED", 0, 0, 0, null, now, now);
        when(runs.createOrReuse(creatorId, now))
                .thenReturn(Optional.of(new YoutubeChannelBackfillRunStore.StartRun(run, false)));

        // When
        service.scheduleDue();

        // Then
        verify(runs).createOrReuse(creatorId, now);
        verify(metrics).recordRunStart(false);
        verifyNoInteractions(videos, jobs);
    }

    @Test
    @DisplayName("비활성 자동 보정은 채널 조회나 실행 생성을 하지 않는다")
    void 자동접수_기능비활성_조회하지않는다() {
        // Given: 기본값은 비활성이다.
        // When
        service.scheduleDue();
        // Then
        verifyNoInteractions(runs, videos, jobs, metrics);
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
        when(videos.query("channel-id", null, 50)).thenThrow(new YoutubeChannelVideoQueryException("YOUTUBE_RATE_LIMIT"));

        service.poll();

        verify(runs).fail(runId, "owner", "YOUTUBE_RATE_LIMIT", now);
    }
}
