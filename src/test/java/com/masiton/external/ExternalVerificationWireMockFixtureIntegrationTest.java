package com.masiton.external;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DisplayName("외부 기준정보 WireMock fixture")
class ExternalVerificationWireMockFixtureIntegrationTest {

    private static final int WIREMOCK_PORT = 8080;
    private static final String WIREMOCK_IMAGE = System.getenv()
            .getOrDefault("WIREMOCK_IMAGE", "wiremock/wiremock:3.13.2-alpine");

    @Container
    static final GenericContainer<?> WIREMOCK = new GenericContainer<>(WIREMOCK_IMAGE)
            .withCopyFileToContainer(
                    MountableFile.forHostPath("docker/wiremock/mappings"),
                    "/home/wiremock/mappings"
            )
            .withCopyFileToContainer(
                    MountableFile.forHostPath("docker/wiremock/__files"),
                    "/home/wiremock/__files"
            )
            .withExposedPorts(WIREMOCK_PORT)
            .waitingFor(Wait.forHttp("/__admin/health").forPort(WIREMOCK_PORT).forStatusCode(200));

    @Test
    @DisplayName("정상 fixture는 카카오 장소와 YouTube 채널 및 영상을 반환한다")
    void 정상응답_각외부API_계약필드를반환한다() throws Exception {
        HttpResponse<String> kakao = get("/v2/local/search/keyword.json?query=fixture-place-normal", Duration.ofSeconds(2));
        HttpResponse<String> channel = get("/youtube/v3/channels?id=UCfixtureNormalChannel01", Duration.ofSeconds(2));
        HttpResponse<String> video = get("/youtube/v3/videos?id=fixtureVid1", Duration.ofSeconds(2));

        assertThat(kakao.statusCode()).isEqualTo(200);
        assertThat(kakao.body()).contains("fixture-place-normal", "road_address_name", "place_url");
        assertThat(channel.statusCode()).isEqualTo(200);
        assertThat(channel.body()).contains("UCfixtureNormalChannel01", "맛잇온 테스트 채널");
        assertThat(video.statusCode()).isEqualTo(200);
        assertThat(video.body()).contains("fixtureVid1", "channelId", "thumbnails");
    }

    @Test
    @DisplayName("없는 외부 자원 fixture는 빈 목록을 반환한다")
    void 없는자원_각외부API_빈목록을반환한다() throws Exception {
        HttpResponse<String> kakao = get("/v2/local/search/keyword.json?query=fixture-place-missing", Duration.ofSeconds(2));
        HttpResponse<String> channel = get("/youtube/v3/channels?id=UCfixtureMissingChannel1", Duration.ofSeconds(2));
        HttpResponse<String> video = get("/youtube/v3/videos?id=fixtureMis", Duration.ofSeconds(2));

        assertThat(kakao.statusCode()).isEqualTo(200);
        assertThat(kakao.body()).contains("\"documents\": []");
        assertThat(channel.statusCode()).isEqualTo(200);
        assertThat(channel.body()).contains("\"items\": []");
        assertThat(video.statusCode()).isEqualTo(200);
        assertThat(video.body()).contains("\"items\": []");
    }

    @Test
    @DisplayName("요청 제한 fixture는 Retry-After와 429를 반환한다")
    void 요청제한_각외부API_429를반환한다() throws Exception {
        HttpResponse<String> kakao = get("/v2/local/search/keyword.json?query=fixture-place-rate-limited", Duration.ofSeconds(2));
        HttpResponse<String> channel = get("/youtube/v3/channels?id=UCfixtureRateLimited01", Duration.ofSeconds(2));
        HttpResponse<String> video = get("/youtube/v3/videos?id=fixtureRat", Duration.ofSeconds(2));

        assertThat(kakao.statusCode()).isEqualTo(429);
        assertThat(kakao.headers().firstValue("Retry-After")).contains("60");
        assertThat(channel.statusCode()).isEqualTo(429);
        assertThat(channel.body()).contains("quotaExceeded");
        assertThat(video.statusCode()).isEqualTo(429);
        assertThat(video.headers().firstValue("Retry-After")).contains("60");
    }

    @Test
    @DisplayName("잘못된 JSON fixture는 200 본문으로 그대로 전달된다")
    void 잘못된JSON_각외부API_역직렬화실패를재현한다() throws Exception {
        HttpResponse<String> kakao = get("/v2/local/search/keyword.json?query=fixture-place-malformed", Duration.ofSeconds(2));
        HttpResponse<String> channel = get("/youtube/v3/channels?id=UCfixtureMalformedCh1", Duration.ofSeconds(2));
        HttpResponse<String> video = get("/youtube/v3/videos?id=fixtureMal", Duration.ofSeconds(2));

        assertThat(kakao.statusCode()).isEqualTo(200);
        assertThat(kakao.body()).isEqualTo("{\n  \"documents\": [\n");
        assertThat(channel.statusCode()).isEqualTo(200);
        assertThat(channel.body()).isEqualTo("{\n  \"items\": [\n");
        assertThat(video.statusCode()).isEqualTo(200);
        assertThat(video.body()).isEqualTo("{\n  \"items\": [\n");
    }

    @Test
    @DisplayName("필수 외부 필드가 누락된 fixture는 계약 오류를 재현한다")
    void 계약오류_각외부API_필수필드누락을반환한다() throws Exception {
        HttpResponse<String> kakao = get("/v2/local/search/keyword.json?query=fixture-place-contract-error", Duration.ofSeconds(2));
        HttpResponse<String> channel = get("/youtube/v3/channels?id=UCfixtureContractError1", Duration.ofSeconds(2));
        HttpResponse<String> video = get("/youtube/v3/videos?id=fixtureCon", Duration.ofSeconds(2));

        assertThat(kakao.statusCode()).isEqualTo(200);
        assertThat(kakao.body()).doesNotContain("road_address_name");
        assertThat(channel.statusCode()).isEqualTo(200);
        assertThat(channel.body()).doesNotContain("snippet");
        assertThat(video.statusCode()).isEqualTo(200);
        assertThat(video.body()).doesNotContain("thumbnails");
    }

    @Test
    @DisplayName("응답 지연 fixture는 adapter timeout보다 길게 대기한다")
    void 응답지연_각외부API_Timeout을재현한다() {
        assertThatThrownBy(() -> get("/v2/local/search/keyword.json?query=fixture-place-timeout", Duration.ofSeconds(1)))
                .isInstanceOf(HttpTimeoutException.class);
        assertThatThrownBy(() -> get("/youtube/v3/channels?id=UCfixtureTimeoutChannel1", Duration.ofSeconds(1)))
                .isInstanceOf(HttpTimeoutException.class);
        assertThatThrownBy(() -> get("/youtube/v3/videos?id=fixtureTmo", Duration.ofSeconds(1)))
                .isInstanceOf(HttpTimeoutException.class);
    }

    private static HttpResponse<String> get(String path, Duration timeout) throws Exception {
        URI uri = URI.create("http://%s:%d%s".formatted(WIREMOCK.getHost(), WIREMOCK.getMappedPort(WIREMOCK_PORT), path));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .GET()
                .build();

        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }
}
