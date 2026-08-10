package com.masiton.orchestration.application.command;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.creator.application.port.in.VerifiedCreatorRegistrationUseCase;
import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.restaurant.application.port.in.VerifiedRestaurantRegistrationUseCase;
import com.masiton.video.application.port.in.VerifiedVideoRegistrationUseCase;
import com.masiton.visit.application.port.in.RegisterVisitUseCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("자동 검증 콘텐츠 등록 서비스")
class AutoRegisterVerifiedContentServiceTest {

    private final VerifiedRestaurantRegistrationUseCase restaurantRegistration =
            mock(VerifiedRestaurantRegistrationUseCase.class);
    private final VerifiedCreatorRegistrationUseCase creatorRegistration =
            mock(VerifiedCreatorRegistrationUseCase.class);
    private final VerifiedVideoRegistrationUseCase videoRegistration =
            mock(VerifiedVideoRegistrationUseCase.class);
    private final RegisterVisitUseCase visitRegistration = mock(RegisterVisitUseCase.class);
    private final AutoRegisterVerifiedContentService service = new AutoRegisterVerifiedContentService(
            restaurantRegistration, creatorRegistration, videoRegistration, visitRegistration);

    @Test
    @DisplayName("자동 검증 결과를 관리자 승인 없이 네 정식 관계로 확정한다")
    void register_자동검증완료_정식관계를등록한다() {
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        when(creatorRegistration.register(any())).thenReturn(
                new VerifiedCreatorRegistrationUseCase.RegistrationResult(creatorId, true));
        when(restaurantRegistration.register(any())).thenReturn(
                new VerifiedRestaurantRegistrationUseCase.RegistrationResult(restaurantId, true));
        when(videoRegistration.register(any())).thenReturn(
                new VerifiedVideoRegistrationUseCase.RegistrationResult(videoId, true));
        when(visitRegistration.register(any())).thenReturn(
                new RegisterVisitUseCase.VisitRegistrationResult(visitId, true));

        AutoRegisterVerifiedContentUseCase.RegistrationResult result = service.register(command(true));

        assertThat(result).isEqualTo(new AutoRegisterVerifiedContentUseCase.RegistrationResult(
                restaurantId, creatorId, videoId, visitId, true, true, true, true));
        verify(visitRegistration).register(new RegisterVisitUseCase.RegisterVisitCommand(
                restaurantId, creatorId, videoId, true));
    }

    @Test
    @DisplayName("게시 채널이 다르면 정식 등록 Port를 호출하지 않는다")
    void register_게시채널불일치_등록하지않는다() {
        AutoRegisterVerifiedContentUseCase.VerifiedContentCommand command = new AutoRegisterVerifiedContentUseCase.VerifiedContentCommand(
                command(true).restaurant(),
                command(true).creator(),
                new AutoRegisterVerifiedContentUseCase.VideoCandidate(
                        "video-id", "other-channel", "영상", "https://youtube.com/watch?v=video-id",
                        "https://i.ytimg.com/thumbnail.jpg", OffsetDateTime.now(), OffsetDateTime.now()),
                true);

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Video publisher channel does not match creator channel.");
        verify(creatorRegistration, never()).register(any());
        verify(restaurantRegistration, never()).register(any());
        verify(videoRegistration, never()).register(any());
    }

    @Test
    @DisplayName("방문 근거가 없으면 자동 등록을 시작하지 않는다")
    void register_방문근거없음_등록하지않는다() {
        assertThatThrownBy(() -> service.register(command(false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("visitEvidenceConfirmed must be true for automatic registration.");
        verify(creatorRegistration, never()).register(any());
    }

    private AutoRegisterVerifiedContentUseCase.VerifiedContentCommand command(boolean evidenceConfirmed) {
        return new AutoRegisterVerifiedContentUseCase.VerifiedContentCommand(
                new AutoRegisterVerifiedContentUseCase.RestaurantCandidate(
                        UUID.randomUUID(), UUID.randomUUID(), "맛집", "place-id",
                        "https://place.map.kakao.com/1", "서울특별시 마포구 도로 1", null,
                        "02-1234-5678", new BigDecimal("37.550000"), new BigDecimal("126.910000")),
                new AutoRegisterVerifiedContentUseCase.CreatorCandidate(
                        "channel-id", "채널", "https://youtube.com/channel/channel-id"),
                new AutoRegisterVerifiedContentUseCase.VideoCandidate(
                        "video-id", "channel-id", "영상", "https://youtube.com/watch?v=video-id",
                        "https://i.ytimg.com/thumbnail.jpg", OffsetDateTime.now(), OffsetDateTime.now()),
                evidenceConfirmed);
    }
}
