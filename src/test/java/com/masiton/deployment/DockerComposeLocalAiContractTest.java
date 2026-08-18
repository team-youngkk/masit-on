package com.masiton.deployment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("로컬 Docker Compose AI 설정 계약")
class DockerComposeLocalAiContractTest {

    private static final Path COMPOSE_FILE = Path.of("docker-compose.yml");
    private static final Path ENV_EXAMPLE = Path.of(".env.example");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> AI_ENVIRONMENT_NAMES = List.of(
            "AI_WORKER_ENABLED",
            "AI_WORKER_QUOTA_WINDOW",
            "AI_WORKER_PROVIDER_QUOTA_LIMIT",
            "AI_WORKER_APPLICATION_QUOTA_LIMIT",
            "GEMINI_ENABLED",
            "GEMINI_FREE_TIER_VERIFIED",
            "GEMINI_PAID_BILLING_ENABLED",
            "GEMINI_API_KEY");

    @Test
    @DisplayName("AI Worker와 quota는 fail-closed 기본값으로 앱 컨테이너에 전달된다")
    void compose_AIWorker와Quota를_failClosed기본값으로전달한다() throws IOException {
        String compose = Files.readString(COMPOSE_FILE);

        assertThat(appEnvironment(compose))
                .contains("AI_WORKER_ENABLED: ${AI_WORKER_ENABLED:-false}")
                .contains("AI_WORKER_PROVIDER_QUOTA_LIMIT: ${AI_WORKER_PROVIDER_QUOTA_LIMIT:-0}")
                .contains("AI_WORKER_APPLICATION_QUOTA_LIMIT: ${AI_WORKER_APPLICATION_QUOTA_LIMIT:-0}");
    }

    @Test
    @DisplayName("Gemini 실행 게이트는 명시적 검증 없이는 비활성으로 전달된다")
    void compose_GeminiGate를_failClosed기본값으로전달한다() throws IOException {
        String compose = Files.readString(COMPOSE_FILE);

        assertThat(appEnvironment(compose))
                .contains("GEMINI_ENABLED: ${GEMINI_ENABLED:-false}")
                .contains("GEMINI_FREE_TIER_VERIFIED: ${GEMINI_FREE_TIER_VERIFIED:-false}")
                .contains("GEMINI_PAID_BILLING_ENABLED: ${GEMINI_PAID_BILLING_ENABLED:-false}")
                .contains("MASITON_AI_PROVIDER_GEMINI_API_KEY: ${GEMINI_API_KEY:-}");
    }

    @Test
    @DisplayName("Compose 예시에는 Gemini API 키의 실제 값이 포함되지 않는다")
    void compose_GeminiApiKey를_실제값없이참조한다() throws IOException {
        String compose = Files.readString(COMPOSE_FILE);
        String envExample = Files.readString(ENV_EXAMPLE);

        assertThat(appEnvironment(compose))
                .contains("MASITON_AI_PROVIDER_GEMINI_API_KEY: ${GEMINI_API_KEY:-}")
                .doesNotContain("\n      GEMINI_API_KEY:");

        assertThat(envExample)
                .as(".env.example은 키 이름만 제공하고 실제 자격 증명은 커밋하지 않아야 한다")
                .contains("\nGEMINI_API_KEY=\n");
    }

    @Test
    @DisplayName("Compose 기본 렌더링은 AI 호출을 fail-closed로 유지한다")
    void composeConfig_기본값_AI호출을비활성화한다() throws Exception {
        JsonNode environment = renderedAppEnvironment(Map.of());

        assertThat(environment.path("AI_WORKER_ENABLED").asText()).isEqualTo("false");
        assertThat(environment.path("AI_WORKER_QUOTA_WINDOW").asText()).isEqualTo("P1D");
        assertThat(environment.path("AI_WORKER_PROVIDER_QUOTA_LIMIT").asText()).isEqualTo("0");
        assertThat(environment.path("AI_WORKER_APPLICATION_QUOTA_LIMIT").asText()).isEqualTo("0");
        assertThat(environment.path("GEMINI_ENABLED").asText()).isEqualTo("false");
        assertThat(environment.path("GEMINI_FREE_TIER_VERIFIED").asText()).isEqualTo("false");
        assertThat(environment.path("GEMINI_PAID_BILLING_ENABLED").asText()).isEqualTo("false");
        assertThat(environment.path("MASITON_AI_PROVIDER_GEMINI_API_KEY").asText()).isEmpty();
    }

    @Test
    @DisplayName("Compose opt-in 렌더링은 Worker와 Gemini 설정을 Spring 환경으로 전달한다")
    void composeConfig_명시적OptIn_Spring환경으로전달한다() throws Exception {
        JsonNode environment = renderedAppEnvironment(Map.of(
                "AI_WORKER_ENABLED", "true",
                "AI_WORKER_QUOTA_WINDOW", "P1D",
                "AI_WORKER_PROVIDER_QUOTA_LIMIT", "20",
                "AI_WORKER_APPLICATION_QUOTA_LIMIT", "10",
                "GEMINI_ENABLED", "true",
                "GEMINI_FREE_TIER_VERIFIED", "true",
                "GEMINI_PAID_BILLING_ENABLED", "false",
                "GEMINI_API_KEY", "compose-test-key"));

        assertThat(environment.path("AI_WORKER_ENABLED").asText()).isEqualTo("true");
        assertThat(environment.path("AI_WORKER_QUOTA_WINDOW").asText()).isEqualTo("P1D");
        assertThat(environment.path("AI_WORKER_PROVIDER_QUOTA_LIMIT").asText()).isEqualTo("20");
        assertThat(environment.path("AI_WORKER_APPLICATION_QUOTA_LIMIT").asText()).isEqualTo("10");
        assertThat(environment.path("GEMINI_ENABLED").asText()).isEqualTo("true");
        assertThat(environment.path("GEMINI_FREE_TIER_VERIFIED").asText()).isEqualTo("true");
        assertThat(environment.path("GEMINI_PAID_BILLING_ENABLED").asText()).isEqualTo("false");
        assertThat(environment.path("MASITON_AI_PROVIDER_GEMINI_API_KEY").asText())
                .isEqualTo("compose-test-key");
    }

    private static String appEnvironment(String compose) {
        int appStart = compose.indexOf("  app:");
        assertThat(appStart).as("Compose app service").isGreaterThanOrEqualTo(0);

        int environmentStart = compose.indexOf("    environment:", appStart);
        int nextSection = compose.indexOf("    ports:", environmentStart);
        assertThat(environmentStart).as("Compose app environment").isGreaterThanOrEqualTo(0);
        assertThat(nextSection).as("Compose app environment boundary").isGreaterThan(environmentStart);
        return compose.substring(environmentStart, nextSection);
    }

    private static JsonNode renderedAppEnvironment(Map<String, String> overrides) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "compose", "--env-file", ".env.example", "config", "--format", "json");
        processBuilder.directory(Path.of("").toAbsolutePath().toFile());
        AI_ENVIRONMENT_NAMES.forEach(processBuilder.environment()::remove);
        processBuilder.environment().putAll(overrides);
        Path outputFile = Files.createTempFile("masiton-compose-config-", ".json");
        Path errorFile = Files.createTempFile("masiton-compose-config-", ".err");
        processBuilder.redirectOutput(outputFile.toFile());
        processBuilder.redirectError(errorFile.toFile());
        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            String error = Files.readString(errorFile, StandardCharsets.UTF_8);

            assertThat(finished).as("docker compose config timeout").isTrue();
            assertThat(process.exitValue()).as(error).isZero();
            return OBJECT_MAPPER.readTree(output).path("services").path("app").path("environment");
        } finally {
            Files.deleteIfExists(outputFile);
            Files.deleteIfExists(errorFile);
        }
    }
}
