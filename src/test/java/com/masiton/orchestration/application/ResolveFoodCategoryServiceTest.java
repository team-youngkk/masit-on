package com.masiton.orchestration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.orchestration.application.port.in.ResolveFoodCategoryUseCase.FoodCategoryResolutionCommand;
import com.masiton.orchestration.application.port.in.ResolveFoodCategoryUseCase.FoodCategoryResolutionStatus;
import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase;
import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase.MappingResolution;

/**
 * {@code BR-AIEXTRACT-010}의 1순위(Kakao 분류)→2순위(메뉴 표현) 폴백 순서만 검증한다. 대조 순서·
 * 같은 순위 충돌 판정 자체는 restaurant 도메인({@code LookupFoodCategoryMappingServiceTest})이
 * 소유하므로 여기서는 {@link LookupFoodCategoryMappingUseCase}를 {@link MappingResolution} 단위로
 * mock한다.
 */
@DisplayName("BR-AIEXTRACT-010 대표 음식 카테고리 자동 선정")
class ResolveFoodCategoryServiceTest {

    private static final UUID KOREAN_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID JAPANESE_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000003");
    private static final UUID KOREAN_MAPPING_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final UUID JAPANESE_MAPPING_ID = UUID.fromString("40000000-0000-4000-8000-000000000002");

    private final LookupFoodCategoryMappingUseCase lookup = mock(LookupFoodCategoryMappingUseCase.class);
    private final ResolveFoodCategoryService service = new ResolveFoodCategoryService(lookup);

    @Test
    @DisplayName("Kakao 분류가 확정되면 1순위 근거로 확정하고 메뉴 표현은 대조하지 않는다")
    void resolve_Kakao분류가확정되면_1순위근거로확정하고메뉴는대조하지않는다() {
        given(lookup.resolveByKakaoPlaceCategory("한식"))
                .willReturn(MappingResolution.matched(KOREAN_MAPPING_ID, KOREAN_CATEGORY_ID));
        given(lookup.findCategoryName(KOREAN_CATEGORY_ID)).willReturn(Optional.of("한식"));

        var result = service.resolve(new FoodCategoryResolutionCommand("한식", "냉면"));

        assertThat(result.isResolved()).isTrue();
        assertThat(result.resolvedFoodCategory().foodCategoryName()).isEqualTo("한식");
        assertThat(result.resolvedFoodCategory().resolvedBy()).isEqualTo("KAKAO_PLACE_CATEGORY");
        assertThat(result.resolvedFoodCategory().matchedMappingId()).isEqualTo(KOREAN_MAPPING_ID);
        verify(lookup, never()).resolveByMenuExpression("냉면");
    }

    @Test
    @DisplayName("Kakao 분류가 일치하지 않으면(NONE) 2순위 메뉴 표현 근거로 확정한다")
    void resolve_Kakao분류가일치하지않으면_2순위메뉴표현근거로확정한다() {
        given(lookup.resolveByKakaoPlaceCategory("정체불명 분류")).willReturn(MappingResolution.none());
        given(lookup.resolveByMenuExpression("냉면"))
                .willReturn(MappingResolution.matched(KOREAN_MAPPING_ID, KOREAN_CATEGORY_ID));
        given(lookup.findCategoryName(KOREAN_CATEGORY_ID)).willReturn(Optional.of("한식"));

        var result = service.resolve(new FoodCategoryResolutionCommand("정체불명 분류", "냉면"));

        assertThat(result.isResolved()).isTrue();
        assertThat(result.resolvedFoodCategory().resolvedBy()).isEqualTo("MENU_EXPRESSION");
    }

    @Test
    @DisplayName("두 근거가 모두 일치하지 않으면 카테고리를 확정하지 못한 것으로 판정한다")
    void resolve_두근거가모두일치하지않으면_확정하지못한것으로판정한다() {
        given(lookup.resolveByKakaoPlaceCategory("정체불명 분류")).willReturn(MappingResolution.none());
        given(lookup.resolveByMenuExpression("정체불명 메뉴")).willReturn(MappingResolution.none());

        var result = service.resolve(new FoodCategoryResolutionCommand("정체불명 분류", "정체불명 메뉴"));

        assertThat(result.status()).isEqualTo(FoodCategoryResolutionStatus.CATEGORY_UNRESOLVED);
        assertThat(result.resolvedFoodCategory()).isNull();
    }

    @Test
    @DisplayName("Kakao 분류에서 같은 순위 충돌이 나면 메뉴 표현으로 넘어가지 않고 확정하지 못한 것으로 판정한다")
    void resolve_Kakao분류가충돌하면_메뉴표현으로넘어가지않는다() {
        given(lookup.resolveByKakaoPlaceCategory("음식점 > 한식")).willReturn(MappingResolution.conflict());

        var result = service.resolve(new FoodCategoryResolutionCommand("음식점 > 한식", "냉면"));

        assertThat(result.status()).isEqualTo(FoodCategoryResolutionStatus.CATEGORY_UNRESOLVED);
        verify(lookup, never()).resolveByMenuExpression("냉면");
    }

    @Test
    @DisplayName("메뉴 표현에서 충돌이 나면 확정하지 못한 것으로 판정한다")
    void resolve_메뉴표현이충돌하면_확정하지못한것으로판정한다() {
        given(lookup.resolveByKakaoPlaceCategory("정체불명 분류")).willReturn(MappingResolution.none());
        given(lookup.resolveByMenuExpression("냉면")).willReturn(MappingResolution.conflict());

        var result = service.resolve(new FoodCategoryResolutionCommand("정체불명 분류", "냉면"));

        assertThat(result.status()).isEqualTo(FoodCategoryResolutionStatus.CATEGORY_UNRESOLVED);
    }

    @Test
    @DisplayName("확정한 카테고리 식별자의 이름을 찾지 못하면 확정하지 못한 것으로 판정한다")
    void resolve_카테고리이름을찾지못하면_확정하지못한것으로판정한다() {
        given(lookup.resolveByKakaoPlaceCategory("한식"))
                .willReturn(MappingResolution.matched(KOREAN_MAPPING_ID, KOREAN_CATEGORY_ID));
        given(lookup.findCategoryName(KOREAN_CATEGORY_ID)).willReturn(Optional.empty());

        var result = service.resolve(new FoodCategoryResolutionCommand("한식", null));

        assertThat(result.status()).isEqualTo(FoodCategoryResolutionStatus.CATEGORY_UNRESOLVED);
    }

    @Test
    @DisplayName("메뉴 표현 근거로 확정하면 매핑 행 식별자와 카테고리를 그대로 전달한다")
    void resolve_메뉴표현근거로확정하면_매핑행식별자를전달한다() {
        given(lookup.resolveByKakaoPlaceCategory(null)).willReturn(MappingResolution.none());
        given(lookup.resolveByMenuExpression("초밥"))
                .willReturn(MappingResolution.matched(JAPANESE_MAPPING_ID, JAPANESE_CATEGORY_ID));
        given(lookup.findCategoryName(JAPANESE_CATEGORY_ID)).willReturn(Optional.of("일식"));

        var result = service.resolve(new FoodCategoryResolutionCommand(null, "초밥"));

        assertThat(result.isResolved()).isTrue();
        assertThat(result.resolvedFoodCategory().foodCategoryId()).isEqualTo(JAPANESE_CATEGORY_ID);
        assertThat(result.resolvedFoodCategory().matchedMappingId()).isEqualTo(JAPANESE_MAPPING_ID);
    }
}
