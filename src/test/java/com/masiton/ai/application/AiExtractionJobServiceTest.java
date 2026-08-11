package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.port.out.AiExtractionJobStore;
import com.masiton.ai.application.port.out.TemporaryInputCipher;
import com.masiton.ai.application.port.out.YoutubeChannelWatchStore;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;

@DisplayName("AI 추출 작업 접수 서비스")
class AiExtractionJobServiceTest {

    private final ResolveVerifiedVideoUseCase resolver = mock(ResolveVerifiedVideoUseCase.class);
    private final AiExtractionJobStore store = mock(AiExtractionJobStore.class);
    private final YoutubeChannelWatchStore watchStore = mock(YoutubeChannelWatchStore.class);
    private final TemporaryInputCipher cipher = mock(TemporaryInputCipher.class);
    private final AiExtractionJobService service = new AiExtractionJobService(resolver,
            new AiExtractionJobPersistenceService(store), watchStore, cipher);

    @Test
    @DisplayName("관리자 동일 요청의 기존 작업이 있으면 resolver 실패와 무관하게 재사용한다")
    void submitAdmin_기존작업존재_resolver실패와무관하게재사용한다() {
        AiExtractionJobView existing = new AiExtractionJobView(
                UUID.randomUUID(),
                "WEBHOOK",
                "channel-id",
                "video-id",
                "https://www.youtube.com/watch?v=video-id",
                "QUEUED",
                null,
                null,
                AiExtractionContract.PROVIDER,
                AiExtractionContract.MODEL_VERSION,
                AiExtractionContract.PROMPT_VERSION,
                AiExtractionContract.SCHEMA_VERSION,
                0,
                OffsetDateTime.parse("2026-08-10T00:00:00Z"),
                null,
                null,
                false
        );
        when(store.findByVideoIdAndInputMode(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(existing));

        AiExtractionJobView result = service.submitAdmin("https://youtu.be/video-id", null, "retry-key");

        assertThat(result.reused()).isTrue();
        assertThat(result.jobId()).isEqualTo(existing.jobId());
        assertThat(result.videoUrl()).isEqualTo("https://www.youtube.com/watch?v=video-id");
        verify(store).findByVideoIdAndInputMode(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(resolver);
    }

    @Test
    @DisplayName("비활성 채널 webhook은 작업을 만들지 않는다")
    void submitWebhook_비활성채널_작업을만들지않는다() {
        when(watchStore.find("channel-id")).thenReturn(Optional.of(
                new YoutubeChannelWatchStore.Watch("channel-id", false, "INACTIVE", null)));

        Optional<AiExtractionJobView> result = service.submitWebhook("channel-id", "video-id",
                URI.create("https://www.youtube.com/watch?v=video-id"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("동시 unique 충돌은 예외 없이 승자 작업을 재사용한다")
    void persistence_동시unique충돌_승자작업을재사용한다() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-10T00:00:00Z");
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-08-10T00:01:00Z");
        OffsetDateTime finishedAt = OffsetDateTime.parse("2026-08-10T00:02:00Z");
        AiExtractionJobView winner = new AiExtractionJobView(
                UUID.randomUUID(),
                "WEBHOOK",
                "channel-id",
                "video-id",
                "https://www.youtube.com/watch?v=video-id",
                "SUCCEEDED",
                "COMPLETE",
                "AUTO_CONFIRMED",
                AiExtractionContract.PROVIDER,
                AiExtractionContract.MODEL_VERSION,
                AiExtractionContract.PROMPT_VERSION,
                AiExtractionContract.SCHEMA_VERSION,
                1,
                createdAt,
                startedAt,
                finishedAt,
                false
        );
        AiExtractionJobStore.AiExtractionJobDraft draft = new AiExtractionJobStore.AiExtractionJobDraft(
                UUID.randomUUID(),
                "WEBHOOK",
                "REALTIME",
                "channel-id",
                "video-id",
                URI.create(winner.videoUrl()),
                "GEMINI_VIDEO_URL",
                new byte[32],
                AiExtractionContract.PROVIDER,
                AiExtractionContract.MODEL_VERSION,
                AiExtractionContract.PROMPT_VERSION,
                AiExtractionContract.SCHEMA_VERSION,
                createdAt
        );
        when(store.find(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(store.insert(any())).thenReturn(Optional.empty());

        AiExtractionJobView result = new AiExtractionJobPersistenceService(store).create(draft, Optional.empty());

        assertThat(result.reused()).isTrue();
        assertThat(result.jobId()).isEqualTo(winner.jobId());
        assertThat(result.resultCompleteness()).isEqualTo("COMPLETE");
        assertThat(result.reviewStatus()).isEqualTo("AUTO_CONFIRMED");
        assertThat(result.startedAt()).isEqualTo(startedAt);
        assertThat(result.finishedAt()).isEqualTo(finishedAt);
    }
}
