package com.masiton.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.masiton.restaurant.application.port.out.RestaurantRepositoryPort;
import com.masiton.restaurant.domain.model.LifecycleStatus;
import com.masiton.restaurant.domain.model.PublicationStatus;
import com.masiton.restaurant.domain.model.Restaurant;

@ExtendWith(MockitoExtension.class)
class RestaurantReferenceQueryServiceTest {

    @Mock
    RestaurantRepositoryPort restaurantRepository;

    @InjectMocks
    RestaurantReferenceQueryService service;

    @Test
    @DisplayName("여러 맛집 참조는 저장소 일괄 조회 한 번으로 이름과 공개 상태를 반환한다")
    void 여러맛집참조_일괄조회_상태반환() {
        UUID publicId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();
        Restaurant publicRestaurant = restaurant(publicId, "공개 맛집",
                PublicationStatus.PUBLIC, LifecycleStatus.ACTIVE);
        Restaurant inactiveRestaurant = restaurant(inactiveId, "비활성 맛집",
                PublicationStatus.PUBLIC, LifecycleStatus.DELETED);
        List<UUID> ids = List.of(publicId, inactiveId);
        when(restaurantRepository.findAllByIds(ids))
                .thenReturn(List.of(publicRestaurant, inactiveRestaurant));

        var references = service.findRestaurantReferences(ids);

        verify(restaurantRepository).findAllByIds(ids);
        assertThat(references).extracting(reference -> reference.id())
                .containsExactly(publicId, inactiveId);
        assertThat(references.get(0).name()).isEqualTo("공개 맛집");
        assertThat(references.get(0).publiclyVisible()).isTrue();
        assertThat(references.get(1).availability()).isEqualTo("INACTIVE");
        assertThat(references.get(1).publiclyVisible()).isFalse();
    }

    private Restaurant restaurant(
            UUID id, String name, PublicationStatus publicationStatus, LifecycleStatus lifecycleStatus) {
        Restaurant restaurant = org.mockito.Mockito.mock(Restaurant.class);
        when(restaurant.getId()).thenReturn(id);
        when(restaurant.getName()).thenReturn(name);
        when(restaurant.getPublicationStatus()).thenReturn(publicationStatus);
        when(restaurant.getLifecycleStatus()).thenReturn(lifecycleStatus);
        return restaurant;
    }
}
