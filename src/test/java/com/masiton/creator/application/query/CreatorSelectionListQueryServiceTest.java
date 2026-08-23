package com.masiton.creator.application.query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.masiton.creator.application.port.in.CreatorSelectionItem;
import com.masiton.creator.application.port.out.CreatorRepositoryPort;
import com.masiton.creator.domain.model.Creator;
import com.masiton.creator.domain.model.ExternalAvailabilityStatus;
import com.masiton.creator.domain.model.LifecycleStatus;
import com.masiton.creator.domain.model.PublicationStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("공개 유튜버 선택 목록 조회 서비스")
class CreatorSelectionListQueryServiceTest {

    @Mock
    private CreatorRepositoryPort creatorRepositoryPort;

    @Test
    @DisplayName("저장소가 빈 목록을 반환하면 빈 선택 목록을 반환한다")
    void 조회_저장소가빈목록반환_빈선택목록을반환한다() {
        // given
        given(creatorRepositoryPort.findPublicSelectionList()).willReturn(List.of());
        CreatorSelectionListQueryService service = new CreatorSelectionListQueryService(creatorRepositoryPort);

        // when
        List<CreatorSelectionItem> result = service.getPublicSelectionList();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("저장소가 반환한 순서를 재정렬하지 않고 그대로 위임한다")
    void 조회_저장소가역순으로반환_서비스가재정렬하지않고그대로위임한다() {
        // given: 저장소가 이미 오름차순으로 정렬해 반환한다는 계약(CreatorRepositoryPort 참고)이므로
        // 서비스가 스스로 재정렬하지 않는지 확인하려면 일부러 오름차순이 아닌 순서를 대역으로 준다.
        Creator first = creatorOf("마바사 채널", "https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg");
        Creator second = creatorOf("가나다 채널", null);
        given(creatorRepositoryPort.findPublicSelectionList()).willReturn(List.of(first, second));
        CreatorSelectionListQueryService service = new CreatorSelectionListQueryService(creatorRepositoryPort);

        // when
        List<CreatorSelectionItem> result = service.getPublicSelectionList();

        // then
        assertThat(result)
                .extracting(CreatorSelectionItem::channelName)
                .containsExactly("마바사 채널", "가나다 채널");
        assertThat(result.get(0).id()).isEqualTo(first.getId());
        assertThat(result.get(1).id()).isEqualTo(second.getId());
        assertThat(result.get(0).profileImageUrl()).isEqualTo("https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg");
        assertThat(result.get(1).profileImageUrl()).isNull();
    }

    private Creator creatorOf(String channelName, String profileImageUrl) {
        return new Creator(
                UUID.randomUUID(),
                "UC-" + UUID.randomUUID(),
                channelName,
                "https://example.com/channel",
                profileImageUrl,
                null,
                null,
                PublicationStatus.PUBLIC,
                LifecycleStatus.ACTIVE,
                ExternalAvailabilityStatus.AVAILABLE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null);
    }
}
