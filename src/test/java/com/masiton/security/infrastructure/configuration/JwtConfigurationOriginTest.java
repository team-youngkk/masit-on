package com.masiton.security.infrastructure.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("통합 인증 Origin 설정")
class JwtConfigurationOriginTest {

    private final JwtConfiguration configuration = new JwtConfiguration();

    @Test
    @DisplayName("운영 프로파일은 loopback HTTP Origin도 기동 시 거부한다")
    void prod_loopbackHttpOrigin_기동실패() {
        SecurityProperties properties = properties("http://localhost:3000");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> configuration.memberCookieSettings(properties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    @DisplayName("로컬 프로파일은 명시한 loopback HTTP Origin을 허용한다")
    void local_loopbackHttpOrigin_허용() {
        SecurityProperties properties = properties("http://localhost:3000");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThat(configuration.memberCookieSettings(properties, environment).allowedOrigins())
                .containsExactly("http://localhost:3000");
    }

    private SecurityProperties properties(String origins) {
        SecurityProperties properties = new SecurityProperties();
        properties.getMember().setPublicBaseUrl(origins);
        return properties;
    }
}
