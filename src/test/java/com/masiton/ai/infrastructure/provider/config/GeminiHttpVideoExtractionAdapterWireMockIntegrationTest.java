package com.masiton.ai.infrastructure.provider.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.masiton.ai.application.port.out.dto.AiVideoExtractionRequest;
import com.masiton.ai.application.port.out.dto.AiVideoExtractionResult;

import tools.jackson.databind.ObjectMapper;

@Testcontainers
class GeminiHttpVideoExtractionAdapterWireMockIntegrationTest {

    private static final int WIREMOCK_PORT = 8080;
    private static final String API_KEY = "wiremock-test-key";

    @Container
    static final GenericContainer<?> WIREMOCK = new GenericContainer<>("wiremock/wiremock:3.13.2-alpine")
            .withExposedPorts(WIREMOCK_PORT)
            .waitingFor(Wait.forHttp("/__admin/health")
                    .forPort(WIREMOCK_PORT)
                    .forStatusCode(200));

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetMappings() throws Exception {
        admin("DELETE", "/__admin/mappings", "");
    }

    @Test
    @DisplayName("WireMock에서 Gemini 키 헤더와 직접 YouTube video input 계약을 검증한다")
    void 추출_WireMock_Gemini헤더와YouTube영상입력을전송한다() throws Exception {
        Map<String, Object> payload = Map.of(
                "responseId", "wiremock-request-id",
                "candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of(
                        "text", "{\"resultCompleteness\":\"COMPLETE\",\"candidates\":[],\"missingFields\":[],"
                                + "\"candidateTruncated\":false}"))))));
        String mapping = objectMapper.writeValueAsString(Map.of(
                "request", Map.of(
                        "method", "POST",
                        "urlPath", "/v1beta/models/gemini-3.5-flash-lite:generateContent",
                        "headers", Map.of(
                                "x-goog-api-key", Map.of("equalTo", API_KEY),
                                "Content-Type", Map.of("equalTo", "application/json"),
                                "Accept", Map.of("equalTo", "application/json")),
                        "bodyPatterns", List.of(Map.of("matchesJsonPath",
                                "$.contents[0].parts[?(@.fileData.fileUri == 'https://www.youtube.com/watch?v=video-id') ]"))),
                "response", Map.of(
                        "status", 200,
                        "headers", Map.of("Content-Type", "application/json"),
                        "jsonBody", payload)));
        admin("POST", "/__admin/mappings", mapping);

        AiVideoExtractionResult result = adapter().extract(
                new AiVideoExtractionRequest(URI.create("https://www.youtube.com/watch?v=video-id"), ""));

        assertThat(result.providerRequestId()).isEqualTo("wiremock-request-id");
        assertThat(result.candidates().path("resultCompleteness").asText()).isEqualTo("COMPLETE");
    }

    private GeminiHttpVideoExtractionAdapter adapter() {
        GeminiProviderProperties properties = new GeminiProviderProperties();
        properties.setEnabled(true);
        properties.setFreeTierVerified(true);
        properties.setApiKey(API_KEY);
        properties.setBaseUrl("http://%s:%d".formatted(WIREMOCK.getHost(), WIREMOCK.getMappedPort(WIREMOCK_PORT)));
        properties.setResponseTimeout(Duration.ofSeconds(2));
        return new GeminiHttpVideoExtractionAdapter(HttpClient.newHttpClient(), objectMapper, properties, true);
    }

    private void admin(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(
                        "http://%s:%d%s".formatted(WIREMOCK.getHost(), WIREMOCK.getMappedPort(WIREMOCK_PORT), path)))
                .timeout(Duration.ofSeconds(5));
        if ("DELETE".equals(method)) {
            request.DELETE();
        } else {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
    }
}
