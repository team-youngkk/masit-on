package com.masiton.curation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.masiton.common.web.BusinessException;
import com.masiton.curation.application.port.out.CurationStore;
import com.masiton.curation.application.port.out.CurationStore.StoredCuration;
import com.masiton.curation.application.port.out.CurationStore.StoredCurationRestaurant;
import com.masiton.curation.domain.model.CurationStatus;
import com.masiton.restaurant.application.port.in.FindRestaurantReferenceUseCase;
import com.masiton.restaurant.application.port.in.FindRestaurantReferenceUseCase.RestaurantReference;

@ExtendWith(MockitoExtension.class)
@DisplayName("공개 큐레이션 서비스")
class PublicCurationServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-05T00:00:00Z");

    @Mock CurationStore store;
    @Mock FindRestaurantReferenceUseCase restaurantReferences;
    @InjectMocks PublicCurationService service;

    @Test
    @DisplayName("게시 목록은 메인 순서를 유지하고 공개 맛집만 구성 순서대로 반환한다")
    void 게시목록_숨김맛집제외_메인과구성순서유지() {
        UUID firstCurationId = UUID.randomUUID();
        UUID secondCurationId = UUID.randomUUID();
        UUID firstRestaurantId = UUID.randomUUID();
        UUID hiddenRestaurantId = UUID.randomUUID();
        UUID lastRestaurantId = UUID.randomUUID();
        List<StoredCuration> curations = List.of(
                curation(firstCurationId, 1), curation(secondCurationId, 2));
        List<StoredCurationRestaurant> relations = List.of(
                new StoredCurationRestaurant(firstCurationId, firstRestaurantId, 1),
                new StoredCurationRestaurant(firstCurationId, hiddenRestaurantId, 2),
                new StoredCurationRestaurant(firstCurationId, lastRestaurantId, 3));
        when(store.findPublished(5)).thenReturn(curations);
        when(store.findRestaurants(List.of(firstCurationId, secondCurationId))).thenReturn(relations);
        when(restaurantReferences.findRestaurantReferences(
                List.of(firstRestaurantId, hiddenRestaurantId, lastRestaurantId))).thenReturn(List.of(
                        reference(firstRestaurantId, "첫 맛집", "서울 첫길 1", true),
                        reference(hiddenRestaurantId, "숨김 맛집", "서울 숨김길 2", false),
                        reference(lastRestaurantId, "마지막 맛집", "서울 마지막길 3", true)));

        var result = service.getPublishedCurations();

        assertThat(result).extracting(item -> item.curationId())
                .containsExactly(firstCurationId, secondCurationId);
        assertThat(result.getFirst().items()).extracting(item -> item.restaurantId())
                .containsExactly(firstRestaurantId, lastRestaurantId);
        assertThat(result.get(1).items()).isEmpty();
        verify(restaurantReferences).findRestaurantReferences(
                List.of(firstRestaurantId, hiddenRestaurantId, lastRestaurantId));
    }

    @Test
    @DisplayName("게시 상세의 모든 맛집이 숨겨져도 빈 구성으로 큐레이션을 반환한다")
    void 게시상세_모든맛집숨김_빈구성200모델() {
        UUID curationId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        when(store.findPublished(curationId)).thenReturn(Optional.of(curation(curationId, 1)));
        when(store.findRestaurants(List.of(curationId))).thenReturn(List.of(
                new StoredCurationRestaurant(curationId, restaurantId, 1)));
        when(restaurantReferences.findRestaurantReferences(List.of(restaurantId)))
                .thenReturn(List.of(reference(restaurantId, "숨김", "서울 숨김길 1", false)));

        var result = service.getPublishedCuration(curationId);

        assertThat(result.curationId()).isEqualTo(curationId);
        assertThat(result.items()).isEmpty();
    }

    @Test
    @DisplayName("초안이거나 없는 상세는 같은 CURATION_NOT_FOUND를 반환한다")
    void 비게시또는없음_상세404() {
        UUID curationId = UUID.randomUUID();
        when(store.findPublished(curationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublishedCuration(curationId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CURATION_NOT_FOUND"));
    }

    private StoredCuration curation(UUID id, int mainPosition) {
        return new StoredCuration(id, "제목 " + mainPosition, "설명", CurationStatus.PUBLISHED,
                mainPosition, UUID.randomUUID(), UUID.randomUUID(), NOW, NOW, NOW);
    }

    private RestaurantReference reference(
            UUID id, String name, String roadAddress, boolean publiclyVisible
    ) {
        return new RestaurantReference(id, name, roadAddress,
                publiclyVisible ? "PUBLIC" : "PRIVATE", publiclyVisible);
    }
}
