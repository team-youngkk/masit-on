package com.masiton.ai.infrastructure.provider.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
