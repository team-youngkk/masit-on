package com.masiton.member.infrastructure.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("회원 인증 메일 설정")
class MemberActionMailPropertiesTest {

    @Test
    @DisplayName("운영 프로필은 HTTP 비밀번호 재설정 URL을 거부한다")
    void 운영프로필_HTTP재설정URL_거부한다() {
        MemberActionMailProperties properties = properties(
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                "http://localhost:3000/password-reset");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A valid member password-reset URL is required");
    }

    @Test
    @DisplayName("운영 프로필은 HTTPS 비밀번호 재설정 URL을 허용한다")
    void 운영프로필_HTTPS재설정URL_허용한다() {
        MemberActionMailProperties properties = properties(
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                "https://masiton.click/password-reset");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비운영 프로필은 loopback HTTP 비밀번호 재설정 URL을 허용한다")
    void 비운영프로필_loopbackHTTP재설정URL_허용한다() {
        MemberActionMailProperties properties = properties(
                new MockEnvironment().withProperty("spring.profiles.active", "test"),
                "http://localhost:3000/password-reset");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    private MemberActionMailProperties properties(MockEnvironment environment, String passwordResetUrl) {
        MemberActionMailProperties properties = new MemberActionMailProperties(environment);
        properties.setFromAddress("no-reply@test.masiton.invalid");
        properties.setActiveKeyId("test-1");
        properties.setActiveKey("test-key");
        properties.setPasswordResetUrl(passwordResetUrl);
        return properties;
    }
}
