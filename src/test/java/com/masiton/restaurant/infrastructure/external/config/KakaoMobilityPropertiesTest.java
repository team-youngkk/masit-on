package com.masiton.restaurant.infrastructure.external.config;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakaoMobilityPropertiesTest {

    @Test
    @DisplayName("월 quota가 1000을 초과하면 기동 설정을 거부한다")
    void 설정검증_월Quota가1000초과_기동설정을거부한다() {
        KakaoMobilityProperties properties = validProperties();
        properties.setMonthlyQuota(1_001);

        assertThatThrownBy(properties::validateFixedContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 1 and 1000");
    }

    @Test
    @DisplayName("운영 Mobility endpoint와 local WireMock endpoint만 허용한다")
    void 설정검증_baseUrl허용목록_운영과로컬만허용한다() {
        KakaoMobilityProperties properties = validProperties();
        properties.setBaseUrl("https://apis-navi.kakaomobility.com");
        assertThatCode(properties::validateFixedContract).doesNotThrowAnyException();

        properties.setBaseUrl("https://example.invalid");
        assertThatThrownBy(properties::validateFixedContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowed provider endpoint");
    }

    private KakaoMobilityProperties validProperties() {
        KakaoMobilityProperties properties = new KakaoMobilityProperties();
        properties.setEnabled(true);
        properties.setFreeTierVerified(true);
        properties.setRestApiKey("test-only-key");
        properties.setBaseUrl("http://localhost:8081");
        properties.setLocalBaseUrlAllowed(true);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setResponseTimeout(Duration.ofSeconds(4));
        properties.setTotalTimeout(Duration.ofSeconds(5));
        return properties;
    }
}
