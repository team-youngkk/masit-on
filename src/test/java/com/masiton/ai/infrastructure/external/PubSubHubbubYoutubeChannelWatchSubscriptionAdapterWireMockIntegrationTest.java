package com.masiton.ai.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.masiton.ai.presentation.webhook.YoutubeWebhookProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@DisplayName("YouTube Hub 구독 Adapter WireMock 계약")
class PubSubHubbubYoutubeChannelWatchSubscriptionAdapterWireMockIntegrationTest {

    private static final int WIREMOCK_PORT = 8080;

    @Container
    static final GenericContainer<?> WIREMOCK = new GenericContainer<>("wiremock/wiremock:3.13.2-alpine")
            .withExposedPorts(WIREMOCK_PORT);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetWireMock() throws Exception {
        admin("DELETE", "/__admin/mappings", "");
        admin("DELETE", "/__admin/requests", "");
    }

    @Test
    @DisplayName("활성화 Token을 동일한 hub verify_token으로 전달한다")
    void 구독요청_활성화Token_동일한verifyToken으로전달한다() throws Exception {
        stubSubscription(202);
        YoutubeWebhookProperties properties = properties();
        PubSubHubbubYoutubeChannelWatchSubscriptionAdapter adapter = new PubSubHubbubYoutubeChannelWatchSubscriptionAdapter(
                HttpClient.newHttpClient(), properties);

        adapter.subscribe("UCchannel123", "verify-token");

        JsonNode request = subscriptionRequest();
        assertThat(request.path("method").asText()).isEqualTo("POST");
        Map<String, String> form = formParams(request.path("body").asText());
        assertThat(form).containsEntry("hub.mode", "subscribe")
                .containsEntry("hub.topic", "https://www.youtube.com/xml/feeds/videos.xml?channel_id=UCchannel123")
                .containsEntry("hub.callback", properties.getCallbackUrl())
                .containsEntry("hub.verify", "async")
                .containsEntry("hub.verify_token", "verify-token")
                .containsEntry("hub.secret", properties.getSecret());
    }

    @Test
    @DisplayName("Hub 4xx 응답은 정규화된 실패 범주로 변환한다")
    void 구독요청_Hub4xx_정규화된실패범주로변환한다() throws Exception {
        stubSubscription(400);
        assertThatThrownBy(() -> adapter().subscribe("UCchannel123", "verify-token"))
                .isInstanceOfSatisfying(com.masiton.ai.application.YoutubeChannelWatchSubscriptionFailedException.class,
                        exception -> assertThat(exception.category()).isEqualTo("SUBSCRIPTION_4XX"));
    }

    @Test
    @DisplayName("Hub 5xx 응답은 정규화된 실패 범주로 변환한다")
    void 구독요청_Hub5xx_정규화된실패범주로변환한다() throws Exception {
        stubSubscription(503);
        assertThatThrownBy(() -> adapter().subscribe("UCchannel123", "verify-token"))
                .isInstanceOfSatisfying(com.masiton.ai.application.YoutubeChannelWatchSubscriptionFailedException.class,
                        exception -> assertThat(exception.category()).isEqualTo("SUBSCRIPTION_5XX"));
    }

    @Test
    @DisplayName("Hub 응답 timeout은 정규화된 timeout 범주로 변환한다")
    void 구독요청_Hub응답timeout_정규화된timeout범주로변환한다() throws Exception {
        stubSubscriptionWithDelay(202, 250);
        assertThatThrownBy(() -> adapter(new YoutubeWebhookProperties(), Duration.ofMillis(50))
                .subscribe("UCchannel123", "verify-token"))
                .isInstanceOfSatisfying(com.masiton.ai.application.YoutubeChannelWatchSubscriptionFailedException.class,
                        exception -> assertThat(exception.category()).isEqualTo("SUBSCRIPTION_TIMEOUT"));
    }

    private PubSubHubbubYoutubeChannelWatchSubscriptionAdapter adapter() {
        return new PubSubHubbubYoutubeChannelWatchSubscriptionAdapter(HttpClient.newHttpClient(), properties());
    }

    private PubSubHubbubYoutubeChannelWatchSubscriptionAdapter adapter(YoutubeWebhookProperties properties,
                                                                       Duration timeout) {
        properties.setSubscriptionHubUrl(baseUrl() + "/subscribe");
        return new PubSubHubbubYoutubeChannelWatchSubscriptionAdapter(HttpClient.newHttpClient(), properties, timeout);
    }

    private YoutubeWebhookProperties properties() {
        YoutubeWebhookProperties properties = new YoutubeWebhookProperties();
        properties.setSubscriptionHubUrl(baseUrl() + "/subscribe");
        properties.setCallbackUrl("https://masiton.example/api/webhooks/youtube/channel-updates");
        properties.setSecret("wiremock-hub-secret");
        return properties;
    }

    private void stubSubscription(int status) throws Exception {
        Map<String, Object> mapping = Map.of(
                "request", Map.of("method", "POST", "urlPath", "/subscribe"),
                "response", Map.of("status", status));
        admin("POST", "/__admin/mappings", objectMapper.writeValueAsString(mapping));
    }

    private void stubSubscriptionWithDelay(int status, int delayMillis) throws Exception {
        Map<String, Object> mapping = Map.of(
                "request", Map.of("method", "POST", "urlPath", "/subscribe"),
                "response", Map.of("status", status, "fixedDelayMilliseconds", delayMillis));
        admin("POST", "/__admin/mappings", objectMapper.writeValueAsString(mapping));
    }

    private JsonNode subscriptionRequest() throws Exception {
        JsonNode requests = objectMapper.readTree(admin("GET", "/__admin/requests", "").body()).path("requests");
        assertThat(requests).hasSize(1);
        return requests.get(0).path("request");
    }

    private Map<String, String> formParams(String body) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            String[] keyAndValue = pair.split("=", 2);
            params.put(URLDecoder.decode(keyAndValue[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(keyAndValue[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    private HttpResponse<String> admin(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(adminUri(path)).timeout(Duration.ofSeconds(5));
        switch (method) {
            case "DELETE" -> request.DELETE();
            case "GET" -> request.GET();
            case "POST" -> request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
        return response;
    }

    private String baseUrl() {
        return "http://%s:%d".formatted(WIREMOCK.getHost(), WIREMOCK.getMappedPort(WIREMOCK_PORT));
    }

    private URI adminUri(String path) {
        return URI.create(baseUrl() + path);
    }
}
