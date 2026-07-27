package com.masiton.common.observability;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 외부 연동 대역이 Compose와 같은 이미지 태그로 기동·응답하는지만 확인한다.
 * Kakao·YouTube Stub 정의는 T-04가 소유한다.
 */
@Testcontainers
@DisplayName("WireMock 로컬 연결")
class WireMockConnectivityIntegrationTest {

    private static final int WIREMOCK_PORT = 8080;

    @Container
    static final GenericContainer<?> WIREMOCK =
            new GenericContainer<>("wiremock/wiremock:3.13.2-alpine")
                    .withExposedPorts(WIREMOCK_PORT)
                    .waitingFor(Wait.forHttp("/__admin/health").forPort(WIREMOCK_PORT).forStatusCode(200));

    @Test
    @DisplayName("관리 상태 확인 응답이 정상이다")
    void 관리엔드포인트조회_컨테이너기동_200을반환한다() throws Exception {
        URI adminHealth = URI.create(
                "http://%s:%d/__admin/health".formatted(WIREMOCK.getHost(), WIREMOCK.getMappedPort(WIREMOCK_PORT))
        );

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build()) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(adminHealth).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("healthy");
        }
    }
}
