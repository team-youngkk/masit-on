package com.masiton.creator.application.query;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.masiton.common.web.BusinessException;
import com.masiton.creator.application.port.in.GetPublicCreatorDetailUseCase.CreatorDetailResult;
import com.masiton.creator.application.port.out.CreatorRepositoryPort;
import com.masiton.creator.domain.model.Creator;
import com.masiton.creator.domain.model.ExternalAvailabilityStatus;
import com.masiton.creator.domain.model.LifecycleStatus;
import com.masiton.creator.domain.model.PublicationStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * API-CREATOR-DETAIL-001 공개 상세 조회 판정을 검증한다. creator-detail-api.md 3절:
 * 없음·비공개·삭제·외부 이용 불가는 모두 같은 404 CREATOR_NOT_FOUND다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("유튜버 기본 상세 조회 서비스")
class CreatorDetailQueryServiceTest {

    @Mock
    private CreatorRepositoryPort creatorRepository;

    private final UUID creatorId = UUID.randomUUID();

    @Test
    @DisplayName("공개·활성·이용 가능한 유튜버는 저장된 표시 정보를 그대로 반환한다")
    void 조회_공개활성이용가능_저장된표시정보를반환한다() {
        Creator creator = creatorOf(
                PublicationStatus.PUBLIC, LifecycleStatus.ACTIVE, ExternalAvailabilityStatus.AVAILABLE,
                "https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg", "채널 소개", "@masiton-fixture");
        given(creatorRepository.findById(creatorId)).willReturn(Optional.of(creator));
        CreatorDetailQueryService service = new CreatorDetailQueryService(creatorRepository);

        CreatorDetailResult result = service.getPublicCreatorDetail(creatorId);

        assertThat(result.id()).isEqualTo(creatorId);
        assertThat(result.channelName()).isEqualTo("채널명");
        assertThat(result.profileImageUrl()).isEqualTo("https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg");
        assertThat(result.description()).isEqualTo("채널 소개");
        assertThat(result.handle()).isEqualTo("@masiton-fixture");
        assertThat(result.channelUrl()).isEqualTo("https://www.youtube.com/channel/UC-fixture");
    }

    @Test
    @DisplayName("선택 표시 정보가 미등록이면 null을 그대로 반환한다")
    void 조회_선택표시정보미등록_null을반환한다() {
        Creator creator = creatorOf(
                PublicationStatus.PUBLIC, LifecycleStatus.ACTIVE, ExternalAvailabilityStatus.AVAILABLE,
                null, null, null);
        given(creatorRepository.findById(creatorId)).willReturn(Optional.of(creator));
        CreatorDetailQueryService service = new CreatorDetailQueryService(creatorRepository);

        CreatorDetailResult result = service.getPublicCreatorDetail(creatorId);

        assertThat(result.profileImageUrl()).isNull();
        assertThat(result.description()).isNull();
        assertThat(result.handle()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 식별자는 404 CREATOR_NOT_FOUND를 던진다")
    void 조회_존재하지않음_404CREATOR_NOT_FOUND를던진다() {
        given(creatorRepository.findById(creatorId)).willReturn(Optional.empty());
        CreatorDetailQueryService service = new CreatorDetailQueryService(creatorRepository);

        assertThatThrownBy(() -> service.getPublicCreatorDetail(creatorId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("CREATOR_NOT_FOUND");
    }

    @Test
    @DisplayName("비공개(PRIVATE) 유튜버는 404 CREATOR_NOT_FOUND를 던진다")
    void 조회_비공개유튜버_404CREATOR_NOT_FOUND를던진다() {
        Creator creator = creatorOf(
                PublicationStatus.PRIVATE, LifecycleStatus.ACTIVE, ExternalAvailabilityStatus.AVAILABLE,
                null, null, null);
        given(creatorRepository.findById(creatorId)).willReturn(Optional.of(creator));
        CreatorDetailQueryService service = new CreatorDetailQueryService(creatorRepository);

        assertThatThrownBy(() -> service.getPublicCreatorDetail(creatorId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("CREATOR_NOT_FOUND");
    }

    @Test
    @DisplayName("삭제(DELETED) 유튜버는 404 CREATOR_NOT_FOUND를 던진다")
    void 조회_삭제유튜버_404CREATOR_NOT_FOUND를던진다() {
        Creator creator = creatorOf(
                PublicationStatus.PRIVATE, LifecycleStatus.DELETED, ExternalAvailabilityStatus.AVAILABLE,
                null, null, null);
        given(creatorRepository.findById(creatorId)).willReturn(Optional.of(creator));
        CreatorDetailQueryService service = new CreatorDetailQueryService(creatorRepository);

        assertThatThrownBy(() -> service.getPublicCreatorDetail(creatorId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("CREATOR_NOT_FOUND");
    }

    @Test
    @DisplayName("외부 이용 불가(UNAVAILABLE) 유튜버는 404 CREATOR_NOT_FOUND를 던진다")
    void 조회_외부이용불가유튜버_404CREATOR_NOT_FOUND를던진다() {
        Creator creator = creatorOf(
                PublicationStatus.PUBLIC, LifecycleStatus.ACTIVE, ExternalAvailabilityStatus.UNAVAILABLE,
                null, null, null);
        given(creatorRepository.findById(creatorId)).willReturn(Optional.of(creator));
        CreatorDetailQueryService service = new CreatorDetailQueryService(creatorRepository);

        assertThatThrownBy(() -> service.getPublicCreatorDetail(creatorId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("CREATOR_NOT_FOUND");
    }

    private Creator creatorOf(
            PublicationStatus publicationStatus,
            LifecycleStatus lifecycleStatus,
            ExternalAvailabilityStatus externalAvailabilityStatus,
            String profileImageUrl,
            String description,
            String handle) {
        return new Creator(
                creatorId,
                "UC-fixture",
                "채널명",
                "https://www.youtube.com/channel/UC-fixture",
                profileImageUrl,
                description,
                handle,
                publicationStatus,
                lifecycleStatus,
                externalAvailabilityStatus,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                lifecycleStatus == LifecycleStatus.DELETED ? OffsetDateTime.now() : null);
    }
}
