package com.masiton.restaurant.infrastructure.external;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.masiton.restaurant.application.PlaceSearchFailedException;
import com.masiton.restaurant.application.port.out.PlaceSearchCandidate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kakao Local Keyword API 상호명 검색 결과에서 등록에 쓸 수 없는 문서를 조용히 제외하는지,
 * 실패 응답을 예외로 알리는지를 WireMock으로 검증한다. 실제 Kakao API는 호출하지 않는다.
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class KakaoPlaceSearchAdapterWireMockIntegrationTest {

    private static final int WIREMOCK_PORT = 8080;
    private static final String API_KEY = "wiremock-test-key";

    @Container
    static final GenericContainer<?> WIREMOCK = new GenericContainer<>("wiremock/wiremock:3.13.2-alpine")
            .withExposedPorts(WIREMOCK_PORT);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetMappingsAndJournal() throws Exception {
        admin("DELETE", "/__admin/mappings", "");
        admin("DELETE", "/__admin/requests", "");
    }

    @Test
    @DisplayName("장소·전화번호가 모두 있는 문서는 https·전체 시도명으로 정규화해 반환한다")
    void 검색_정상문서_정규화해반환한다() throws Exception {
        stubKeywordSearch(200, Map.of("documents", List.of(Map.of(
                "place_name", "아코",
                "place_url", "http://place.map.kakao.com/example",
                "road_address_name", "서울 강동구 성내동 12-38",
                "phone", "02-000-0000"))));

        List<PlaceSearchCandidate> results = adapter().search("아코");

        assertThat(results).hasSize(1);
        PlaceSearchCandidate candidate = results.get(0);
        assertThat(candidate.placeName()).isEqualTo("아코");
        assertThat(candidate.kakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/example");
        assertThat(candidate.roadAddress()).isEqualTo("서울특별시 강동구 성내동 12-38");
        assertThat(candidate.phoneNumber()).isEqualTo("02-000-0000");
    }

    @Test
    @DisplayName("category_name이 있는 문서는 placeCategory로 그대로 반환한다")
    void 검색_카테고리있음_placeCategory로반환한다() throws Exception {
        stubKeywordSearch(200, Map.of("documents", List.of(Map.of(
                "place_name", "아코",
                "place_url", "https://place.map.kakao.com/example",
                "road_address_name", "서울특별시 강동구 성내동 12-38",
                "phone", "02-000-0000",
                "category_name", "음식점 > 한식 > 냉면"))));

        List<PlaceSearchCandidate> results = adapter().search("아코");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).placeCategory()).isEqualTo("음식점 > 한식 > 냉면");
    }

    @Test
    @DisplayName("category_name이 없는 문서는 placeCategory를 null로 채워 반환한다")
    void 검색_카테고리없음_placeCategory를null로반환한다() throws Exception {
        stubKeywordSearch(200, Map.of("documents", List.of(Map.of(
                "place_name", "아코",
                "place_url", "https://place.map.kakao.com/example",
                "road_address_name", "서울특별시 강동구 성내동 12-38",
                "phone", "02-000-0000"))));

        List<PlaceSearchCandidate> results = adapter().search("아코");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).placeCategory()).isNull();
    }

    @Test
    @DisplayName("전화번호가 없는 문서는 phoneNumber를 null로 채워 반환한다")
    void 검색_전화번호없음_phoneNumber를null로반환한다() throws Exception {
        stubKeywordSearch(200, Map.of("documents", List.of(Map.of(
                "place_name", "아코",
                "place_url", "https://place.map.kakao.com/example",
                "road_address_name", "서울특별시 강동구 성내동 12-38"))));

        List<PlaceSearchCandidate> results = adapter().search("아코");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).phoneNumber()).isNull();
    }

    @Test
    @DisplayName("도로명주소가 없는 문서는 결과에서 제외한다")
    void 검색_도로명주소없음_결과에서제외한다() throws Exception {
        stubKeywordSearch(200, Map.of("documents", List.of(Map.of(
                "place_name", "아코",
                "place_url", "https://place.map.kakao.com/example",
                "phone", "02-000-0000"))));

        List<PlaceSearchCandidate> results = adapter().search("아코");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("장소 링크가 없는 문서는 결과에서 제외한다")
    void 검색_장소링크없음_결과에서제외한다() throws Exception {
        stubKeywordSearch(200, Map.of("documents", List.of(Map.of(
                "place_name", "아코",
                "road_address_name", "서울특별시 강동구 성내동 12-38",
                "phone", "02-000-0000"))));

        List<PlaceSearchCandidate> results = adapter().search("아코");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("path가 없는 카카오 장소 링크는 결과에서 제외한다")
    void 검색_장소링크path없음_결과에서제외한다() throws Exception {
        stubKeywordSearch(200, Map.of("documents", List.of(Map.of(
                "place_name", "아코",
                "place_url", "https://place.map.kakao.com",
                "road_address_name", "서울특별시 강동구 성내동 12-38",
                "phone", "02-000-0000"))));

        List<PlaceSearchCandidate> results = adapter().search("아코");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("카카오 장소 host가 아닌 링크는 결과에서 제외한다")
    void 검색_다른host링크_결과에서제외한다() throws Exception {
        stubKeywordSearch(200, Map.of("documents", List.of(Map.of(
                "place_name", "아코",
                "place_url", "https://place.example.com/example",
                "road_address_name", "서울특별시 강동구 성내동 12-38",
                "phone", "02-000-0000"))));

        List<PlaceSearchCandidate> results = adapter().search("아코");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("응답 문서가 모두 계약 위반이면 원문 없이 제외 사유별 경고를 기록한다")
    void 검색_모든문서계약위반_제외사유별경고를기록한다(CapturedOutput output) throws Exception {
        stubKeywordSearch(200, Map.of("documents", List.of(
                Map.of(
                        "place_name", "주소 없음",
                        "place_url", "https://place.map.kakao.com/1"),
                Map.of(
                        "place_name", "잘못된 URL",
                        "place_url", "https://place.example.com/2",
                        "road_address_name", "서울특별시 강동구 성내동 12-38"))));

        List<PlaceSearchCandidate> results = adapter().search("아코");

        assertThat(results).isEmpty();
        assertThat(output).contains(
                "kakao place search response excluded all documents: total=2, missingRequired=1, "
                        + "invalidPlaceUrl=1, invalidDocument=0");
        assertThat(output).doesNotContain("주소 없음", "잘못된 URL", "place.example.com");
    }

    @Test
    @DisplayName("요청 URL과 Authorization 헤더가 Kakao 계약을 따른다")
    void 검색_요청규격_Kakao계약을따른다() throws Exception {
        stubKeywordSearch(200, Map.of("documents", List.of()));

        adapter().search("아코 맛집");

        List<JsonNode> requests = keywordSearchRequests();
        assertThat(requests).hasSize(1);
        JsonNode request = requests.get(0);
        assertThat(request.path("url").asText()).startsWith("/v2/local/search/keyword.json?query=");
        assertThat(URLDecoder.decode(
                request.path("url").asText().substring("/v2/local/search/keyword.json?query=".length()),
                StandardCharsets.UTF_8)).isEqualTo("아코 맛집");
        assertThat(request.path("headers").path("Authorization").asText()).isEqualTo("KakaoAK " + API_KEY);
    }

    @Test
    @DisplayName("500 응답이면 검색 실패 예외를 던진다")
    void 검색_500응답_검색실패예외를던진다() throws Exception {
        stubKeywordSearch(500, Map.of("error", "server error"));

        assertThatThrownBy(() -> adapter().search("아코"))
                .isInstanceOf(PlaceSearchFailedException.class);
    }

    @Test
    @DisplayName("404 응답도 제공자 실패로 보고 검색 실패 예외를 던진다")
    void 검색_404응답_검색실패예외를던진다() throws Exception {
        stubKeywordSearch(404, Map.of("error", "not found"));

        assertThatThrownBy(() -> adapter().search("없는가게"))
                .isInstanceOf(PlaceSearchFailedException.class);
    }

    private KakaoPlaceSearchAdapter adapter() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        return new KakaoPlaceSearchAdapter(httpClient, objectMapper, baseUrl(), API_KEY);
    }

    private String baseUrl() {
        return "http://%s:%d".formatted(WIREMOCK.getHost(), WIREMOCK.getMappedPort(WIREMOCK_PORT));
    }

    private void stubKeywordSearch(int status, Object jsonBody) throws Exception {
        Map<String, Object> mapping = Map.of(
                "request", Map.of("method", "GET", "urlPathPattern", "/v2/local/search/keyword\\.json"),
                "response", Map.of(
                        "status", status,
                        "jsonBody", jsonBody,
                        "headers", Map.of("Content-Type", "application/json")));
        admin("POST", "/__admin/mappings", objectMapper.writeValueAsString(mapping));
    }

    private List<JsonNode> keywordSearchRequests() throws Exception {
        List<JsonNode> result = new ArrayList<>();
        HttpResponse<String> response = admin("GET", "/__admin/requests", "");
        for (JsonNode entry : objectMapper.readTree(response.body()).path("requests")) {
            JsonNode request = entry.path("request");
            if (request.path("url").asText().startsWith("/v2/local/search/keyword.json")) {
                result.add(request);
            }
        }
        return result;
    }

    private HttpResponse<String> admin(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(adminUri(path)).timeout(Duration.ofSeconds(5));
        switch (method) {
            case "DELETE" -> request.DELETE();
            case "GET" -> request.GET();
            case "POST" -> request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            default -> throw new IllegalArgumentException("Unsupported admin method: " + method);
        }
        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
        return response;
    }

    private URI adminUri(String path) {
        return URI.create("http://%s:%d%s".formatted(WIREMOCK.getHost(), WIREMOCK.getMappedPort(WIREMOCK_PORT), path));
    }
}
