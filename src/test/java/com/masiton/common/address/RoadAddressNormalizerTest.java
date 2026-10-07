package com.masiton.common.address;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("전국 주소의 지역 경계")
class RoadAddressNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "서울 마포구 월드컵로 1,서울특별시,마포구",
            "부산 중구 중앙대로 1,부산광역시,중구",
            "경기 수원시 영통구 광교로 1,경기도,수원시",
            "제주 제주시 첨단로 1,제주특별자치도,제주시",
            "제주도 서귀포시 중문로 1,제주특별자치도,서귀포시",
            "세종 한누리대로 1,세종특별자치시,세종특별자치시",
            "세종특별자치시 조치원읍 새내로 1,세종특별자치시,세종특별자치시",
            "강원도 춘천시 중앙로 1,강원특별자치도,춘천시",
            "전북 전주시 완산구 기린대로 1,전북특별자치도,전주시",
            "광주광역시 동구 금남로 1,전남광주통합특별시,동구",
            "전남 목포시 영산로 1,전남광주통합특별시,목포시",
            "전남광주통합특별시 순천시 중앙로 1,전남광주통합특별시,순천시"
    })
    @DisplayName("시도 별칭과 특수 행정구조를 두 단계로 해석한다")
    void 지역해석_지원주소_시도와시군구로반환한다(String address, String province, String municipality) {
        // Given / When
        var region = RoadAddressNormalizer.extractRegion(address);
        // Then
        assertThat(region).contains(new RoadAddressNormalizer.RegionAddress(province, municipality));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "서울특별시", "서울특별시 마포구", "서울특별시마포구 월드컵로 1",
            "경기 수원시 영통구", "세종 가짜구 도로 1", "일본 도쿄구 거리 1"})
    @DisplayName("불완전하거나 시도 경계가 없는 주소를 지역으로 추측하지 않는다")
    void 지역해석_불완전주소_빈값을반환한다(String address) {
        // Given / When / Then
        assertThat(RoadAddressNormalizer.extractRegion(address)).isEmpty();
    }
}
