package com.masiton.security.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import com.masiton.security.infrastructure.configuration.VerificationAccessProperties;
import com.masiton.security.infrastructure.web.VerificationClientAddressResolver;
import com.masiton.security.application.VerificationSessionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("검증 세션 기능 가용성")
class VerificationFeatureAvailabilityTest {

    @Test
    @DisplayName("설정이 없으면 검증 세션을 활성화한다")
    void 설정없음_검증세션활성화() {
        assertThat(new VerificationAccessProperties().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("활성화된 기본 설정에서는 검증 세션 컨트롤러를 등록한다")
    void 기본설정_검증세션컨트롤러를등록한다() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(VerificationSessionController.class);
        });
    }

    @Test
    @DisplayName("검증 세션이 비활성화되면 HTTP 매핑을 등록하지 않는다")
    void 검증세션비활성화_컨트롤러를등록하지않는다() {
        runner()
                .withPropertyValues("masiton.security.verification.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(VerificationSessionController.class);
                });
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        VerificationAccessProperties.class,
                        VerificationSessionController.class,
                        ControllerDependencies.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControllerDependencies {
        @Bean
        VerificationSessionService verificationSessionService() {
            return mock(VerificationSessionService.class);
        }

        @Bean
        VerificationClientAddressResolver verificationClientAddressResolver() {
            return mock(VerificationClientAddressResolver.class);
        }
    }
}
