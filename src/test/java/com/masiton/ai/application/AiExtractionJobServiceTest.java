package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.ArgumentCaptor;

import com.masiton.ai.application.port.out.AiExtractionJobStore;
import com.masiton.ai.application.port.out.TemporaryInputCipher;
import com.masiton.ai.application.port.out.YoutubeChannelWatchStore;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;

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
        when(store.findByVideoIdAndInputHash(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(existing));

        AiExtractionJobView result = service.submitAdmin("https://youtu.be/video-id", null, "retry-key");

        assertThat(result.reused()).isTrue();
        assertThat(result.jobId()).isEqualTo(existing.jobId());
        assertThat(result.videoUrl()).isEqualTo("https://www.youtube.com/watch?v=video-id");
        verify(store).findByVideoIdAndInputHash(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(resolver);
    }

    @Test
    @DisplayName("멱등성 키가 있으면 다른 payload의 영상 모드 작업을 재사용하지 않는다")
    void submitAdmin_멱등성키와다른payload_영상모드작업을재사용하지않는다() {
        AiExtractionJobView existing = queuedJob("https://www.youtube.com/watch?v=video-id");
        when(store.findByVideoIdAndInputMode(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(existing));
        when(store.findByVideoIdAndInputHash(any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(resolver.resolve(any())).thenReturn(Optional.of(verifiedVideo()));
        when(cipher.encrypt(any())).thenReturn(new TemporaryInputCipher.EncryptedInput(new byte[]{1, 2}, "key-1"));
        AiExtractionJobView created = queuedJob("https://www.youtube.com/watch?v=video-id");
        when(store.insert(any())).thenReturn(Optional.of(created));

        AiExtractionJobView result = service.submitAdmin(
                "https://www.youtube.com/watch?v=video-id", "다른 payload", "idempotency-key");

        assertThat(result.jobId()).isEqualTo(created.jobId());
        verify(store).findByVideoIdAndInputHash(any(), any(), any(), any(), any(), any());
        verify(resolver).resolve(URI.create("https://www.youtube.com/watch?v=video-id"));
    }

    @Test
    @DisplayName("보완 텍스트는 trim 후 2만 자까지 암호화 임시 입력으로 저장한다")
    void submitAdmin_보완텍스트trim후경계길이_암호화임시입력으로저장한다() {
        when(resolver.resolve(any())).thenReturn(Optional.of(verifiedVideo()));
        when(cipher.encrypt(any())).thenReturn(new TemporaryInputCipher.EncryptedInput(new byte[]{1, 2}, "key-1"));
        AiExtractionJobView created = queuedJob("https://www.youtube.com/watch?v=video-id");
        when(store.insert(any())).thenReturn(Optional.of(created));

        AiExtractionJobView result = service.submitAdmin(
                " https://www.youtube.com/watch?v=video-id ", " " + "a".repeat(20_000) + " ", null);

        assertThat(result.reused()).isFalse();
        verify(cipher).encrypt("a".repeat(20_000));
        ArgumentCaptor<AiExtractionJobStore.AiExtractionJobDraft> draft =
                ArgumentCaptor.forClass(AiExtractionJobStore.AiExtractionJobDraft.class);
        verify(store).insert(draft.capture());
        assertThat(draft.getValue().inputHash()).isEqualTo(hash("https://www.youtube.com/watch?v=video-id", "a".repeat(20_000)));
        verify(store).storeTemporaryInput(created.jobId(), new byte[]{1, 2}, "key-1", created.createdAt().plusHours(24));
    }

    @Test
    @DisplayName("보완 텍스트가 trim 후 2만 자를 초과하면 작업을 만들지 않고 거부한다")
    void submitAdmin_보완텍스트trim후초과길이_작업을만들지않고거부한다() {
        assertThatThrownBy(() -> service.submitAdmin(
                "https://www.youtube.com/watch?v=video-id", " " + "a".repeat(20_001) + " ", null))
                .isInstanceOf(com.masiton.common.web.BusinessException.class)
                .satisfies(exception -> assertThat(((com.masiton.common.web.BusinessException) exception).fieldErrors())
                        .anySatisfy(error -> assertThat(error.reason()).isEqualTo("supplementText is too long.")));

        verifyNoInteractions(resolver, store, cipher);
    }

    @Test
    @DisplayName("HTTP·비 YouTube·포트가 있는 URL은 공개 영상 URL로 인정하지 않는다")
    void submitAdmin_공개HTTPSYouTubeURL아님_거부한다() {
        assertThatThrownBy(() -> service.submitAdmin("http://www.youtube.com/watch?v=video-id", null, null))
                .isInstanceOf(com.masiton.common.web.BusinessException.class);
        assertThatThrownBy(() -> service.submitAdmin("https://evil.example/watch?v=video-id", null, null))
                .isInstanceOf(com.masiton.common.web.BusinessException.class);
        assertThatThrownBy(() -> service.submitAdmin("https://www.youtube.com:443/watch?v=video-id", null, null))
                .isInstanceOf(com.masiton.common.web.BusinessException.class);
        assertThatThrownBy(() -> service.submitAdmin("https://youtu.be/video-id/extra", null, null))
                .isInstanceOf(com.masiton.common.web.BusinessException.class);
        assertThatThrownBy(() -> service.submitAdmin("https://www.youtube.com/shorts/video-id/extra", null, null))
                .isInstanceOf(com.masiton.common.web.BusinessException.class);
        verifyNoInteractions(resolver, store, cipher);
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

    private AiExtractionJobView queuedJob(String videoUrl) {
        return new AiExtractionJobView(UUID.randomUUID(), "ADMIN", "channel-id", "video-id", videoUrl,
                "QUEUED", null, null, AiExtractionContract.PROVIDER, AiExtractionContract.MODEL_VERSION,
                AiExtractionContract.PROMPT_VERSION, AiExtractionContract.SCHEMA_VERSION, 0,
                OffsetDateTime.parse("2026-08-10T00:00:00Z"), null, null, false);
    }

    private VerifiedVideo verifiedVideo() {
        return new VerifiedVideo("video-id", "channel-id", "title", "https://img.example/thumbnail",
                "channel", "https://www.youtube.com/watch?v=video-id",
                OffsetDateTime.parse("2026-08-10T00:00:00Z"), OffsetDateTime.parse("2026-08-10T00:00:00Z"));
    }

    private byte[] hash(String videoUrl, String supplement) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest((videoUrl + "\n" + supplement).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
