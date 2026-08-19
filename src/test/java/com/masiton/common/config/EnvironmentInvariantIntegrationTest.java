package com.masiton.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공통 설정 계층(application.yml)의 운영 불변값이 프로파일 계층을 얹은 실제 기동 환경에서도
 * 유지되는지 검증한다. 파일 내용만 보는 {@link ConfigurationLayeringTest}와 달리 프로파일 병합
 * 결과를 Environment에서 확인한다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
@DisplayName("환경별 운영 불변값")
class EnvironmentInvariantIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.10-alpine")
                    .withDatabaseName("masiton")
                    .withUsername("masiton")
                    .withPassword("masiton_local");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("test 프로파일로 기동해도 open-in-view와 ddl-auto 운영 불변값이 유지된다")
    void 애플리케이션기동_test프로파일_운영불변값이유지된다() {
        // given & when
        boolean openInView = environment.getProperty("spring.jpa.open-in-view", Boolean.class, true);
        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto");

        // then
        assertThat(environment.getActiveProfiles()).containsExactly("test");
        assertThat(openInView).isFalse();
        assertThat(ddlAuto).isEqualTo("validate");
    }

    @Test
    @DisplayName("test 프로파일로 기동해도 Refresh 쿠키와 관리자 경로 운영 불변값이 유지된다")
    void 애플리케이션기동_test프로파일_인증쿠키불변값이유지된다() {
        // given & when
        boolean secure = environment.getProperty("masiton.security.secure", Boolean.class, false);
        String sameSite = environment.getProperty("masiton.security.same-site");
        String cookiePath = environment.getProperty("masiton.security.path");

        // then
        assertThat(secure).isTrue();
        assertThat(sameSite).isEqualTo("Strict");
        assertThat(cookiePath).isEqualTo("/api/auth/tokens");
    }
}
