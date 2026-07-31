package com.masiton.common.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

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

        // 파일이 없으면 속성 자체가 없다. 어댑터가 `@Value("${...:}")`로 빈 기본값을
        // 두므로 기동은 성립하고 등록 흐름에서만 검증 호출이 실패한다.
        runner(secrets).run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("masiton.integration.kakao.rest-api-key")).isNull();
            assertThat(environment.getProperty("masiton.integration.youtube.api-key")).isNull();
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
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "SECRETS_DIR=" + secrets.toAbsolutePath(),
                        "DB_URL=jdbc:postgresql://example.invalid:5432/masiton",
                        "DB_USERNAME=masiton",
                        "REDIS_HOST=127.0.0.1",
                        "REDIS_PORT=6379"
                );
    }

    private void writeSecrets(Path secrets) throws Exception {
        write(secrets, "spring.datasource.password", "db-secret-value");
        write(secrets, "spring.data.redis.password", "redis-secret-value");
        write(secrets, "masiton.security.jwt.key-id", "prod-1");
        write(secrets, "masiton.security.jwt.private-key-pem", PRIVATE_KEY_PEM);
        write(secrets, "masiton.security.jwt.public-key-pem", "-----BEGIN PUBLIC KEY-----\npub\n-----END PUBLIC KEY-----");
        write(secrets, "masiton.integration.kakao.rest-api-key", "kakao-key-value");
        write(secrets, "masiton.integration.youtube.api-key", "youtube-key-value");
    }

    private void write(Path directory, String name, String value) throws Exception {
        Files.writeString(directory.resolve(name), value, StandardCharsets.UTF_8);
    }
}
