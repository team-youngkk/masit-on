package com.masiton.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase.MappingOutcome;
import com.masiton.restaurant.application.port.out.FoodCategoryMappingRepositoryPort;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.domain.model.FoodCategoryMapping;
import com.masiton.restaurant.domain.model.FoodCategoryMappingMatchType;
import com.masiton.restaurant.domain.model.FoodCategoryMappingSourceType;

/**
 * {@code BR-AIEXTRACT-010}의 대조 순서(EXACT 우선, priority 오름차순)와 같은 순위 복수 일치
 * 충돌 판정을 검증한다. Kakao→메뉴 폴백 순서는 {@code ResolveFoodCategoryServiceTest}가 담당한다.
 */
@DisplayName("food_category_mapping 대조 순서")
class LookupFoodCategoryMappingServiceTest {

    private static final UUID KOREAN_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID JAPANESE_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000003");

    private final FoodCategoryMappingRepositoryPort mappingRepository = mock(FoodCategoryMappingRepositoryPort.class);
    private final FoodCategoryRepositoryPort foodCategoryRepository = mock(FoodCategoryRepositoryPort.class);
    private final LookupFoodCategoryMappingService service =
            new LookupFoodCategoryMappingService(mappingRepository, foodCategoryRepository);

    @Test
    @DisplayName("완전일치하면 확정한다")
    void resolveByKakaoPlaceCategory_완전일치하면_확정한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY))
                .willReturn(List.of(mapping("한식", FoodCategoryMappingMatchType.EXACT, KOREAN_CATEGORY_ID, (short) 10)));

        var result = service.resolveByKakaoPlaceCategory("한식");

        assertThat(result.outcome()).isEqualTo(MappingOutcome.MATCHED);
        assertThat(result.match().foodCategoryId()).isEqualTo(KOREAN_CATEGORY_ID);
    }

    @Test
    @DisplayName("계층형 표현은 PARTIAL 매핑으로 부분 일치해 확정한다")
    void resolveByKakaoPlaceCategory_계층형표현은_PARTIAL매핑으로확정한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY))
                .willReturn(List.of(mapping("한식", FoodCategoryMappingMatchType.PARTIAL, KOREAN_CATEGORY_ID, (short) 10)));

        var result = service.resolveByKakaoPlaceCategory("음식점 > 한식 > 냉면");

        assertThat(result.outcome()).isEqualTo(MappingOutcome.MATCHED);
        assertThat(result.match().foodCategoryId()).isEqualTo(KOREAN_CATEGORY_ID);
    }

    @Test
    @DisplayName("EXACT 우선순위 매핑이 있으면 더 낮은 우선순위의 PARTIAL 매핑은 사용하지 않는다")
    void resolveByKakaoPlaceCategory_EXACT매핑이있으면_PARTIAL매핑보다우선한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY))
                .willReturn(List.of(
                        mapping("한식", FoodCategoryMappingMatchType.EXACT, KOREAN_CATEGORY_ID, (short) 10),
                        mapping("식", FoodCategoryMappingMatchType.PARTIAL, JAPANESE_CATEGORY_ID, (short) 10)));

        var result = service.resolveByKakaoPlaceCategory("한식");

        assertThat(result.outcome()).isEqualTo(MappingOutcome.MATCHED);
        assertThat(result.match().foodCategoryId()).isEqualTo(KOREAN_CATEGORY_ID);
    }

    @Test
    @DisplayName("같은 순위에서 서로 다른 카테고리로 일치하면 충돌로 판정한다")
    void resolveByKakaoPlaceCategory_같은순위충돌하면_CONFLICT로판정한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY))
                .willReturn(List.of(
                        mapping("한식", FoodCategoryMappingMatchType.PARTIAL, KOREAN_CATEGORY_ID, (short) 10),
                        mapping("식", FoodCategoryMappingMatchType.PARTIAL, JAPANESE_CATEGORY_ID, (short) 10)));

        var result = service.resolveByKakaoPlaceCategory("음식점 > 한식");

        assertThat(result.outcome()).isEqualTo(MappingOutcome.CONFLICT);
    }

    @Test
    @DisplayName("일치하는 활성 행이 없으면 NONE으로 판정한다")
    void resolveByKakaoPlaceCategory_일치하는행이없으면_NONE으로판정한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY)).willReturn(List.of());

        var result = service.resolveByKakaoPlaceCategory("정체불명 분류");

        assertThat(result.outcome()).isEqualTo(MappingOutcome.NONE);
    }

    @Test
    @DisplayName("후보 문자열이 비어 있으면 대조하지 않고 NONE으로 판정한다")
    void resolveByKakaoPlaceCategory_후보문자열이비어있으면_대조하지않고NONE으로판정한다() {
        var result = service.resolveByKakaoPlaceCategory(null);

        assertThat(result.outcome()).isEqualTo(MappingOutcome.NONE);
    }

    @Test
    @DisplayName("공백과 대소문자 차이는 정규화 후 비교해 확정한다")
    void resolveByMenuExpression_공백과대소문자차이_정규화후확정한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.MENU_EXPRESSION))
                .willReturn(List.of(mapping(FoodCategoryMappingSourceType.MENU_EXPRESSION, "cafe",
                        FoodCategoryMappingMatchType.EXACT, KOREAN_CATEGORY_ID, (short) 10)));

        var result = service.resolveByMenuExpression(" CA FE ");

        assertThat(result.outcome()).isEqualTo(MappingOutcome.MATCHED);
    }

    private FoodCategoryMapping mapping(
            String pattern, FoodCategoryMappingMatchType matchType, UUID foodCategoryId, short priority) {
        return mapping(FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY, pattern, matchType, foodCategoryId, priority);
    }

    private FoodCategoryMapping mapping(FoodCategoryMappingSourceType sourceType, String pattern,
                                         FoodCategoryMappingMatchType matchType, UUID foodCategoryId, short priority) {
        return new FoodCategoryMapping(UUID.randomUUID(), sourceType, pattern, matchType, foodCategoryId, priority,
                true, OffsetDateTime.now(), OffsetDateTime.now());
    }
}
