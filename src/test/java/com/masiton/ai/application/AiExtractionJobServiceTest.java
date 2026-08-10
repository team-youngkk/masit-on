package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import com.masiton.video.application.port.out.VerifiedVideo;

@DisplayName("AI extraction job intake service")
class AiExtractionJobServiceTest {

    private final ResolveVerifiedVideoUseCase resolver = mock(ResolveVerifiedVideoUseCase.class);
    private final AiExtractionJobStore store = mock(AiExtractionJobStore.class);
    private final YoutubeChannelWatchStore watchStore = mock(YoutubeChannelWatchStore.class);
    private final TemporaryInputCipher cipher = mock(TemporaryInputCipher.class);
    private final AiExtractionJobService service = new AiExtractionJobService(resolver,
            new AiExtractionJobPersistenceService(store), watchStore, cipher);

    @Test
    @DisplayName("같은 영상 입력은 기존 queued job으로 수렴한다")
    void submitAdmin_동일영상입력_기존작업재사용() {
        VerifiedVideo verified = new VerifiedVideo("video-id", "channel-id", "title", null, "channel",
                "https://www.youtube.com/watch?v=video-id", null, OffsetDateTime.now());
        AiExtractionJobView existing = new AiExtractionJobView(UUID.randomUUID(), "WEBHOOK", "channel-id",
                "video-id", verified.sourceUrl(), "QUEUED", AiExtractionContract.PROVIDER,
                AiExtractionContract.MODEL_VERSION, AiExtractionContract.PROMPT_VERSION,
                AiExtractionContract.SCHEMA_VERSION, 0, OffsetDateTime.now(), false);
        when(resolver.resolve(any())).thenReturn(Optional.of(verified));
        when(store.find(any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(existing));

        AiExtractionJobView result = service.submitAdmin(verified.sourceUrl(), null, "retry-key");

        assertThat(result.reused()).isTrue();
        assertThat(result.jobId()).isEqualTo(existing.jobId());
        verify(store).find(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("비활성 채널 webhook은 작업을 만들지 않는다")
    void submitWebhook_비활성감시채널_작업미생성() {
        when(watchStore.find("channel-id")).thenReturn(Optional.of(
                new YoutubeChannelWatchStore.Watch("channel-id", false, "INACTIVE", null)));

        Optional<AiExtractionJobView> result = service.submitWebhook("channel-id", "video-id",
                URI.create("https://www.youtube.com/watch?v=video-id"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("동시 unique 충돌은 예외 없이 승자 작업을 재조회한다")
    void persistence_동시유니크충돌_승자재조회() {
        AiExtractionJobView winner = new AiExtractionJobView(UUID.randomUUID(), "WEBHOOK", "channel-id",
                "video-id", "https://www.youtube.com/watch?v=video-id", "QUEUED",
                AiExtractionContract.PROVIDER, AiExtractionContract.MODEL_VERSION,
                AiExtractionContract.PROMPT_VERSION, AiExtractionContract.SCHEMA_VERSION, 0,
                OffsetDateTime.now(), false);
        AiExtractionJobStore.AiExtractionJobDraft draft = new AiExtractionJobStore.AiExtractionJobDraft(
                UUID.randomUUID(), "WEBHOOK", "REALTIME", "channel-id", "video-id",
                URI.create(winner.videoUrl()), "GEMINI_VIDEO_URL", new byte[32],
                AiExtractionContract.PROVIDER, AiExtractionContract.MODEL_VERSION,
                AiExtractionContract.PROMPT_VERSION, AiExtractionContract.SCHEMA_VERSION, OffsetDateTime.now());
        when(store.find(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(store.insert(any())).thenReturn(Optional.empty());

        AiExtractionJobView result = new AiExtractionJobPersistenceService(store).create(draft, Optional.empty());

        assertThat(result.reused()).isTrue();
        assertThat(result.jobId()).isEqualTo(winner.jobId());
    }
}
