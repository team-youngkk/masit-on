package com.masiton.orchestration.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("AI 장소명 완화 매칭 설정")
class PlaceIdentityMatchingPropertiesTest {

    @Test
    @DisplayName("환경 변수가 없을 때 완화 매칭을 기본 활성화한다")
    void 기본값_완화매칭을활성화한다() {
        PlaceIdentityMatchingProperties properties = new PlaceIdentityMatchingProperties();

        assertThat(properties.relaxedMatchingEnabled()).isTrue();
    }

    @Test
    @DisplayName("운영 긴급 차단을 위해 false 설정을 받을 수 있다")
    void 설정값_false_완화매칭을비활성화한다() {
        PlaceIdentityMatchingProperties properties = new PlaceIdentityMatchingProperties();

        properties.setRelaxedMatchingEnabled(false);

        assertThat(properties.relaxedMatchingEnabled()).isFalse();
    }

    @Test
    @DisplayName("Spring 환경 변수 바인딩으로 false를 주입하면 정책도 비활성화된다")
    void 환경변수_바인딩값_false_정책을비활성화한다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("masiton.ai.place-identity.relaxed-matching-enabled", "false");

        PlaceIdentityMatchingProperties properties = Binder.get(environment)
                .bind("masiton.ai.place-identity", Bindable.of(PlaceIdentityMatchingProperties.class))
                .get();

        assertThat(properties.relaxedMatchingEnabled()).isFalse();
    }

    @Test
    @DisplayName("공통 설정의 환경 변수 기본값도 true다")
    void 공통설정_환경변수기본값_true를사용한다() throws Exception {
        String applicationYaml = Files.readString(
                Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);

        assertThat(applicationYaml)
                .contains("relaxed-matching-enabled: ${AI_PLACE_IDENTITY_RELAXED_MATCHING_ENABLED:true}");
    }
}
