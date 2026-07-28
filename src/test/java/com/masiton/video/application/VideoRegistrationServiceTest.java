package com.masiton.video.application;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.security.application.AcquiredConfirmationToken;
import com.masiton.security.application.IssuedConfirmationToken;
import com.masiton.security.application.port.in.ConfirmationTokenUseCase;
import com.masiton.security.domain.model.ConfirmationTokenResourceType;
import com.masiton.security.domain.model.ConfirmationTokenStatus;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.video.application.port.in.VideoRegistrationUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;
import com.masiton.video.application.port.out.VideoRepositoryPort;
import com.masiton.video.application.port.out.VideoVerificationPort;
import com.masiton.video.domain.model.ExternalAvailabilityStatus;
import com.masiton.video.domain.model.LifecycleStatus;
import com.masiton.video.domain.model.PublicationStatus;
import com.masiton.video.domain.model.Video;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("영상 등록 애플리케이션 서비스")
class VideoRegistrationServiceTest {

    private final VideoVerificationPort videoVerificationPort = mock(VideoVerificationPort.class);
    private final VideoRepositoryPort videoRepository = mock(VideoRepositoryPort.class);
    private final ConfirmationTokenUseCase confirmationTokenUseCase = mock(ConfirmationTokenUseCase.class);
    private final VideoRegistrationService service = new VideoRegistrationService(
            videoVerificationPort,
            videoRepository,
            confirmationTokenUseCase,
            new ObjectMapper());

    private final UUID adminId = UUID.randomUUID();
    private final OffsetDateTime publishedAt = OffsetDateTime.parse("2026-07-27T12:00:00Z");
    private final OffsetDateTime checkedAt = OffsetDateTime.parse("2026-07-27T12:05:00Z");

    @Test
    @DisplayName("정상 미리보기는 확인 토큰을 발급하고 영상을 저장하지 않는다")
    void preview_정상_READY_토큰을발급하고저장하지않는다() {
        when(videoVerificationPort.verify(any())).thenReturn(Optional.of(verifiedVideo()));
        when(videoRepository.findByExternalVideoId("video-1")).thenReturn(Optional.empty());
        when(confirmationTokenUseCase.issue(any())).thenReturn(
                new IssuedConfirmationToken("opaque-token", OffsetDateTime.parse("2026-07-27T12:15:00Z")));

        VideoRegistrationUseCase.VideoPreviewResult result = service.preview(
                new VideoRegistrationUseCase.VideoPreviewCommand(
                        adminId,
                        "https://www.youtube.com/watch?v=video-1"));

        assertThat(result.decision()).isEqualTo(VideoRegistrationUseCase.VideoPreviewResult.Decision.READY);
        assertThat(result.confirmationToken()).isEqualTo("opaque-token");
        assertThat(result.candidate()).isEqualTo(new VideoRegistrationUseCase.VideoCandidate(
                null,
                "영상 제목",
                "https://i.ytimg.com/example.jpg",
                "채널명",
                "https://www.youtube.com/watch?v=video-1"));
        verify(videoRepository, never()).insertIfAbsent(any());
    }

    @Test
    @DisplayName("동일 영상은 미리보기에서 기존 영상을 반환하고 토큰을 만들지 않는다")
    void preview_중복_기존영상을반환하고토큰을만들지않는다() {
        Video existing = video(UUID.randomUUID(), "video-1");
        when(videoVerificationPort.verify(any())).thenReturn(Optional.of(verifiedVideo()));
        when(videoRepository.findByExternalVideoId("video-1")).thenReturn(Optional.of(existing));

        VideoRegistrationUseCase.VideoPreviewResult result = service.preview(
                new VideoRegistrationUseCase.VideoPreviewCommand(
                        adminId,
                        "https://www.youtube.com/watch?v=video-1"));

        assertThat(result.decision()).isEqualTo(VideoRegistrationUseCase.VideoPreviewResult.Decision.DUPLICATE);
        assertThat(result.confirmationToken()).isNull();
        assertThat(result.existingResource()).isEqualTo(new VideoRegistrationUseCase.ExistingVideo(
                existing.getId(),
                existing.getTitle(),
                "채널명",
                existing.getSourceUrl()));
        verify(confirmationTokenUseCase, never()).issue(any());
    }

    @Test
    @DisplayName("영상 식별자가 없는 YouTube URL은 INVALID_FIELD_VALUE로 거부한다")
    void preview_영상식별자없는URL_INVALID_FIELD_VALUE() {
        assertThatThrownBy(() -> service.preview(
                new VideoRegistrationUseCase.VideoPreviewCommand(
                        adminId,
                        "https://www.youtube.com/watch")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(ErrorCode.INVALID_FIELD_VALUE.name());
    }

    @Test
    @DisplayName("발급된 토큰으로 확정하면 공개 영상을 만들고 CREATED로 완료한다")
    void create_발급토큰_생성과완료를처리한다() {
        UUID tokenId = UUID.randomUUID();
        when(confirmationTokenUseCase.acquire(
                eq("opaque-token"),
                eq(adminId),
                eq(ConfirmationTokenResourceType.VIDEO)))
                .thenReturn(acquired(tokenId, ConfirmationTokenStatus.ISSUED, null));
        when(videoRepository.findByExternalVideoId("video-1")).thenReturn(Optional.empty());
        when(videoRepository.insertIfAbsent(any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        VideoRegistrationUseCase.VideoCreationResult result = service.create(
                new VideoRegistrationUseCase.VideoCreateCommand(adminId, "opaque-token"));

        assertThat(result.created()).isTrue();
        assertThat(result.duplicate()).isFalse();
        assertThat(result.video().channelName()).isEqualTo("채널명");
        verify(confirmationTokenUseCase).completeCreated(eq(tokenId), eq(result.video().id()));
    }

    @Test
    @DisplayName("완료된 CREATED 토큰 재시도는 새 영상을 만들지 않고 같은 결과를 반환한다")
    void create_CREATED재시도_같은결과를반환한다() {
        UUID tokenId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        when(confirmationTokenUseCase.acquire(
                eq("opaque-token"),
                eq(adminId),
                eq(ConfirmationTokenResourceType.VIDEO)))
                .thenReturn(acquired(tokenId, ConfirmationTokenStatus.CREATED, videoId));
        when(videoRepository.findById(videoId)).thenReturn(Optional.of(video(videoId, "video-1")));

        VideoRegistrationUseCase.VideoCreationResult result = service.create(
                new VideoRegistrationUseCase.VideoCreateCommand(adminId, "opaque-token"));

        assertThat(result.created()).isFalse();
        assertThat(result.duplicate()).isFalse();
        assertThat(result.video().id()).isEqualTo(videoId);
        verify(videoRepository, never()).insertIfAbsent(any());
        verify(confirmationTokenUseCase, never()).completeCreated(any(), any());
    }

    @Test
    @DisplayName("확정 직전 다른 요청이 동일 영상을 만들면 토큰을 DUPLICATE로 완료한다")
    void create_동시중복_DUPLICATE로완료한다() {
        UUID tokenId = UUID.randomUUID();
        Video concurrent = video(UUID.randomUUID(), "video-1");
        when(confirmationTokenUseCase.acquire(
                eq("opaque-token"),
                eq(adminId),
                eq(ConfirmationTokenResourceType.VIDEO)))
                .thenReturn(acquired(tokenId, ConfirmationTokenStatus.ISSUED, null));
        when(videoRepository.findByExternalVideoId("video-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrent));
        when(videoRepository.insertIfAbsent(any())).thenReturn(Optional.empty());

        VideoRegistrationUseCase.VideoCreationResult result = service.create(
                new VideoRegistrationUseCase.VideoCreateCommand(adminId, "opaque-token"));

        assertThat(result.duplicate()).isTrue();
        assertThat(result.video().id()).isEqualTo(concurrent.getId());
        verify(confirmationTokenUseCase).completeDuplicate(tokenId, concurrent.getId());
    }

    private VerifiedVideo verifiedVideo() {
        return new VerifiedVideo(
                "video-1",
                "channel-1",
                "영상 제목",
                "https://i.ytimg.com/example.jpg",
                "채널명",
                "https://www.youtube.com/watch?v=video-1",
                publishedAt,
                checkedAt);
    }

    private AcquiredConfirmationToken acquired(UUID tokenId, ConfirmationTokenStatus status, UUID resultResourceId) {
        return new AcquiredConfirmationToken(
                tokenId,
                (short) 1,
                "video-1",
                """
                {"externalVideoId":"video-1","publisherExternalChannelId":"channel-1",
                "title":"영상 제목","thumbnailUrl":"https://i.ytimg.com/example.jpg",
                "channelName":"채널명","sourceUrl":"https://www.youtube.com/watch?v=video-1",
                "publishedAt":"2026-07-27T12:00:00Z","checkedAt":"2026-07-27T12:05:00Z"}
                """,
                status,
                resultResourceId);
    }

    private Video video(UUID id, String externalVideoId) {
        return new Video(
                id,
                null,
                externalVideoId,
                "channel-1",
                "기존 영상",
                "https://www.youtube.com/watch?v=" + externalVideoId,
                "https://i.ytimg.com/existing.jpg",
                publishedAt,
                PublicationStatus.PUBLIC,
                LifecycleStatus.ACTIVE,
                ExternalAvailabilityStatus.AVAILABLE,
                checkedAt,
                null,
                null,
                null);
    }
}
