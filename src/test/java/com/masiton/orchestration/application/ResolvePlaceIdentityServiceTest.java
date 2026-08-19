package com.masiton.orchestration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.orchestration.application.port.in.ResolvePlaceIdentityUseCase.PlaceIdentityCommand;
import com.masiton.orchestration.application.port.in.ResolvePlaceIdentityUseCase.PlaceIdentityStatus;
import com.masiton.restaurant.application.port.out.PlaceSearchCandidate;
import com.masiton.restaurant.application.port.in.SearchPlacesByNameUseCase;

@DisplayName("BR-AIEXTRACT-009 장소 동일성 자동 확정")
class ResolvePlaceIdentityServiceTest {

    private final SearchPlacesByNameUseCase placeSearchPort = mock(SearchPlacesByNameUseCase.class);
    private final ResolvePlaceIdentityService service = new ResolvePlaceIdentityService(placeSearchPort);

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
}
