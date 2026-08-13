package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import com.masiton.ai.application.port.in.AiExtractionJobUseCase;
import com.masiton.ai.application.port.in.YoutubeChannelWatchManagementUseCase;
import com.masiton.ai.application.port.out.YoutubeChannelWatchVerificationTokenPort;
import com.masiton.creator.application.port.in.FindCreatorReferenceUseCase;
import com.masiton.test.FullContextIntegrationTest;

import tools.jackson.databind.ObjectMapper;

@DisplayName("YouTube 채널 감시 활성화·challenge 경쟁 통합")
@SpringBootTest
class YoutubeChannelWatchActivationRaceIntegrationTest extends FullContextIntegrationTest {

    private static final int WIREMOCK_PORT = 8080;

    @Container
    static final GenericContainer<?> WIREMOCK = new GenericContainer<>("wiremock/wiremock:3.13.2-alpine")
            .withExposedPorts(WIREMOCK_PORT);

    @DynamicPropertySource
    static void wiremockProperties(DynamicPropertyRegistry registry) {
        registry.add("masiton.ai.youtube-webhook.subscription-hub-url",
                () -> baseUrl() + "/subscribe");
        registry.add("masiton.ai.youtube-webhook.callback-url",
                () -> "http://localhost:8080/api/webhooks/youtube/channel-updates");
    }

    @Autowired
    private YoutubeChannelWatchManagementUseCase management;

    @Autowired
    private AiExtractionJobUseCase extraction;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FindCreatorReferenceUseCase creatorReferences;

    @MockitoBean
    private YoutubeChannelWatchVerificationTokenPort verificationTokens;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Hub 202 응답 전 callback challenge가 도착해도 pending hash로 ACTIVE 전환한다")
    void 활성화_Hub응답전callbackChallenge_저장된pendingHash로ACTIVE전환한다() throws Exception {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        given(creatorReferences.findCreatorReference(creatorId)).willReturn(Optional.of(
                new FindCreatorReferenceUseCase.CreatorReference(creatorId, channelId, true, true)));
        given(verificationTokens.issue(channelId)).willReturn("verify-token");
        stubDelayedAcceptedSubscription();
        insertCreator(creatorId, channelId);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<YoutubeChannelWatchManagementUseCase.WatchStatus> activation =
                    executor.submit(() -> management.setEnabled(creatorId, true));

            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(
                    () -> assertThat(subscriptionRequestCount()).isEqualTo(1));

            assertThat(extraction.verifyChallenge(channelId, "verify-token", "challenge"))
                    .isEqualTo("challenge");
            activation.get(5, TimeUnit.SECONDS);
            assertThat(subscriptionStatus(channelId)).isEqualTo("ACTIVE");
        } finally {
            deleteFixture(creatorId, channelId);
        }
    }

    private void stubDelayedAcceptedSubscription() throws Exception {
        Map<String, Object> mapping = Map.of(
                "request", Map.of("method", "POST", "urlPath", "/subscribe"),
                "response", Map.of("status", 202, "fixedDelayMilliseconds", 1_000));
        admin("POST", "/__admin/mappings", objectMapper.writeValueAsString(mapping));
    }

    private int subscriptionRequestCount() throws Exception {
        return objectMapper.readTree(admin("GET", "/__admin/requests", "").body())
                .path("requests").size();
    }

    private HttpResponse<String> admin(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(5));
        if ("GET".equals(method)) {
            request.GET();
        } else {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
        return response;
    }

    private void insertCreator(UUID creatorId, String channelId) {
        jdbcTemplate.update("""
                INSERT INTO creator (
                    id, external_channel_id, channel_name, channel_url,
                    publication_status, lifecycle_status, external_availability_status,
                    external_status_checked_at
                ) VALUES (?, ?, ?, ?, 'PUBLIC', 'ACTIVE', 'AVAILABLE', CURRENT_TIMESTAMP)
                """, creatorId, channelId, "fixture-" + creatorId,
                "https://example.com/channel/" + creatorId);
    }

    private String subscriptionStatus(String channelId) {
        return jdbcTemplate.queryForObject(
                "SELECT subscription_status FROM youtube_channel_watch WHERE youtube_channel_id = ?",
                String.class, channelId);
    }

    private void deleteFixture(UUID creatorId, String channelId) {
        jdbcTemplate.update("DELETE FROM youtube_channel_watch WHERE creator_id = ? OR youtube_channel_id = ?",
                creatorId, channelId);
        jdbcTemplate.update("DELETE FROM creator WHERE id = ?", creatorId);
    }

    private static String baseUrl() {
        return "http://%s:%d".formatted(WIREMOCK.getHost(), WIREMOCK.getMappedPort(WIREMOCK_PORT));
    }
}
