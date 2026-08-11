package com.masiton.common.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

import com.masiton.ai.application.port.out.AiVideoExtractionProvider;
import com.masiton.ai.infrastructure.persistence.AesGcmTemporaryInputCipher;
import com.masiton.ai.infrastructure.provider.config.GeminiProviderConfiguration;
import com.masiton.ai.infrastructure.provider.config.GeminiProviderProperties;
import com.masiton.common.web.BusinessException;
import com.masiton.member.infrastructure.configuration.MemberActionMailConfiguration;
import com.masiton.member.infrastructure.configuration.MemberRateLimitConfiguration;
import com.masiton.restaurant.infrastructure.configuration.MapRateLimitConfiguration;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 운영 프로파일이 비밀값을 tmpfs 파일에서 읽는지 검증한다.
 *
 * 비밀값을 컨테이너 환경 변수로 넘기면 Docker가 그것을 컨테이너 스펙 파일에 평문으로
 * 적어 {@code docker inspect}로 읽히고 볼륨 스냅샷에 남는다(ADR-SEC-001 11절 평문 저장
 * 금지). 그래서 {@code configtree:}로 파일에서 읽게 바꿨고, 그 매핑이 조용히 깨지면
 * 기동 시점에야 드러나므로 여기서 고정한다.
 */
@DisplayName("운영 프로파일 비밀값 주입")
class ProdSecretsConfigTreeTest {

    private static final String PRIVATE_KEY_PEM = """
            -----BEGIN PRIVATE KEY-----
            first-line
            second-line
            -----END PRIVATE KEY-----""";

    @Test
    @DisplayName("configtree 파일 이름이 운영 속성으로 매핑된다")
    void 운영프로파일_비밀값파일을두면_속성으로매핑된다(@TempDir Path secrets) throws Exception {
        writeSecrets(secrets);

        runner(secrets).run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("db-secret-value");
            assertThat(environment.getProperty("spring.data.redis.password")).isEqualTo("redis-secret-value");
            assertThat(environment.getProperty("masiton.security.jwt.key-id")).isEqualTo("prod-1");
            assertThat(environment.getProperty("masiton.security.jwt.public-key-pem"))
                    .startsWith("-----BEGIN PUBLIC KEY-----");
            assertThat(environment.getProperty("masiton.member.action-mail.active-key-id")).isEqualTo("prod-mail-1");
            assertThat(environment.getProperty("masiton.member.action-mail.active-key")).isEqualTo("mail-cipher-key");
            assertThat(environment.getProperty("masiton.member.rate-limit.secret")).isEqualTo("member-rate-secret");
            assertThat(environment.getProperty("spring.mail.username")).isEqualTo("smtp-user");
            assertThat(environment.getProperty("spring.mail.password")).isEqualTo("smtp-password");
            assertThat(environment.getProperty("masiton.security.verification.login-id"))
                    .isEqualTo("verification-participant");
            assertThat(environment.getProperty("masiton.security.verification.password-hash"))
                    .isEqualTo("verification-bcrypt-hash");
            assertThat(environment.getProperty("masiton.ai.provider.gemini.api-key"))
                    .isEqualTo("test-gemini-api-key");
            assertThat(environment.getProperty("masiton.ai.temporary-input.active-key-id"))
                    .isEqualTo("test-temporary-input-key-1");
            assertThat(environment.getProperty("masiton.ai.temporary-input.active-key"))
                    .isEqualTo("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
            assertThat(environment.getProperty("masiton.ai.youtube-webhook.secret"))
                    .isEqualTo("test-youtube-webhook-secret");
            assertThat(environment.getProperty("masiton.security.verification.public-base-url"))
                    .isEqualTo("https://masiton.click");
            assertThat(environment.getProperty("masiton.security.verification.trusted-proxy-addresses"))
                    .isEqualTo("127.0.0.1");
            assertThat(environment.getProperty("masiton.security.verification.reverse-proxy-enabled"))
                    .isEqualTo("true");
        });
    }

    @Test
    @DisplayName("운영 필수 비밀값과 SMTP 설정으로 Properties와 MailSender가 기동한다")
    void 운영프로파일_필수설정주입_회원설정과SMTP기동성공(@TempDir Path secrets) throws Exception {
        writeSecrets(secrets);

        runner(secrets).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JavaMailSender.class);
        });
    }

    @Test
    @DisplayName("여러 줄 PEM이 이스케이프 없이 그대로 읽힌다")
    void 운영프로파일_여러줄PEM을두면_원문그대로읽힌다(@TempDir Path secrets) throws Exception {
        writeSecrets(secrets);

        runner(secrets).run(context -> assertThat(
                context.getEnvironment().getProperty("masiton.security.jwt.private-key-pem"))
                .isEqualTo(PRIVATE_KEY_PEM));
    }

    @Test
    @DisplayName("선택 값인 외부 API Key가 없으면 빈 값으로 기동한다")
    void 경계값_외부APIKey가없으면_빈값이된다(@TempDir Path secrets) throws Exception {
        writeSecrets(secrets);
        Files.deleteIfExists(secrets.resolve("masiton.integration.kakao.rest-api-key"));
        Files.deleteIfExists(secrets.resolve("masiton.integration.youtube.api-key"));
        Files.deleteIfExists(secrets.resolve("masiton.integration.kakao-mobility.rest-api-key"));
        Files.deleteIfExists(secrets.resolve("masiton.ai.provider.gemini.api-key"));
        Files.deleteIfExists(secrets.resolve("masiton.ai.temporary-input.active-key-id"));
        Files.deleteIfExists(secrets.resolve("masiton.ai.temporary-input.active-key"));
        Files.deleteIfExists(secrets.resolve("masiton.ai.youtube-webhook.secret"));

        // 파일이 없으면 속성 자체가 없다. 어댑터가 `@Value("${...:}")`로 빈 기본값을
        // 두므로 기동은 성립하고 등록 흐름에서만 검증 호출이 실패한다.
        runner(secrets).run(context -> {
            assertThat(context).hasNotFailed();
            GeminiProviderProperties geminiProperties = context.getBean(GeminiProviderProperties.class);
            assertThat(geminiProperties.isEnabled()).isFalse();
            assertThat(geminiProperties.getApiKey()).isBlank();
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("masiton.integration.kakao.rest-api-key")).isNull();
            assertThat(environment.getProperty("masiton.integration.youtube.api-key")).isNull();
            assertThat(environment.getProperty("masiton.integration.kakao-mobility.rest-api-key")).isBlank();
        });
    }

    @Test
    @DisplayName("외부 API Key 파일이 있으면 그 값을 쓴다")
    void 운영프로파일_외부APIKey파일을두면_그값을쓴다(@TempDir Path secrets) throws Exception {
        writeSecrets(secrets);

        runner(secrets).run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("masiton.integration.kakao.rest-api-key")).isEqualTo("kakao-key-value");
            assertThat(environment.getProperty("masiton.integration.youtube.api-key")).isEqualTo("youtube-key-value");
            assertThat(environment.getProperty("masiton.integration.kakao-mobility.rest-api-key"))
                    .isEqualTo("kakao-mobility-key-value");
        });
    }

    @Test
    @DisplayName("Gemini 선택 비밀값과 Free Tier 확인값이 있으면 제공자가 기동한다")
    void Gemini선택비밀값과FreeTier확인값이있으면_제공자가기동한다(@TempDir Path secrets) throws Exception {
        writeSecrets(secrets);

        runner(secrets)
                .withPropertyValues(
                        "GEMINI_ENABLED=true",
                        "GEMINI_FREE_TIER_VERIFIED=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AiVideoExtractionProvider.class);
                });
    }

    @Test
    @DisplayName("Gemini를 활성화했는데 선택 API 키가 없으면 기동을 차단한다")
    void Gemini활성화상태에서선택API키가없으면_기동을차단한다(@TempDir Path secrets) throws Exception {
        writeSecrets(secrets);
        Files.deleteIfExists(secrets.resolve("masiton.ai.provider.gemini.api-key"));

        runner(secrets)
                .withPropertyValues(
                        "GEMINI_ENABLED=true",
                        "GEMINI_FREE_TIER_VERIFIED=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isNotNull();
                });
    }

    @Test
    @DisplayName("임시 입력 활성 키가 없으면 암호화 요청을 fail-closed로 차단한다")
    void 임시입력활성키가없으면_암호화요청을차단한다(@TempDir Path secrets) throws Exception {
        writeSecrets(secrets);
        Files.deleteIfExists(secrets.resolve("masiton.ai.temporary-input.active-key"));

        runner(secrets).run(context -> {
            assertThat(context).hasNotFailed();
            assertThatThrownBy(() -> context.getBean(AesGcmTemporaryInputCipher.class).encrypt("보완 텍스트"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).code())
                    .isEqualTo("AIEXTRACT_TEMPORARY_INPUT_UNAVAILABLE");
        });
    }

    @Test
    @DisplayName("운영 불변값은 공통 계층에서 그대로 상속한다")
    void 운영프로파일_로드후_운영불변값이유지된다(@TempDir Path secrets) throws Exception {
        writeSecrets(secrets);

        runner(secrets).run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(environment.getProperty("masiton.security.secure")).isEqualTo("true");
        });
    }

    private ApplicationContextRunner runner(Path secrets) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(
                        AesGcmTemporaryInputCipher.class,
                        GeminiProviderConfiguration.class,
                        MemberActionMailConfiguration.class,
                        MemberRateLimitConfiguration.class,
                        MapRateLimitConfiguration.class,
                        ObjectMapperConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "SECRETS_DIR=" + secrets.toAbsolutePath(),
                        "DB_URL=jdbc:postgresql://example.invalid:5432/masiton",
                        "DB_USERNAME=masiton",
                        "REDIS_HOST=127.0.0.1",
                        "REDIS_PORT=6379",
                        "MAIL_HOST=smtp.example.invalid",
                        "MAIL_PORT=587",
                        "MEMBER_PUBLIC_BASE_URL=https://masiton.click",
                        "VERIFICATION_PUBLIC_BASE_URL=https://masiton.click",
                        "VERIFICATION_TRUSTED_PROXY_ADDRESSES=127.0.0.1",
                        "VERIFICATION_REVERSE_PROXY_ENABLED=true",
                        "MEMBER_TRUSTED_PROXY_ADDRESSES=127.0.0.1",
                        "MEMBER_REVERSE_PROXY_ENABLED=true",
                        "RESTAURANT_MAP_TRUSTED_PROXY_ADDRESSES=127.0.0.1",
                        "RESTAURANT_MAP_REVERSE_PROXY_ENABLED=true"
                );
    }

    private void writeSecrets(Path secrets) throws Exception {
        write(secrets, "spring.datasource.password", "db-secret-value");
        write(secrets, "spring.data.redis.password", "redis-secret-value");
        write(secrets, "masiton.security.jwt.key-id", "prod-1");
        write(secrets, "masiton.security.jwt.private-key-pem", PRIVATE_KEY_PEM);
        write(secrets, "masiton.security.jwt.public-key-pem", "-----BEGIN PUBLIC KEY-----\npub\n-----END PUBLIC KEY-----");
        write(secrets, "masiton.member.action-mail.active-key-id", "prod-mail-1");
        write(secrets, "masiton.member.action-mail.active-key", "mail-cipher-key");
        write(secrets, "masiton.member.rate-limit.secret", "member-rate-secret");
        write(secrets, "spring.mail.username", "smtp-user");
        write(secrets, "spring.mail.password", "smtp-password");
        write(secrets, "masiton.security.verification.login-id", "verification-participant");
        write(secrets, "masiton.security.verification.password-hash", "verification-bcrypt-hash");
        write(secrets, "masiton.integration.kakao.rest-api-key", "kakao-key-value");
        write(secrets, "masiton.integration.youtube.api-key", "youtube-key-value");
        write(secrets, "masiton.integration.kakao-mobility.rest-api-key", "kakao-mobility-key-value");
        write(secrets, "masiton.ai.provider.gemini.api-key", "test-gemini-api-key");
        write(secrets, "masiton.ai.temporary-input.active-key-id", "test-temporary-input-key-1");
        write(secrets, "masiton.ai.temporary-input.active-key", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        write(secrets, "masiton.ai.youtube-webhook.secret", "test-youtube-webhook-secret");
    }

    static class ObjectMapperConfiguration {
        @org.springframework.context.annotation.Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private void write(Path directory, String name, String value) throws Exception {
        Files.writeString(directory.resolve(name), value, StandardCharsets.UTF_8);
    }
}
