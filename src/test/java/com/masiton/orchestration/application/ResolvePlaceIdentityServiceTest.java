package com.masiton.orchestration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.orchestration.application.port.in.ResolvePlaceIdentityUseCase.PlaceIdentityCommand;
import com.masiton.orchestration.application.port.in.ResolvePlaceIdentityUseCase.PlaceIdentityStatus;
import com.masiton.orchestration.application.port.out.PlaceIdentityMatchingPolicy;
import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase;
import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase.MappingResolution;
import com.masiton.restaurant.application.port.in.SearchPlacesByNameUseCase;
import com.masiton.restaurant.application.port.out.PlaceSearchCandidate;

@DisplayName("BR-AIEXTRACT-009 장소 동일성 자동 확정")
class ResolvePlaceIdentityServiceTest {

    private final SearchPlacesByNameUseCase placeSearchPort = mock(SearchPlacesByNameUseCase.class);
    private final LookupFoodCategoryMappingUseCase categoryMapping = mock(LookupFoodCategoryMappingUseCase.class);
    private final PlaceIdentityMatchingPolicy relaxedMatchingEnabled = () -> true;
    private final ResolvePlaceIdentityService service = new ResolvePlaceIdentityService(
            placeSearchPort, categoryMapping, relaxedMatchingEnabled);

    @Test
    @DisplayName("이름 완전일치와 시구 일치를 만족하는 결과가 정확히 1건이면 확정한다")
    void resolve_이름과시구가일치하는결과가하나_확정한다() {
        given(placeSearchPort.search("행복식당")).willReturn(List.of(
                new PlaceSearchCandidate("행복식당", "https://place.map.kakao.com/1",
                        "서울특별시 영등포구 도림로131길 17", "02-000-0000", "음식점 > 한식 > 냉면")));

        var result = service.resolve(new PlaceIdentityCommand("행복식당", "서울특별시 영등포구 도림로 100"));

        assertThat(result.isConfirmed()).isTrue();
        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.CONFIRMED);
        assertThat(result.confirmedPlace().kakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/1");
        assertThat(result.confirmedPlace().roadAddress()).isEqualTo("서울특별시 영등포구 도림로131길 17");
        assertThat(result.confirmedPlace().matchedBy()).isEqualTo("NAME_AND_DISTRICT");
        assertThat(result.confirmedPlace().placeCategory()).isEqualTo("음식점 > 한식 > 냉면");
        org.mockito.Mockito.verifyNoInteractions(categoryMapping);
    }

    @Test
    @DisplayName("공백과 대소문자 차이는 정규화 후 비교해 확정한다")
    void resolve_공백과대소문자차이_정규화후확정한다() {
        given(placeSearchPort.search("ABC Cafe")).willReturn(List.of(
                new PlaceSearchCandidate("abccafe", "https://place.map.kakao.com/1",
                        "서울특별시 마포구 월드컵로 1", "02-000-0000", "카페")));

        var result = service.resolve(new PlaceIdentityCommand("ABC Cafe", "서울특별시 마포구 어딘가"));

        assertThat(result.isConfirmed()).isTrue();
    }

    @Test
    @DisplayName("정확 일치 후보가 있으면 완화 후보가 있어도 정확 일치 결과를 우선한다")
    void resolve_정확일치와완화후보가함께있으면_정확일치를우선한다() {
        given(placeSearchPort.search("우래옥")).willReturn(List.of(
                new PlaceSearchCandidate("우래옥", "https://place.map.kakao.com/exact",
                        "서울특별시 중구 세종대로 1", "02-000-0000", null),
                new PlaceSearchCandidate("우래옥 본점", "https://place.map.kakao.com/relaxed",
                        "서울특별시 중구 창경궁로 62-29", "02-111-1111", "음식점 > 한식 > 냉면")));

        var result = service.resolve(new PlaceIdentityCommand("우래옥", "서울특별시 중구 세종대로 10", "냉면"));

        assertThat(result.isConfirmed()).isTrue();
        assertThat(result.confirmedPlace().kakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/exact");
        assertThat(result.confirmedPlace().matchedBy()).isEqualTo("NAME_AND_DISTRICT");
        org.mockito.Mockito.verifyNoInteractions(categoryMapping);
    }

    @Test
    @DisplayName("정확 일치 후보가 여러 개면 완화 후보가 있어도 모호하게 차단한다")
    void resolve_정확일치가여러개이고완화후보가있으면_모호하게차단한다() {
        given(placeSearchPort.search("우래옥")).willReturn(List.of(
                new PlaceSearchCandidate("우래옥", "https://place.map.kakao.com/exact-1",
                        "서울특별시 중구 세종대로 1", "02-000-0000", null),
                new PlaceSearchCandidate("우래옥", "https://place.map.kakao.com/exact-2",
                        "서울특별시 중구 세종대로 10", "02-111-1111", null),
                new PlaceSearchCandidate("우래옥 본점", "https://place.map.kakao.com/relaxed",
                        "서울특별시 중구 창경궁로 62-29", "02-222-2222", "음식점 > 한식 > 냉면")));

        var result = service.resolve(new PlaceIdentityCommand("우래옥", "서울특별시 중구 세종대로 100", "냉면"));

        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.PLACE_AMBIGUOUS);
        assertThat(result.confirmedPlace()).isNull();
        org.mockito.Mockito.verifyNoInteractions(categoryMapping);
    }

    @Test
    @DisplayName("조건을 만족하는 검색 결과가 없으면 장소를 찾지 못한 것으로 판정한다")
    void resolve_조건을만족하는결과가없으면_장소를찾지못한것으로판정한다() {
        given(placeSearchPort.search("행복식당")).willReturn(List.of(
                new PlaceSearchCandidate("다른가게", "https://place.map.kakao.com/1",
                        "서울특별시 영등포구 도림로131길 17", "02-000-0000", null)));

        var result = service.resolve(new PlaceIdentityCommand("행복식당", "서울특별시 영등포구 도림로 100"));

        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.PLACE_NOT_FOUND);
        assertThat(result.confirmedPlace()).isNull();
    }

    @Test
    @DisplayName("이름은 같아도 자치구가 다르면 장소를 찾지 못한 것으로 판정한다")
    void resolve_이름은같아도자치구가다르면_장소를찾지못한것으로판정한다() {
        given(placeSearchPort.search("행복식당")).willReturn(List.of(
                new PlaceSearchCandidate("행복식당", "https://place.map.kakao.com/1",
                        "서울특별시 마포구 월드컵로 1", "02-000-0000", null)));

        var result = service.resolve(new PlaceIdentityCommand("행복식당", "서울특별시 영등포구 도림로 100"));

        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("이름과 도로명주소가 모두 없는 후보는 필수값 누락으로 제외한다")
    void resolve_도로명주소가없는후보는_필수값누락으로제외한다() {
        given(placeSearchPort.search("행복식당")).willReturn(List.of(
                new PlaceSearchCandidate("행복식당", "https://place.map.kakao.com/1", null, "02-000-0000", null)));

        var result = service.resolve(new PlaceIdentityCommand("행복식당", "서울특별시 영등포구 도림로 100"));

        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("조건을 만족하는 결과가 둘 이상이면 모호한 것으로 판정한다")
    void resolve_조건을만족하는결과가둘이상이면_모호한것으로판정한다() {
        given(placeSearchPort.search("행복식당")).willReturn(List.of(
                new PlaceSearchCandidate("행복식당", "https://place.map.kakao.com/1",
                        "서울특별시 영등포구 도림로131길 17", "02-000-0000", null),
                new PlaceSearchCandidate("행복식당", "https://place.map.kakao.com/2",
                        "서울특별시 영등포구 여의대로 10", "02-111-1111", null)));

        var result = service.resolve(new PlaceIdentityCommand("행복식당", "서울특별시 영등포구 도림로 100"));

        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.PLACE_AMBIGUOUS);
        assertThat(result.confirmedPlace()).isNull();
    }

    @Test
    @DisplayName("상호명 또는 주소 후보가 비어 있으면 장소를 찾지 못한 것으로 판정하고 검색을 호출하지 않는다")
    void resolve_상호명또는주소후보가비어있으면_검색을호출하지않는다() {
        var result = service.resolve(new PlaceIdentityCommand("", "서울특별시 영등포구 도림로 100"));

        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.PLACE_NOT_FOUND);
        org.mockito.Mockito.verifyNoInteractions(placeSearchPort);
    }

    @Test
    @DisplayName("주소 후보에서 서울 자치구를 추출할 수 없으면 장소를 찾지 못한 것으로 판정한다")
    void resolve_자치구를추출할수없는주소후보는_장소를찾지못한것으로판정한다() {
        var result = service.resolve(new PlaceIdentityCommand("행복식당", "부산광역시 해운대구 어딘가"));

        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.PLACE_NOT_FOUND);
        org.mockito.Mockito.verifyNoInteractions(placeSearchPort);
    }

    @Test
    @DisplayName("정확 일치가 없을 때 우래옥과 우래옥 본점이 같은 자치구·카테고리 근거로 유일하면 완화 확정한다")
    void resolve_우래옥과우래옥본점이카테고리까지일치하는유일후보면_완화확정한다() {
        // Given
        var foodCategoryId = java.util.UUID.randomUUID();
        given(placeSearchPort.search("우래옥")).willReturn(List.of(
                new PlaceSearchCandidate("우래옥 본점", "https://place.map.kakao.com/1",
                        "서울특별시 중구 창경궁로 62-29", "02-000-0000", "음식점 > 한식 > 냉면")));
        given(categoryMapping.resolveByKakaoPlaceCategory("음식점 > 한식 > 냉면"))
                .willReturn(MappingResolution.matched(java.util.UUID.randomUUID(), foodCategoryId));
        given(categoryMapping.resolveByMenuExpression("냉면"))
                .willReturn(MappingResolution.matched(java.util.UUID.randomUUID(), foodCategoryId));

        // When
        var result = service.resolve(new PlaceIdentityCommand("우래옥", "서울특별시 중구 세종대로 1", "냉면"));

        // Then
        assertThat(result.isConfirmed()).isTrue();
        assertThat(result.confirmedPlace().matchedBy()).isEqualTo("NAME_CONTAINMENT_AND_DISTRICT_AND_CATEGORY");
        assertThat(result.confirmedPlace().kakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/1");
    }

    @Test
    @DisplayName("AI 상호명에 붙은 본점이 Kakao 상호명에 없으면 기본 상호명 검색과 역방향 완화로 확정한다")
    void resolve_AI상호명에지점명이붙고Kakao상호명은기본명이면_역방향완화확정한다() {
        // Given
        var foodCategoryId = java.util.UUID.randomUUID();
        given(placeSearchPort.search("우래옥 본점")).willReturn(List.of());
        given(placeSearchPort.search("우래옥")).willReturn(List.of(
                new PlaceSearchCandidate("우래옥", "https://place.map.kakao.com/1",
                        "서울특별시 중구 창경궁로 62-29", "02-000-0000", "음식점 > 한식 > 냉면")));
        given(categoryMapping.resolveByKakaoPlaceCategory("음식점 > 한식 > 냉면"))
                .willReturn(MappingResolution.matched(java.util.UUID.randomUUID(), foodCategoryId));
        given(categoryMapping.resolveByMenuExpression("냉면"))
                .willReturn(MappingResolution.matched(java.util.UUID.randomUUID(), foodCategoryId));

        // When
        var result = service.resolve(new PlaceIdentityCommand(
                "우래옥 본점", "서울특별시 중구 세종대로 1", "냉면"));

        // Then
        assertThat(result.isConfirmed()).isTrue();
        assertThat(result.confirmedPlace().matchedBy())
                .isEqualTo("NAME_CONTAINMENT_AND_DISTRICT_AND_CATEGORY");
        assertThat(result.confirmedPlace().kakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/1");
        org.mockito.Mockito.verify(placeSearchPort).search("우래옥 본점");
        org.mockito.Mockito.verify(placeSearchPort).search("우래옥");
    }

    @Test
    @DisplayName("원래 검색과 기본 상호명 검색이 같은 후보를 반환해도 한 건으로 중복 제거한다")
    void resolve_원래검색과기본상호명검색이같은후보면_중복제거후확정한다() {
        var foodCategoryId = java.util.UUID.randomUUID();
        var candidate = new PlaceSearchCandidate(
                "우래옥", "https://place.map.kakao.com/1",
                "서울특별시 중구 창경궁로 62-29", "02-000-0000", "음식점 > 한식 > 냉면");
        given(placeSearchPort.search("우래옥 본점")).willReturn(List.of(candidate));
        given(placeSearchPort.search("우래옥")).willReturn(List.of(candidate));
        given(categoryMapping.resolveByKakaoPlaceCategory("음식점 > 한식 > 냉면"))
                .willReturn(MappingResolution.matched(java.util.UUID.randomUUID(), foodCategoryId));
        given(categoryMapping.resolveByMenuExpression("냉면"))
                .willReturn(MappingResolution.matched(java.util.UUID.randomUUID(), foodCategoryId));

        var result = service.resolve(new PlaceIdentityCommand(
                "우래옥 본점", "서울특별시 중구 세종대로 1", "냉면"));

        assertThat(result.isConfirmed()).isTrue();
        assertThat(result.confirmedPlace().kakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/1");
    }

    @Test
    @DisplayName("일반 단어의 점으로 끝나는 표현은 지점 접미사로 오인하지 않는다")
    void resolve_일반단어의점표현은_지점접미사로오인하지않는다() {
        given(placeSearchPort.search("독점")).willReturn(List.of());

        var result = service.resolve(new PlaceIdentityCommand(
                "독점", "서울특별시 중구 세종대로 1", "냉면"));

        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.PLACE_NOT_FOUND);
        org.mockito.Mockito.verify(placeSearchPort).search("독점");
        org.mockito.Mockito.verifyNoMoreInteractions(placeSearchPort);
    }

    @Test
    @DisplayName("완화 후보의 Kakao 분류와 메뉴 표현 카테고리가 다르면 확정하지 않는다")
    void resolve_완화후보의카테고리가다르면_확정하지않는다() {
        // Given
        var kakaoCategoryId = java.util.UUID.randomUUID();
        var menuCategoryId = java.util.UUID.randomUUID();
        given(placeSearchPort.search("우래옥")).willReturn(List.of(
                new PlaceSearchCandidate("우래옥 본점", "https://place.map.kakao.com/1",
                        "서울특별시 중구 창경궁로 62-29", "02-000-0000", "음식점 > 한식 > 냉면")));
        given(categoryMapping.resolveByKakaoPlaceCategory("음식점 > 한식 > 냉면"))
                .willReturn(MappingResolution.matched(java.util.UUID.randomUUID(), kakaoCategoryId));
        given(categoryMapping.resolveByMenuExpression("초밥"))
                .willReturn(MappingResolution.matched(java.util.UUID.randomUUID(), menuCategoryId));

        // When
        var result = service.resolve(new PlaceIdentityCommand("우래옥", "서울특별시 중구 세종대로 1", "초밥"));

        // Then
        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("완화 조건을 만족하는 후보가 둘이면 임의로 선택하지 않고 모호하게 판정한다")
    void resolve_완화조건을만족하는후보가둘이면_모호하게판정한다() {
        // Given
        var foodCategoryId = java.util.UUID.randomUUID();
        given(placeSearchPort.search("우래옥")).willReturn(List.of(
                new PlaceSearchCandidate("우래옥 본점", "https://place.map.kakao.com/1",
                        "서울특별시 중구 창경궁로 62-29", "02-000-0000", "음식점 > 한식 > 냉면"),
                new PlaceSearchCandidate("우래옥 별관", "https://place.map.kakao.com/2",
                        "서울특별시 중구 세종대로 10", "02-111-1111", "음식점 > 한식 > 냉면")));
        given(categoryMapping.resolveByKakaoPlaceCategory("음식점 > 한식 > 냉면"))
                .willReturn(MappingResolution.matched(java.util.UUID.randomUUID(), foodCategoryId));
        given(categoryMapping.resolveByMenuExpression("냉면"))
                .willReturn(MappingResolution.matched(java.util.UUID.randomUUID(), foodCategoryId));

        // When
        var result = service.resolve(new PlaceIdentityCommand("우래옥", "서울특별시 중구 세종대로 1", "냉면"));

        // Then
        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.PLACE_AMBIGUOUS);
        assertThat(result.confirmedPlace()).isNull();
    }

    @Test
    @DisplayName("완화 후보에 카테고리 근거가 없으면 확정하지 않는다")
    void resolve_완화후보에카테고리근거가없으면_확정하지않는다() {
        // Given
        given(placeSearchPort.search("우래옥")).willReturn(List.of(
                new PlaceSearchCandidate("우래옥 본점", "https://place.map.kakao.com/1",
                        "서울특별시 중구 창경궁로 62-29", "02-000-0000", null)));

        // When
        var result = service.resolve(new PlaceIdentityCommand("우래옥", "서울특별시 중구 세종대로 1", "냉면"));

        // Then
        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("완화 후보의 필수 장소 필드가 없으면 카테고리 근거가 있어도 확정하지 않는다")
    void resolve_완화후보의필수필드가없으면_확정하지않는다() {
        // Given
        var foodCategoryId = java.util.UUID.randomUUID();
        given(placeSearchPort.search("우래옥")).willReturn(List.of(
                new PlaceSearchCandidate("우래옥 본점", null,
                        "서울특별시 중구 창경궁로 62-29", "02-000-0000", "음식점 > 한식 > 냉면")));
        given(categoryMapping.resolveByKakaoPlaceCategory("음식점 > 한식 > 냉면"))
                .willReturn(MappingResolution.matched(java.util.UUID.randomUUID(), foodCategoryId));
        given(categoryMapping.resolveByMenuExpression("냉면"))
                .willReturn(MappingResolution.matched(java.util.UUID.randomUUID(), foodCategoryId));

        // When
        var result = service.resolve(new PlaceIdentityCommand("우래옥", "서울특별시 중구 세종대로 1", "냉면"));

        // Then
        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("완화 판정 플래그가 꺼져 있으면 이름 포함 후보를 확정하지 않는다")
    void resolve_완화판정플래그가꺼져있으면_이름포함후보를확정하지않는다() {
        given(placeSearchPort.search("우래옥 본점")).willReturn(List.of());

        ResolvePlaceIdentityService disabledService = new ResolvePlaceIdentityService(
                placeSearchPort, categoryMapping, () -> false);

        var result = disabledService.resolve(new PlaceIdentityCommand(
                "우래옥 본점", "서울특별시 중구 세종대로 1", "냉면"));

        assertThat(result.status()).isEqualTo(PlaceIdentityStatus.PLACE_NOT_FOUND);
        org.mockito.Mockito.verifyNoInteractions(categoryMapping);
        org.mockito.Mockito.verify(placeSearchPort).search("우래옥 본점");
        org.mockito.Mockito.verifyNoMoreInteractions(placeSearchPort);
    }
}
