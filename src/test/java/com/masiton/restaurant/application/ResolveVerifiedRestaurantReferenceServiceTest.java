package com.masiton.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.application.port.out.PlaceVerificationPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.application.port.out.VerifiedPlace;
import com.masiton.restaurant.domain.model.FoodCategory;
import com.masiton.restaurant.domain.model.Region;

@DisplayName("검증된 맛집 참조 해석기")
class ResolveVerifiedRestaurantReferenceServiceTest {

    private final PlaceVerificationPort placeVerification = mock(PlaceVerificationPort.class);
    private final RegionRepositoryPort regionRepository = mock(RegionRepositoryPort.class);
    private final FoodCategoryRepositoryPort foodCategoryRepository = mock(FoodCategoryRepositoryPort.class);
    private final ResolveVerifiedRestaurantReferenceService service =
            new ResolveVerifiedRestaurantReferenceService(placeVerification, regionRepository, foodCategoryRepository);

    @Test
    @DisplayName("메뉴 표현을 대표 카테고리로 매핑해 냉면을 한식으로 해석한다")
    void resolve_메뉴표현냉면_대표카테고리한식으로해석한다() {
        // Given
        URI kakaoUrl = URI.create("https://place.map.kakao.com/123");
        given(placeVerification.verify("맛집", kakaoUrl, null)).willReturn(Optional.of(place("서울특별시 마포구 월드컵로 1")));
        Region region = mock(Region.class);
        given(region.isActive()).willReturn(true);
        given(region.getId()).willReturn(UUID.randomUUID());
        given(regionRepository.findByName("마포구")).willReturn(Optional.of(region));
        FoodCategory category = mock(FoodCategory.class);
        given(category.isActive()).willReturn(true);
        UUID categoryId = UUID.randomUUID();
        given(category.getId()).willReturn(categoryId);
        given(foodCategoryRepository.findByName("한식")).willReturn(Optional.of(category));

        // When
        var result = service.resolve("맛집", "서울특별시 마포구 월드컵로 1", kakaoUrl, "냉면");

        // Then
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().foodCategoryId()).isEqualTo(categoryId);
        verify(foodCategoryRepository).findByName(eq("한식"));
    }

    @Test
    @DisplayName("주소가 다른 지점의 접두어이면 장소를 동일하다고 판정하지 않는다")
    void resolve_주소가다른지점접두어_동일장소로판정하지않는다() {
        // Given
        URI kakaoUrl = URI.create("https://place.map.kakao.com/123");
        given(placeVerification.verify("맛집", kakaoUrl, null))
                .willReturn(Optional.of(place("서울특별시 마포구 월드컵로 10")));

        // When
        var result = service.resolve("맛집", "서울특별시 마포구 월드컵로 1", kakaoUrl, "한식");

        // Then
        assertThat(result).isEmpty();
    }

    private VerifiedPlace place(String address) {
        return new VerifiedPlace("kakao-1", "맛집", "https://place.map.kakao.com/123", address,
                "02-1234-5678", BigDecimal.valueOf(126.9), BigDecimal.valueOf(37.5));
    }
}
