package com.masiton.orchestration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.orchestration.application.port.in.ResolveFoodCategoryUseCase.FoodCategoryResolutionCommand;
import com.masiton.orchestration.application.port.in.ResolveFoodCategoryUseCase.FoodCategoryResolutionStatus;
import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase;
import com.masiton.restaurant.domain.model.FoodCategoryMapping;
import com.masiton.restaurant.domain.model.FoodCategoryMappingMatchType;
import com.masiton.restaurant.domain.model.FoodCategoryMappingSourceType;

@DisplayName("BR-AIEXTRACT-010 대표 음식 카테고리 자동 선정")
class ResolveFoodCategoryServiceTest {

    private static final UUID KOREAN_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID JAPANESE_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000003");

    private final LookupFoodCategoryMappingUseCase mappingRepository = mock(LookupFoodCategoryMappingUseCase.class);
    private final ResolveFoodCategoryService service = new ResolveFoodCategoryService(mappingRepository);

    @Test
    @DisplayName("Kakao 분류가 매핑 표와 완전일치하면 1순위 근거로 확정한다")
    void resolve_Kakao분류가완전일치하면_1순위근거로확정한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY))
                .willReturn(List.of(mapping("한식", FoodCategoryMappingMatchType.EXACT, KOREAN_CATEGORY_ID, (short) 10)));
        given(mappingRepository.findCategoryName(KOREAN_CATEGORY_ID)).willReturn(Optional.of("한식"));

        var result = service.resolve(new FoodCategoryResolutionCommand("한식", null));

        assertThat(result.isResolved()).isTrue();
        assertThat(result.resolvedFoodCategory().foodCategoryName()).isEqualTo("한식");
        assertThat(result.resolvedFoodCategory().resolvedBy()).isEqualTo("KAKAO_PLACE_CATEGORY");
        verify(mappingRepository, never())
                .findActiveBySourceTypeOrderByMatchTypeThenPriority(FoodCategoryMappingSourceType.MENU_EXPRESSION);
    }

    @Test
    @DisplayName("계층형 Kakao 분류 표현은 PARTIAL 매핑으로 부분 일치해 확정한다")
    void resolve_계층형Kakao분류표현은_PARTIAL매핑으로확정한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY))
                .willReturn(List.of(mapping("한식", FoodCategoryMappingMatchType.PARTIAL, KOREAN_CATEGORY_ID, (short) 10)));
        given(mappingRepository.findCategoryName(KOREAN_CATEGORY_ID)).willReturn(Optional.of("한식"));

        var result = service.resolve(new FoodCategoryResolutionCommand("음식점 > 한식 > 냉면", null));

        assertThat(result.isResolved()).isTrue();
        assertThat(result.resolvedFoodCategory().foodCategoryName()).isEqualTo("한식");
    }

    @Test
    @DisplayName("Kakao 분류가 없으면 2순위 메뉴 표현 근거로 확정한다")
    void resolve_Kakao분류가없으면_2순위메뉴표현근거로확정한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.MENU_EXPRESSION))
                .willReturn(List.of(mapping(FoodCategoryMappingSourceType.MENU_EXPRESSION, "냉면",
                        FoodCategoryMappingMatchType.EXACT, KOREAN_CATEGORY_ID, (short) 10)));
        given(mappingRepository.findCategoryName(KOREAN_CATEGORY_ID)).willReturn(Optional.of("한식"));

        var result = service.resolve(new FoodCategoryResolutionCommand(null, "냉면"));

        assertThat(result.isResolved()).isTrue();
        assertThat(result.resolvedFoodCategory().resolvedBy()).isEqualTo("MENU_EXPRESSION");
        verify(mappingRepository, never())
                .findActiveBySourceTypeOrderByMatchTypeThenPriority(FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY);
    }

    @Test
    @DisplayName("Kakao 분류에 대응 값이 없으면 2순위 메뉴 표현 근거로 확정한다")
    void resolve_Kakao분류에대응값이없으면_2순위메뉴표현근거로확정한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY))
                .willReturn(List.of(mapping("일식", FoodCategoryMappingMatchType.EXACT, JAPANESE_CATEGORY_ID, (short) 10)));
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.MENU_EXPRESSION))
                .willReturn(List.of(mapping(FoodCategoryMappingSourceType.MENU_EXPRESSION, "냉면",
                        FoodCategoryMappingMatchType.EXACT, KOREAN_CATEGORY_ID, (short) 10)));
        given(mappingRepository.findCategoryName(KOREAN_CATEGORY_ID)).willReturn(Optional.of("한식"));

        var result = service.resolve(new FoodCategoryResolutionCommand("정체불명 분류", "냉면"));

        assertThat(result.isResolved()).isTrue();
        assertThat(result.resolvedFoodCategory().resolvedBy()).isEqualTo("MENU_EXPRESSION");
    }

    @Test
    @DisplayName("두 근거가 모두 실패하면 카테고리를 확정하지 못한 것으로 판정한다")
    void resolve_두근거가모두실패하면_확정하지못한것으로판정한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY)).willReturn(List.of());
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.MENU_EXPRESSION)).willReturn(List.of());

        var result = service.resolve(new FoodCategoryResolutionCommand("정체불명 분류", "정체불명 메뉴"));

        assertThat(result.status()).isEqualTo(FoodCategoryResolutionStatus.CATEGORY_UNRESOLVED);
        assertThat(result.resolvedFoodCategory()).isNull();
    }

    @Test
    @DisplayName("두 근거 후보 문자열이 모두 비어 있으면 카테고리를 확정하지 못한 것으로 판정한다")
    void resolve_두근거후보문자열이모두비어있으면_확정하지못한것으로판정한다() {
        var result = service.resolve(new FoodCategoryResolutionCommand(null, null));

        assertThat(result.status()).isEqualTo(FoodCategoryResolutionStatus.CATEGORY_UNRESOLVED);
    }

    @Test
    @DisplayName("같은 순위에서 서로 다른 카테고리로 일치하면 확정하지 못한 것으로 판정하고 메뉴 표현으로 넘어가지 않는다")
    void resolve_같은순위에서카테고리충돌하면_2순위로넘어가지않고확정하지못한것으로판정한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY))
                .willReturn(List.of(
                        mapping("한식", FoodCategoryMappingMatchType.PARTIAL, KOREAN_CATEGORY_ID, (short) 10),
                        mapping("식", FoodCategoryMappingMatchType.PARTIAL, JAPANESE_CATEGORY_ID, (short) 10)));

        var result = service.resolve(new FoodCategoryResolutionCommand("음식점 > 한식", "냉면"));

        assertThat(result.status()).isEqualTo(FoodCategoryResolutionStatus.CATEGORY_UNRESOLVED);
        verify(mappingRepository, never())
                .findActiveBySourceTypeOrderByMatchTypeThenPriority(FoodCategoryMappingSourceType.MENU_EXPRESSION);
    }

    @Test
    @DisplayName("EXACT 우선순위 매핑이 있으면 더 낮은 우선순위의 PARTIAL 매핑은 사용하지 않는다")
    void resolve_EXACT매핑이있으면_PARTIAL매핑보다우선한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY))
                .willReturn(List.of(
                        mapping("한식", FoodCategoryMappingMatchType.EXACT, KOREAN_CATEGORY_ID, (short) 10),
                        mapping("식", FoodCategoryMappingMatchType.PARTIAL, JAPANESE_CATEGORY_ID, (short) 10)));
        given(mappingRepository.findCategoryName(KOREAN_CATEGORY_ID)).willReturn(Optional.of("한식"));

        var result = service.resolve(new FoodCategoryResolutionCommand("한식", null));

        assertThat(result.isResolved()).isTrue();
        assertThat(result.resolvedFoodCategory().foodCategoryId()).isEqualTo(KOREAN_CATEGORY_ID);
    }

    @Test
    @DisplayName("공백과 대소문자 차이는 정규화 후 비교해 확정한다")
    void resolve_공백과대소문자차이_정규화후확정한다() {
        given(mappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(
                FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY))
                .willReturn(List.of(mapping("cafe", FoodCategoryMappingMatchType.EXACT, KOREAN_CATEGORY_ID, (short) 10)));
        given(mappingRepository.findCategoryName(KOREAN_CATEGORY_ID)).willReturn(Optional.of("카페·디저트"));

        var result = service.resolve(new FoodCategoryResolutionCommand(" CA FE ", null));

        assertThat(result.isResolved()).isTrue();
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
