package com.masiton.restaurant.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("서울 도로명주소 정규화")
class SeoulRoadAddressNormalizerTest {

    @Test
    @DisplayName("축약 시도명과 전체 시도명은 서울특별시 표기로 정규화한다")
    void 정규화_서울표기_전체시도명으로반환한다() {
        assertThat(SeoulRoadAddressNormalizer.normalize(" 서울 강동구 성내동 12-38 "))
                .isEqualTo("서울특별시 강동구 성내동 12-38");
        assertThat(SeoulRoadAddressNormalizer.normalize("서울특별시 강동구 성내동 12-38"))
                .isEqualTo("서울특별시 강동구 성내동 12-38");
    }

    @Test
    @DisplayName("서울 밖 주소는 앞뒤 공백만 제거한다")
    void 정규화_서울밖주소_표기를유지한다() {
        assertThat(SeoulRoadAddressNormalizer.normalize(" 부산 영도구 태종로99번길 28 "))
                .isEqualTo("부산 영도구 태종로99번길 28");
    }
}
