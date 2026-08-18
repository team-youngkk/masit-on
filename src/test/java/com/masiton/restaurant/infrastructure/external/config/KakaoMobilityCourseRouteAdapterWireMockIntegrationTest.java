package com.masiton.restaurant.infrastructure.external.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.masiton.restaurant.application.port.out.CourseRouteFailureCategory;
import com.masiton.restaurant.application.port.out.CourseRouteLeg;
import com.masiton.restaurant.application.port.out.CourseRouteProviderException;
import com.masiton.restaurant.application.port.out.CourseRouteRequest;
import com.masiton.restaurant.application.port.out.CourseRouteResult;
import com.masiton.restaurant.application.port.out.CourseRouteVertex;
import com.masiton.restaurant.application.port.out.CourseRouteWaypoint;
import com.masiton.restaurant.domain.course.CourseRouteShapeStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ADR-ROUTE-001 5.2절·9절과 API-DISCOVERY-COURSE-001 6·7절이 정한 Kakao Mobility {@code /v1/directions}
 * 요청 규격·재시도 0회·quota hard stop·실패 범주 매핑을 WireMock으로 검증하는 계약 테스트다(TST-E3-COURSE-002).
 */
@Testcontainers
class KakaoMobilityCourseRouteAdapterWireMockIntegrationTest {

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
    @DisplayName("2개 stop 정상 응답에서 leg 1건에 거리·시간을 그대로 매핑한다")
    void 코스경로계산_2개stop정상응답_leg1건을매핑한다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of("distance", 4200, "duration", 780))))));

        CourseRouteResult result = defaultAdapter().calculate(twoStopRequest());

        assertThat(result.legs()).hasSize(1);
        assertThat(result.legs().get(0).distanceMeters()).isEqualTo(4200);
        assertThat(result.legs().get(0).durationSeconds()).isEqualTo(780);
    }

    @Test
    @DisplayName("3개 stop 정상 응답에서 sections 2건이 순서대로 leg로 매핑된다")
    void 코스경로계산_3개stop정상응답_leg2건이순서대로매핑된다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(
                        Map.of("distance", 4200, "duration", 780),
                        Map.of("distance", 3100, "duration", 600))))));

        CourseRouteResult result = defaultAdapter().calculate(threeStopRequest());

        assertThat(result.legs()).hasSize(2);
        assertThat(result.legs().get(0).distanceMeters()).isEqualTo(4200);
        assertThat(result.legs().get(0).durationSeconds()).isEqualTo(780);
        assertThat(result.legs().get(1).distanceMeters()).isEqualTo(3100);
        assertThat(result.legs().get(1).durationSeconds()).isEqualTo(600);
    }

    @Test
    @DisplayName("3개 stop 요청은 경로·좌표 순서·waypoints·헤더가 Kakao 요청 규격을 따른다")
    void 코스경로계산_3개stop요청_Kakao요청규격을따른다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(
                        Map.of("distance", 4200, "duration", 780),
                        Map.of("distance", 3100, "duration", 600))))));

        defaultAdapter().calculate(threeStopRequest());

        List<JsonNode> requests = directionsRequests();
        assertThat(requests).hasSize(1);
        JsonNode request = requests.get(0);
        assertThat(request.path("url").asText()).startsWith("/v1/directions?");
        Map<String, String> params = queryParams(request);
        assertThat(params).containsOnlyKeys("origin", "destination", "waypoints", "priority", "summary");
        assertThat(params.get("origin")).isEqualTo("127.200200,37.100100");
        assertThat(params.get("destination")).isEqualTo("127.400400,37.300300");
        assertThat(params.get("waypoints")).isEqualTo("127.300300,37.200200");
        assertThat(params.get("priority")).isEqualTo("RECOMMEND");
        assertThat(params.get("summary")).isEqualTo("false");
        assertThat(request.path("headers").path("Authorization").asText()).isEqualTo("KakaoAK " + API_KEY);
    }

    @Test
    @DisplayName("2개 stop 요청에는 waypoints 파라미터가 없다")
    void 코스경로계산_2개stop요청_waypoints파라미터가없다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of("distance", 4200, "duration", 780))))));

        defaultAdapter().calculate(twoStopRequest());

        List<JsonNode> requests = directionsRequests();
        assertThat(requests).hasSize(1);
        Map<String, String> params = queryParams(requests.get(0));
        assertThat(params).containsOnlyKeys("origin", "destination", "priority", "summary");
        assertThat(params).doesNotContainKey("waypoints");
        assertThat(params.get("summary")).isEqualTo("false");
    }

    @Test
    @DisplayName("429 응답이면 RATE_LIMIT으로 매핑하고 재시도 없이 요청 1건만 보낸다")
    void 코스경로계산_429응답_RATE_LIMIT으로매핑하고요청1건만보낸다() throws Exception {
        stubDirections(429, Map.of("error", "rate limited"));

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> defaultAdapter().calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.RATE_LIMIT);
        assertThat(directionsRequests()).hasSize(1);
    }

    @Test
    @DisplayName("500 응답이면 UPSTREAM으로 매핑하고 요청 1건만 보낸다")
    void 코스경로계산_500응답_UPSTREAM으로매핑하고요청1건만보낸다() throws Exception {
        stubDirections(500, Map.of("error", "server error"));

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> defaultAdapter().calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.UPSTREAM);
        assertThat(directionsRequests()).hasSize(1);
    }

    @Test
    @DisplayName("응답 timeout을 초과하면 TIMEOUT으로 매핑하고 요청 1건만 보낸다")
    void 코스경로계산_응답timeout초과_TIMEOUT으로매핑하고요청1건만보낸다() throws Exception {
        stubDirectionsWithDelay(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of("distance", 4200, "duration", 780))))), 3000);

        KakaoMobilityCourseRouteAdapter adapter = adapter(properties(true, true, API_KEY, Duration.ofSeconds(1)));
        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> adapter.calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.TIMEOUT);
        assertThat(directionsRequests()).hasSize(1);
    }

    @Test
    @DisplayName("sections 개수가 기대 leg 수보다 적으면 PARTIAL로 매핑한다")
    void 코스경로계산_sections개수부족_PARTIAL로매핑한다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of("distance", 4200, "duration", 780))))));

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> defaultAdapter().calculate(threeStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.PARTIAL);
        assertThat(directionsRequests()).hasSize(1);
    }

    @Test
    @DisplayName("section의 distance가 문자열이면 PARTIAL로 매핑한다")
    void 코스경로계산_distance가문자열_PARTIAL로매핑한다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of("distance", "far", "duration", 780))))));

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> defaultAdapter().calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.PARTIAL);
        assertThat(directionsRequests()).hasSize(1);
    }

    @Test
    @DisplayName("section의 distance가 음수이면 PARTIAL로 매핑한다")
    void 코스경로계산_distance가음수_PARTIAL로매핑한다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of("distance", -100, "duration", 780))))));

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> defaultAdapter().calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.PARTIAL);
        assertThat(directionsRequests()).hasSize(1);
    }

    @Test
    @DisplayName("routes가 빈 배열이면 SCHEMA로 매핑한다")
    void 코스경로계산_routes가빈배열_SCHEMA로매핑한다() throws Exception {
        stubDirections(200, Map.of("routes", List.of()));

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> defaultAdapter().calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.SCHEMA);
        assertThat(directionsRequests()).hasSize(1);
    }

    @Test
    @DisplayName("result_code가 0이 아니면 UPSTREAM으로 매핑한다")
    void 코스경로계산_result_code가0이아님_UPSTREAM으로매핑한다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 104,
                "sections", List.of(Map.of("distance", 4200, "duration", 780))))));

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> defaultAdapter().calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.UPSTREAM);
        assertThat(directionsRequests()).hasSize(1);
    }

    @Test
    @DisplayName("JSON이 아닌 본문이면 SCHEMA로 매핑한다")
    void 코스경로계산_JSON이아닌본문_SCHEMA로매핑한다() throws Exception {
        stubDirectionsRawBody(200, "not-json-body");

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> defaultAdapter().calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.SCHEMA);
        assertThat(directionsRequests()).hasSize(1);
    }

    @Test
    @DisplayName("roads.vertexes가 정상 제공되면 위도·경도 순으로 정규화하고 shapeStatus를 AVAILABLE로 매핑한다")
    void 코스경로계산_형상좌표정상제공_path와shapeStatus를정규화한다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of(
                        "distance", 4200,
                        "duration", 780,
                        "roads", List.of(Map.of(
                                "vertexes", List.of(127.2002, 37.1001, 127.3003, 37.2002, 127.4004, 37.3003)))))))));

        CourseRouteResult result = defaultAdapter().calculate(twoStopRequest());

        CourseRouteLeg leg = result.legs().get(0);
        assertThat(leg.shapeStatus()).isEqualTo(CourseRouteShapeStatus.AVAILABLE);
        assertThat(leg.path()).containsExactly(
                new CourseRouteVertex(37.1001, 127.2002),
                new CourseRouteVertex(37.2002, 127.3003),
                new CourseRouteVertex(37.3003, 127.4004));
    }

    @Test
    @DisplayName("여러 roads의 vertexes는 순서대로 이어붙인다")
    void 코스경로계산_roads가여러건_vertexes를순서대로이어붙인다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of(
                        "distance", 4200,
                        "duration", 780,
                        "roads", List.of(
                                Map.of("vertexes", List.of(127.2002, 37.1001)),
                                Map.of("vertexes", List.of(127.3003, 37.2002)))))))));

        CourseRouteResult result = defaultAdapter().calculate(twoStopRequest());

        assertThat(result.legs().get(0).path()).containsExactly(
                new CourseRouteVertex(37.1001, 127.2002),
                new CourseRouteVertex(37.2002, 127.3003));
    }

    @Test
    @DisplayName("형상 좌표가 500개를 초과하면 시작·끝점을 보존한 균등 샘플링으로 500개로 줄인다")
    void 코스경로계산_형상좌표500개초과_시작끝점보존샘플링으로500개로줄인다() throws Exception {
        int pointCount = 601;
        List<Double> vertexes = new ArrayList<>(pointCount * 2);
        for (int i = 0; i < pointCount; i++) {
            vertexes.add(127.0 + (i * 0.0001));
            vertexes.add(37.0 + (i * 0.0001));
        }
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of(
                        "distance", 4200,
                        "duration", 780,
                        "roads", List.of(Map.of("vertexes", vertexes))))))));

        CourseRouteResult result = defaultAdapter().calculate(twoStopRequest());

        List<CourseRouteVertex> path = result.legs().get(0).path();
        assertThat(path).hasSize(500);
        assertThat(path.get(0)).isEqualTo(new CourseRouteVertex(37.0, 127.0));
        assertThat(path.get(path.size() - 1)).isEqualTo(
                new CourseRouteVertex(37.0 + ((pointCount - 1) * 0.0001), 127.0 + ((pointCount - 1) * 0.0001)));
    }

    @Test
    @DisplayName("roads가 없으면 거리·시간은 정상 매핑하고 shapeStatus는 MISSING, path는 빈 목록이다")
    void 코스경로계산_roads가없음_shapeStatus가MISSING이고path가비어있다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of("distance", 4200, "duration", 780))))));

        CourseRouteResult result = defaultAdapter().calculate(twoStopRequest());

        CourseRouteLeg leg = result.legs().get(0);
        assertThat(leg.distanceMeters()).isEqualTo(4200);
        assertThat(leg.durationSeconds()).isEqualTo(780);
        assertThat(leg.shapeStatus()).isEqualTo(CourseRouteShapeStatus.MISSING);
        assertThat(leg.path()).isEmpty();
    }

    @Test
    @DisplayName("roads의 vertexes가 빈 배열이면 shapeStatus는 MISSING이고 요청은 실패하지 않는다")
    void 코스경로계산_vertexes가빈배열_shapeStatus가MISSING이고실패하지않는다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of(
                        "distance", 4200,
                        "duration", 780,
                        "roads", List.of(Map.of("vertexes", List.of()))))))));

        CourseRouteResult result = defaultAdapter().calculate(twoStopRequest());

        CourseRouteLeg leg = result.legs().get(0);
        assertThat(leg.shapeStatus()).isEqualTo(CourseRouteShapeStatus.MISSING);
        assertThat(leg.path()).isEmpty();
    }

    @Test
    @DisplayName("vertexes 길이가 홀수이면 SCHEMA로 매핑한다")
    void 코스경로계산_vertexes길이가홀수_SCHEMA로매핑한다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of(
                        "distance", 4200,
                        "duration", 780,
                        "roads", List.of(Map.of("vertexes", List.of(127.2002, 37.1001, 127.3003)))))))));

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> defaultAdapter().calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.SCHEMA);
    }

    @Test
    @DisplayName("vertexes의 위도가 범위를 벗어나면 SCHEMA로 매핑한다")
    void 코스경로계산_위도범위초과_SCHEMA로매핑한다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of(
                        "distance", 4200,
                        "duration", 780,
                        "roads", List.of(Map.of("vertexes", List.of(127.2002, 91.0)))))))));

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> defaultAdapter().calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.SCHEMA);
    }

    @Test
    @DisplayName("roads가 배열이 아닌 타입이면 형상 없음으로 조용히 넘기지 않고 SCHEMA로 매핑한다")
    void 코스경로계산_roads가배열이아닌타입_SCHEMA로매핑한다() throws Exception {
        stubDirections(200, Map.of("routes", List.of(Map.of(
                "result_code", 0,
                "sections", List.of(Map.of(
                        "distance", 4200,
                        "duration", 780,
                        "roads", Map.of("unexpected", "object")))))));

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> defaultAdapter().calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.SCHEMA);
    }

    @Test
    @DisplayName("enabled가 false이면 외부 호출 없이 PROVIDER_BLOCKED로 실패한다")
    void 코스경로계산_enabled가false_PROVIDER_BLOCKED로실패하고요청이없다() throws Exception {
        KakaoMobilityCourseRouteAdapter adapter = adapter(properties(false, true, API_KEY, Duration.ofSeconds(4)));

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> adapter.calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.PROVIDER_BLOCKED);
        assertThat(directionsRequests()).isEmpty();
    }

    @Test
    @DisplayName("restApiKey가 blank이면 외부 호출 없이 PROVIDER_BLOCKED로 실패한다")
    void 코스경로계산_restApiKey가blank_PROVIDER_BLOCKED로실패하고요청이없다() throws Exception {
        KakaoMobilityCourseRouteAdapter adapter = adapter(properties(true, true, "", Duration.ofSeconds(4)));

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> adapter.calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.PROVIDER_BLOCKED);
        assertThat(directionsRequests()).isEmpty();
    }

    @Test
    @DisplayName("freeTierVerified가 false이면 외부 호출 없이 PROVIDER_BLOCKED로 실패한다")
    void 코스경로계산_freeTierVerified가false_PROVIDER_BLOCKED로실패하고요청이없다() throws Exception {
        KakaoMobilityCourseRouteAdapter adapter = adapter(properties(true, false, API_KEY, Duration.ofSeconds(4)));

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> adapter.calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.PROVIDER_BLOCKED);
        assertThat(directionsRequests()).isEmpty();
    }

    @Test
    @DisplayName("서비스 요청 제한 permit을 얻지 못하면 외부 호출 없이 SERVICE_RATE_LIMIT으로 실패한다")
    void 코스경로계산_서비스요청제한Permit거부_SERVICE_RATE_LIMIT으로실패하고요청이없다() throws Exception {
        KakaoMobilityProperties properties = properties(true, true, API_KEY, Duration.ofSeconds(4));
        KakaoMobilityCourseRouteAdapter adapter = new KakaoMobilityCourseRouteAdapter(
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build(),
                objectMapper,
                properties,
                new com.masiton.restaurant.application.port.out.CourseRouteQuotaPort() {
                    @Override
                    public boolean tryAcquireMonthlyPermit() {
                        return true;
                    }

                    @Override
                    public boolean tryAcquireRequestPermit() {
                        return false;
                    }
                });

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> adapter.calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.SERVICE_RATE_LIMIT);
        assertThat(directionsRequests()).isEmpty();
    }

    @Test
    @DisplayName("Redis quota 저장소 장애는 외부 호출 없이 PROVIDER_BLOCKED로 실패한다")
    void 코스경로계산_RedisQuota저장소장애_PROVIDER_BLOCKED로실패하고요청이없다() throws Exception {
        KakaoMobilityProperties properties = properties(true, true, API_KEY, Duration.ofSeconds(4));
        KakaoMobilityCourseRouteAdapter adapter = new KakaoMobilityCourseRouteAdapter(
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build(),
                objectMapper,
                properties,
                new com.masiton.restaurant.application.port.out.CourseRouteQuotaPort() {
                    @Override
                    public boolean tryAcquireMonthlyPermit() {
                        return true;
                    }

                    @Override
                    public boolean tryAcquireRequestPermit() {
                        throw new com.masiton.restaurant.application.port.out.CourseRouteQuotaUnavailableException(
                                new IllegalStateException("redis unavailable"));
                    }
                });

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> adapter.calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.PROVIDER_BLOCKED);
        assertThat(directionsRequests()).isEmpty();
    }

    @Test
    @DisplayName("월 quota permit을 얻지 못하면 외부 호출 없이 PROVIDER_BLOCKED로 실패한다")
    void 코스경로계산_월QuotaPermit거부_PROVIDER_BLOCKED로실패하고요청이없다() throws Exception {
        KakaoMobilityProperties properties = properties(true, true, API_KEY, Duration.ofSeconds(4));
        KakaoMobilityCourseRouteAdapter adapter = new KakaoMobilityCourseRouteAdapter(
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build(),
                objectMapper,
                properties,
                () -> false);

        CourseRouteProviderException exception = catchThrowableOfType(
                CourseRouteProviderException.class, () -> adapter.calculate(twoStopRequest()));

        assertThat(exception.category()).isEqualTo(CourseRouteFailureCategory.PROVIDER_BLOCKED);
        assertThat(directionsRequests()).isEmpty();
    }

    @Test
    @DisplayName("stop이 1개이면 IllegalArgumentException이 발생하고 외부 호출이 없다")
    void 코스경로계산_stop이1개_IllegalArgumentException이발생하고요청이없다() throws Exception {
        KakaoMobilityCourseRouteAdapter adapter = defaultAdapter();

        assertThatThrownBy(() -> adapter.calculate(stopsOf(1))).isInstanceOf(IllegalArgumentException.class);

        assertThat(directionsRequests()).isEmpty();
    }

    @Test
    @DisplayName("stop이 6개이면 IllegalArgumentException이 발생하고 외부 호출이 없다")
    void 코스경로계산_stop이6개_IllegalArgumentException이발생하고요청이없다() throws Exception {
        KakaoMobilityCourseRouteAdapter adapter = defaultAdapter();

        assertThatThrownBy(() -> adapter.calculate(stopsOf(6))).isInstanceOf(IllegalArgumentException.class);

        assertThat(directionsRequests()).isEmpty();
    }

    private CourseRouteRequest twoStopRequest() {
        return new CourseRouteRequest(List.of(
                new CourseRouteWaypoint(UUID.randomUUID(), new BigDecimal("37.100100"), new BigDecimal("127.200200")),
                new CourseRouteWaypoint(UUID.randomUUID(), new BigDecimal("37.300300"), new BigDecimal("127.400400"))));
    }

    private CourseRouteRequest threeStopRequest() {
        return new CourseRouteRequest(List.of(
                new CourseRouteWaypoint(UUID.randomUUID(), new BigDecimal("37.100100"), new BigDecimal("127.200200")),
                new CourseRouteWaypoint(UUID.randomUUID(), new BigDecimal("37.200200"), new BigDecimal("127.300300")),
                new CourseRouteWaypoint(UUID.randomUUID(), new BigDecimal("37.300300"), new BigDecimal("127.400400"))));
    }

    private CourseRouteRequest stopsOf(int count) {
        List<CourseRouteWaypoint> stops = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            stops.add(new CourseRouteWaypoint(UUID.randomUUID(),
                    new BigDecimal("37." + (100000 + i)), new BigDecimal("127." + (200000 + i))));
        }
        return new CourseRouteRequest(stops);
    }

    private KakaoMobilityCourseRouteAdapter defaultAdapter() {
        return adapter(properties(true, true, API_KEY, Duration.ofSeconds(4)));
    }

    private KakaoMobilityCourseRouteAdapter adapter(KakaoMobilityProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        return new KakaoMobilityCourseRouteAdapter(httpClient, objectMapper, properties);
    }

    private KakaoMobilityProperties properties(
            boolean enabled, boolean freeTierVerified, String restApiKey, Duration responseTimeout) {
        KakaoMobilityProperties properties = new KakaoMobilityProperties();
        properties.setEnabled(enabled);
        properties.setFreeTierVerified(freeTierVerified);
        properties.setPaidBillingEnabled(false);
        properties.setRestApiKey(restApiKey);
        properties.setBaseUrl(baseUrl());
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setResponseTimeout(responseTimeout);
        properties.setTotalTimeout(Duration.ofSeconds(5));
        return properties;
    }

    private String baseUrl() {
        return "http://%s:%d".formatted(WIREMOCK.getHost(), WIREMOCK.getMappedPort(WIREMOCK_PORT));
    }

    private void stubDirections(int status, Object jsonBody) throws Exception {
        Map<String, Object> mapping = Map.of(
                "request", Map.of("method", "GET", "urlPath", "/v1/directions"),
                "response", Map.of("status", status, "jsonBody", jsonBody));
        admin("POST", "/__admin/mappings", objectMapper.writeValueAsString(mapping));
    }

    private void stubDirectionsWithDelay(int status, Object jsonBody, int delayMillis) throws Exception {
        Map<String, Object> mapping = Map.of(
                "request", Map.of("method", "GET", "urlPath", "/v1/directions"),
                "response", Map.of(
                        "status", status,
                        "jsonBody", jsonBody,
                        "fixedDelayMilliseconds", delayMillis));
        admin("POST", "/__admin/mappings", objectMapper.writeValueAsString(mapping));
    }

    private void stubDirectionsRawBody(int status, String rawBody) throws Exception {
        Map<String, Object> mapping = Map.of(
                "request", Map.of("method", "GET", "urlPath", "/v1/directions"),
                "response", Map.of("status", status, "body", rawBody));
        admin("POST", "/__admin/mappings", objectMapper.writeValueAsString(mapping));
    }

    private List<JsonNode> directionsRequests() throws Exception {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode entry : journalRequests()) {
            JsonNode request = entry.path("request");
            if (request.path("url").asText().startsWith("/v1/directions")) {
                result.add(request);
            }
        }
        return result;
    }

    private JsonNode journalRequests() throws Exception {
        HttpResponse<String> response = admin("GET", "/__admin/requests", "");
        return objectMapper.readTree(response.body()).path("requests");
    }

    private Map<String, String> queryParams(JsonNode requestNode) {
        String url = requestNode.path("url").asText();
        String query = url.substring(url.indexOf('?') + 1);
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            String[] keyAndValue = pair.split("=", 2);
            String key = URLDecoder.decode(keyAndValue[0], StandardCharsets.UTF_8);
            String value = keyAndValue.length > 1 ? URLDecoder.decode(keyAndValue[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
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
