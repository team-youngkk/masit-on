package com.masiton.orchestration.application.command;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.common.web.BusinessException;
import com.masiton.creator.application.port.in.FindCreatorReferenceUseCase;
import com.masiton.orchestration.application.port.in.RegisterVisitRelationshipUseCase;
import com.masiton.restaurant.application.port.in.FindRestaurantReferenceUseCase;
import com.masiton.security.application.AdminPrincipal;
import com.masiton.security.application.AdminRole;
import com.masiton.video.application.port.in.FindVideoReferenceUseCase;
import com.masiton.video.application.port.in.ResolveVideoCreatorUseCase;
import com.masiton.visit.application.port.in.RegisterVisitUseCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("방문 관계 등록 서비스")
class RegisterVisitServiceTest {

    private final FindRestaurantReferenceUseCase restaurantReferences = mock(FindRestaurantReferenceUseCase.class);
    private final FindCreatorReferenceUseCase creatorReferences = mock(FindCreatorReferenceUseCase.class);
    private final FindVideoReferenceUseCase videoReferences = mock(FindVideoReferenceUseCase.class);
    private final ResolveVideoCreatorUseCase videoCreatorResolver = mock(ResolveVideoCreatorUseCase.class);
    private final RegisterVisitUseCase visitRegistration = mock(RegisterVisitUseCase.class);
    private final RegisterVisitService service = new RegisterVisitService(
            restaurantReferences, creatorReferences, videoReferences, videoCreatorResolver, visitRegistration);

    @Test
    @DisplayName("미연결 영상을 같은 채널의 Creator에 연결하고 공개 Visit을 등록한다")
    void register_미연결영상과일치채널_Visit을등록한다() {
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        givenPublicReferences(restaurantId, creatorId, videoId, null);
        when(videoCreatorResolver.resolveCreator(videoId, creatorId)).thenReturn(video(videoId, creatorId));
        when(visitRegistration.register(any())).thenReturn(new RegisterVisitUseCase.VisitRegistrationResult(visitId, true));

        RegisterVisitRelationshipUseCase.RegisteredVisitRelationship result = service.register(
                command(restaurantId, creatorId, videoId, true), adminPrincipal());

        assertThat(result).isEqualTo(new RegisterVisitRelationshipUseCase.RegisteredVisitRelationship(
                visitId, restaurantId, creatorId, videoId));
        verify(videoCreatorResolver).resolveCreator(videoId, creatorId);
        verify(visitRegistration).register(new RegisterVisitUseCase.RegisterVisitCommand(
                restaurantId, creatorId, videoId, true));
    }

    @Test
    @DisplayName("영상 게시 채널이 Creator와 다르면 저장하지 않고 422를 반환한다")
    void register_게시채널불일치_422을반환한다() {
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        when(restaurantReferences.findRestaurantReference(restaurantId)).thenReturn(Optional.of(restaurant(restaurantId)));
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId)));
        when(videoReferences.findVideoReference(videoId)).thenReturn(Optional.of(new FindVideoReferenceUseCase.VideoReference(
                videoId, creatorId, "different-channel", true, true)));

        assertThatThrownBy(() -> service.register(command(restaurantId, creatorId, videoId, true), adminPrincipal()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("VIDEO_CHANNEL_MISMATCH"));
    }

    @Test
    @DisplayName("이미 등록된 조합은 409로 반환한다")
    void register_동일조합존재_409을반환한다() {
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        givenPublicReferences(restaurantId, creatorId, videoId, creatorId);
        when(visitRegistration.register(any())).thenReturn(new RegisterVisitUseCase.VisitRegistrationResult(null, false));

        assertThatThrownBy(() -> service.register(command(restaurantId, creatorId, videoId, true), adminPrincipal()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("DUPLICATE_VISIT_RELATIONSHIP"));
    }

    @Test
    @DisplayName("방문 근거 확인이 없으면 참조와 저장을 조회하지 않고 422를 반환한다")
    void register_방문근거없음_422을반환한다() {
        assertThatThrownBy(() -> service.register(
                        command(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false), adminPrincipal()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("VISIT_EVIDENCE_INSUFFICIENT"));
    }

    @Test
    @DisplayName("ADMIN Principal이 없으면 참조를 조회하지 않고 403을 반환한다")
    void register_관리자Principal없음_참조미조회403반환() {
        assertThatThrownBy(() -> service.register(
                        command(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true),
                        new AdminPrincipal("admin-id", java.util.Set.of())))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("FORBIDDEN"));
    }

    private void givenPublicReferences(UUID restaurantId, UUID creatorId, UUID videoId, UUID videoCreatorId) {
        when(restaurantReferences.findRestaurantReference(restaurantId)).thenReturn(Optional.of(restaurant(restaurantId)));
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId)));
        when(videoReferences.findVideoReference(videoId)).thenReturn(Optional.of(video(videoId, videoCreatorId)));
    }

    private FindRestaurantReferenceUseCase.RestaurantReference restaurant(UUID id) {
        return new FindRestaurantReferenceUseCase.RestaurantReference(id, true);
    }

    private FindCreatorReferenceUseCase.CreatorReference creator(UUID id) {
        return new FindCreatorReferenceUseCase.CreatorReference(id, "channel-id", true, true);
    }

    private FindVideoReferenceUseCase.VideoReference video(UUID videoId, UUID creatorId) {
        return new FindVideoReferenceUseCase.VideoReference(videoId, creatorId, "channel-id", true, true);
    }

    private RegisterVisitRelationshipUseCase.RegisterVisitRelationshipCommand command(
            UUID restaurantId, UUID creatorId, UUID videoId, boolean evidenceConfirmed) {
        return new RegisterVisitRelationshipUseCase.RegisterVisitRelationshipCommand(
                restaurantId, creatorId, videoId, evidenceConfirmed);
    }

    private AdminPrincipal adminPrincipal() {
        return new AdminPrincipal("admin-id", java.util.Set.of(AdminRole.ADMIN));
    }
}
