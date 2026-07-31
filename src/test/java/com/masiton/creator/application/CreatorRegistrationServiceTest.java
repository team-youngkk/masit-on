package com.masiton.creator.application;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.masiton.creator.application.port.in.CreatorRegistrationUseCase;
import com.masiton.creator.application.port.out.ChannelVerificationPort;
import com.masiton.creator.application.port.out.CreatorRepositoryPort;
import com.masiton.creator.application.port.out.VerifiedChannel;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.creator.domain.model.Creator;
import com.masiton.creator.domain.model.ExternalAvailabilityStatus;
import com.masiton.creator.domain.model.LifecycleStatus;
import com.masiton.creator.domain.model.PublicationStatus;
import com.masiton.security.application.AcquiredConfirmationToken;
import com.masiton.security.application.IssuedConfirmationToken;
import com.masiton.security.application.port.in.ConfirmationTokenUseCase;
import com.masiton.security.domain.model.ConfirmationTokenResourceType;
import com.masiton.security.domain.model.ConfirmationTokenStatus;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("유튜버 등록 애플리케이션 서비스")
class CreatorRegistrationServiceTest {

    private final ChannelVerificationPort channelVerificationPort = mock(ChannelVerificationPort.class);
    private final CreatorRepositoryPort creatorRepository = mock(CreatorRepositoryPort.class);
    private final ConfirmationTokenUseCase confirmationTokenUseCase = mock(ConfirmationTokenUseCase.class);
    private final CreatorRegistrationService service = new CreatorRegistrationService(
            channelVerificationPort,
            creatorRepository,
            confirmationTokenUseCase,
            new ObjectMapper());

    private final UUID adminId = UUID.randomUUID();
    private final OffsetDateTime checkedAt = OffsetDateTime.parse("2026-07-27T12:00:00Z");

    @Test
    @DisplayName("정상 미리보기는 확인 토큰을 발급하고 유튜버를 저장하지 않는다")
    void preview_정상_READY_토큰을발급하고저장하지않는다() {
        when(channelVerificationPort.verify(any())).thenReturn(Optional.of(verifiedChannel()));
        when(creatorRepository.findByExternalChannelId("channel-1")).thenReturn(Optional.empty());
        when(confirmationTokenUseCase.issue(any())).thenReturn(
                new IssuedConfirmationToken("opaque-token", OffsetDateTime.parse("2026-07-27T12:10:00Z")));

        CreatorRegistrationUseCase.CreatorPreviewResult result = service.preview(
                new CreatorRegistrationUseCase.CreatorPreviewCommand(
                        adminId,
                        "https://www.youtube.com/channel/channel-1"));

        assertThat(result.decision()).isEqualTo(CreatorRegistrationUseCase.CreatorPreviewResult.Decision.READY);
        assertThat(result.confirmationToken()).isEqualTo("opaque-token");
        assertThat(result.candidate()).isEqualTo(new CreatorRegistrationUseCase.CreatorCandidate(
                null,
                "채널명",
                "https://www.youtube.com/channel/channel-1"));
        verify(creatorRepository, never()).insertIfAbsent(any());
    }

    @Test
    @DisplayName("동일 채널은 미리보기에서 기존 유튜버를 반환하고 토큰을 만들지 않는다")
    void preview_중복_기존유튜버를반환하고토큰을만들지않는다() {
        Creator existing = creator(UUID.randomUUID(), "channel-1");
        when(channelVerificationPort.verify(any())).thenReturn(Optional.of(verifiedChannel()));
        when(creatorRepository.findByExternalChannelId("channel-1")).thenReturn(Optional.of(existing));

        CreatorRegistrationUseCase.CreatorPreviewResult result = service.preview(
                new CreatorRegistrationUseCase.CreatorPreviewCommand(
                        adminId,
                        "https://www.youtube.com/channel/channel-1"));

        assertThat(result.decision()).isEqualTo(CreatorRegistrationUseCase.CreatorPreviewResult.Decision.DUPLICATE);
        assertThat(result.confirmationToken()).isNull();
        assertThat(result.existingResource()).isEqualTo(new CreatorRegistrationUseCase.ExistingCreator(
                existing.getId(),
                existing.getChannelName(),
                existing.getChannelUrl()));
        verify(confirmationTokenUseCase, never()).issue(any());
    }

    @Test
    @DisplayName("채널 식별자가 없는 YouTube URL은 INVALID_FIELD_VALUE로 거부한다")
    void preview_채널식별자없는URL_INVALID_FIELD_VALUE() {
        assertThatThrownBy(() -> service.preview(
                new CreatorRegistrationUseCase.CreatorPreviewCommand(
                        adminId,
                        "https://www.youtube.com/watch?v=video-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(ErrorCode.INVALID_FIELD_VALUE.name());
    }

    @Test
    @DisplayName("유효한 공개 채널을 확인할 수 없으면 INVALID_FIELD_VALUE로 거부한다")
    void preview_공개채널확인불가_INVALID_FIELD_VALUE() {
        when(channelVerificationPort.verify(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(
                new CreatorRegistrationUseCase.CreatorPreviewCommand(
                        adminId,
                        "https://www.youtube.com/channel/missing-channel")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(ErrorCode.INVALID_FIELD_VALUE.name());
        verify(confirmationTokenUseCase, never()).issue(any());
    }

    @Test
    @DisplayName("발급된 토큰으로 확정하면 공개 유튜버를 만들고 CREATED로 완료한다")
    void create_발급토큰_생성과완료를처리한다() {
        UUID tokenId = UUID.randomUUID();
        when(confirmationTokenUseCase.acquire(
                eq("opaque-token"),
                eq(adminId),
                eq(ConfirmationTokenResourceType.CREATOR)))
                .thenReturn(acquired(tokenId, ConfirmationTokenStatus.ISSUED, null));
        when(creatorRepository.findByExternalChannelId("channel-1")).thenReturn(Optional.empty());
        when(creatorRepository.insertIfAbsent(any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        CreatorRegistrationUseCase.CreatorCreationResult result = service.create(
                new CreatorRegistrationUseCase.CreatorCreateCommand(adminId, "opaque-token"));

        assertThat(result.created()).isTrue();
        assertThat(result.duplicate()).isFalse();
        assertThat(result.creator().channelName()).isEqualTo("채널명");
        verify(confirmationTokenUseCase).completeCreated(eq(tokenId), eq(result.creator().id()));
    }

    @Test
    @DisplayName("확정 시 미리보기에서 저장된 프로필 이미지·소개·handle을 Creator에 그대로 전달한다")
    void create_발급토큰_표시정보를Creator에전달한다() {
        UUID tokenId = UUID.randomUUID();
        when(confirmationTokenUseCase.acquire(
                eq("opaque-token"),
                eq(adminId),
                eq(ConfirmationTokenResourceType.CREATOR)))
                .thenReturn(acquired(tokenId, ConfirmationTokenStatus.ISSUED, null));
        when(creatorRepository.findByExternalChannelId("channel-1")).thenReturn(Optional.empty());
        when(creatorRepository.insertIfAbsent(any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        ArgumentCaptor<Creator> captor = ArgumentCaptor.forClass(Creator.class);

        service.create(new CreatorRegistrationUseCase.CreatorCreateCommand(adminId, "opaque-token"));

        verify(creatorRepository).insertIfAbsent(captor.capture());
        Creator saved = captor.getValue();
        assertThat(saved.getProfileImageUrl()).isEqualTo("https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg");
        assertThat(saved.getDescription()).isEqualTo("채널 소개");
        assertThat(saved.getHandle()).isEqualTo("@channel-handle");
    }

    @Test
    @DisplayName("미리보기 snapshot의 표시 정보가 공백이면 저장 전 null로 정규화한다")
    void create_snapshot표시정보공백_null로정규화한다() {
        UUID tokenId = UUID.randomUUID();
        when(confirmationTokenUseCase.acquire(
                eq("opaque-token"),
                eq(adminId),
                eq(ConfirmationTokenResourceType.CREATOR)))
                .thenReturn(new AcquiredConfirmationToken(
                        tokenId,
                        (short) 1,
                        "channel-1",
                        """
                        {"externalChannelId":"channel-1","channelName":"채널명",
                        "channelUrl":"https://www.youtube.com/channel/channel-1",
                        "profileImageUrl":"   ","description":"","handle":"   ",
                        "checkedAt":"2026-07-27T12:00:00Z"}
                        """,
                        ConfirmationTokenStatus.ISSUED,
                        null));
        when(creatorRepository.findByExternalChannelId("channel-1")).thenReturn(Optional.empty());
        when(creatorRepository.insertIfAbsent(any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        ArgumentCaptor<Creator> captor = ArgumentCaptor.forClass(Creator.class);

        service.create(new CreatorRegistrationUseCase.CreatorCreateCommand(adminId, "opaque-token"));

        verify(creatorRepository).insertIfAbsent(captor.capture());
        Creator saved = captor.getValue();
        assertThat(saved.getProfileImageUrl()).isNull();
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getHandle()).isNull();
    }

    @Test
    @DisplayName("완료된 CREATED 토큰 재시도는 새 유튜버를 만들지 않고 같은 결과를 반환한다")
    void create_CREATED재시도_같은결과를반환한다() {
        UUID tokenId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        when(confirmationTokenUseCase.acquire(
                eq("opaque-token"),
                eq(adminId),
                eq(ConfirmationTokenResourceType.CREATOR)))
                .thenReturn(acquired(tokenId, ConfirmationTokenStatus.CREATED, creatorId));
        when(creatorRepository.findById(creatorId)).thenReturn(Optional.of(creator(creatorId, "channel-1")));

        CreatorRegistrationUseCase.CreatorCreationResult result = service.create(
                new CreatorRegistrationUseCase.CreatorCreateCommand(adminId, "opaque-token"));

        assertThat(result.created()).isFalse();
        assertThat(result.duplicate()).isFalse();
        assertThat(result.creator().id()).isEqualTo(creatorId);
        verify(creatorRepository, never()).insertIfAbsent(any());
        verify(confirmationTokenUseCase, never()).completeCreated(any(), any());
    }

    @Test
    @DisplayName("확정 직전 다른 요청이 동일 채널을 만들면 토큰을 DUPLICATE로 완료한다")
    void create_동시중복_DUPLICATE로완료한다() {
        UUID tokenId = UUID.randomUUID();
        Creator concurrent = creator(UUID.randomUUID(), "channel-1");
        when(confirmationTokenUseCase.acquire(
                eq("opaque-token"),
                eq(adminId),
                eq(ConfirmationTokenResourceType.CREATOR)))
                .thenReturn(acquired(tokenId, ConfirmationTokenStatus.ISSUED, null));
        when(creatorRepository.findByExternalChannelId("channel-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrent));
        when(creatorRepository.insertIfAbsent(any())).thenReturn(Optional.empty());

        CreatorRegistrationUseCase.CreatorCreationResult result = service.create(
                new CreatorRegistrationUseCase.CreatorCreateCommand(adminId, "opaque-token"));

        assertThat(result.duplicate()).isTrue();
        assertThat(result.creator().id()).isEqualTo(concurrent.getId());
        verify(confirmationTokenUseCase).completeDuplicate(tokenId, concurrent.getId());
    }

    @Test
    @DisplayName("채널이 아닌 YouTube URL은 외부 호출 전에 입력 오류로 거부한다")
    void preview_채널이아닌YouTubeUrl_입력오류로거부한다() {
        assertThatThrownBy(() -> service.preview(
                new CreatorRegistrationUseCase.CreatorPreviewCommand(
                        adminId,
                        "https://www.youtube.com/watch?v=video-1")))
                .isInstanceOf(com.masiton.common.web.BusinessException.class)
                .hasMessageContaining("요청 값을 확인해 주세요.");

        verify(channelVerificationPort, never()).verify(any());
    }

    private VerifiedChannel verifiedChannel() {
        return new VerifiedChannel(
                "channel-1",
                "채널명",
                "https://www.youtube.com/channel/channel-1",
                "https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg",
                "채널 소개",
                "@channel-handle",
                checkedAt);
    }

    private AcquiredConfirmationToken acquired(UUID tokenId, ConfirmationTokenStatus status, UUID resultResourceId) {
        return new AcquiredConfirmationToken(
                tokenId,
                (short) 1,
                "channel-1",
                """
                {"externalChannelId":"channel-1","channelName":"채널명",
                "channelUrl":"https://www.youtube.com/channel/channel-1",
                "profileImageUrl":"https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg",
                "description":"채널 소개","handle":"@channel-handle",
                "checkedAt":"2026-07-27T12:00:00Z"}
                """,
                status,
                resultResourceId);
    }

    private Creator creator(UUID id, String externalChannelId) {
        return new Creator(
                id,
                externalChannelId,
                "기존 채널",
                "https://www.youtube.com/channel/" + externalChannelId,
                null,
                null,
                null,
                PublicationStatus.PUBLIC,
                LifecycleStatus.ACTIVE,
                ExternalAvailabilityStatus.AVAILABLE,
                checkedAt,
                null,
                null,
                null);
    }
}
