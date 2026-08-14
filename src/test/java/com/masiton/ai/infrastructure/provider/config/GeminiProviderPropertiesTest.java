package com.masiton.ai.infrastructure.provider.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeminiProviderPropertiesTest {

    @Test
    @DisplayName("계약과 다른 모델 버전은 애플리케이션 시작 전에 거부한다")
    void 설정_계약외모델_시작전에거부한다() {
        GeminiProviderProperties properties = new GeminiProviderProperties();
        properties.setModel("gemini-other-model");

        assertThatThrownBy(properties::validateFixedContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fixed by the AI contract");
    }

    @Test
    @DisplayName("유료 결제 활성화 표시는 애플리케이션 시작 전에 거부한다")
    void 설정_유료결제활성화_시작전에거부한다() {
        GeminiProviderProperties properties = new GeminiProviderProperties();
        properties.setPaidBillingEnabled(true);

        assertThatThrownBy(properties::validateFixedContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("paid billing");
    }

    @Test
    @DisplayName("global endpoint가 아닌 외부 주소는 활성화 전에 거부한다")
    void 설정_외부Endpoint_활성화전에거부한다() {
        GeminiProviderProperties properties = new GeminiProviderProperties();
        properties.setEnabled(true);
        properties.setFreeTierVerified(true);
        properties.setApiKey("test-only-key");
        properties.setBaseUrl("https://collector.example/receive");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setResponseTimeout(Duration.ofSeconds(1));

        assertThatThrownBy(properties::validateFixedContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid endpoint");
    }

    @Test
    @DisplayName("loopback endpoint는 운영 설정에서 활성화 전에 거부한다")
    void 설정_LoopbackEndpoint_운영활성화전에거부한다() {
        GeminiProviderProperties properties = new GeminiProviderProperties();
        properties.setEnabled(true);
        properties.setFreeTierVerified(true);
        properties.setApiKey("test-only-key");
        properties.setBaseUrl("http://localhost:8080");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setResponseTimeout(Duration.ofSeconds(1));

        assertThatThrownBy(properties::validateFixedContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid endpoint");
    }

    @Test
    @DisplayName("운영 Gemini timeout은 계약값과 다르면 활성화 전에 거부한다")
    void 설정_계약외Timeout_활성화전에거부한다() {
        GeminiProviderProperties properties = new GeminiProviderProperties();
        properties.setEnabled(true);
        properties.setFreeTierVerified(true);
        properties.setApiKey("test-only-key");
        properties.setResponseTimeout(Duration.ofSeconds(91));

        assertThatThrownBy(properties::validateFixedContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("positive timeouts");
    }
}
