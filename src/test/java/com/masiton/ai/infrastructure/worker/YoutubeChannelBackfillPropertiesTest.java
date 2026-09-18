package com.masiton.ai.infrastructure.worker;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("YouTube 자동 보정 운영 주기 설정")
class YoutubeChannelBackfillPropertiesTest {
    @Test
    @DisplayName("활성화 시 주기가 빠지면 기동을 거부한다")
    void 검증_활성화와주기누락_거부한다() {
        // Given
        YoutubeChannelBackfillProperties properties = enabled();
        // When / Then
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("run interval");
    }

    @Test
    @DisplayName("양의 운영 주기와 키·quota를 설정하면 허용한다")
    void 검증_양의운영주기_허용한다() {
        // Given
        YoutubeChannelBackfillProperties properties = enabled();
        properties.setRunIntervalSeconds(3600);
        // When / Then
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비활성은 주기 미설정을 허용하되 음수는 거부한다")
    void 검증_비활성주기_미설정허용음수거부한다() {
        // Given
        YoutubeChannelBackfillProperties properties = new YoutubeChannelBackfillProperties();
        // When / Then
        assertThatCode(properties::validate).doesNotThrowAnyException();
        properties.setRunIntervalSeconds(-1);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    private YoutubeChannelBackfillProperties enabled() {
        YoutubeChannelBackfillProperties properties = new YoutubeChannelBackfillProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-only-key");
        properties.setProviderQuotaLimit(100);
        properties.setBackfillQuotaLimit(50);
        return properties;
    }
}
