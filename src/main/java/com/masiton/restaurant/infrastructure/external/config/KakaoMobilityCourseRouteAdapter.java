package com.masiton.restaurant.infrastructure.external.config;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.masiton.restaurant.application.port.out.CourseRouteFailureCategory;
import com.masiton.restaurant.application.port.out.CourseRouteLeg;
import com.masiton.restaurant.application.port.out.CourseRouteProviderException;
import com.masiton.restaurant.application.port.out.CourseRouteProviderPort;
import com.masiton.restaurant.application.port.out.CourseRouteQuotaPort;
import com.masiton.restaurant.application.port.out.CourseRouteQuotaUnavailableException;
import com.masiton.restaurant.application.port.out.CourseRouteRequest;
import com.masiton.restaurant.application.port.out.CourseRouteResult;
import com.masiton.restaurant.application.port.out.CourseRouteVertex;
import com.masiton.restaurant.application.port.out.CourseRouteWaypoint;
import com.masiton.restaurant.domain.course.CourseRouteShapeStatus;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Kakao Mobility 자동차 길찾기({@code /v1/directions}) HTTP-only adapter다. ADR-ROUTE-001 5.2절·9절에 따라
 * 코스당 정확히 1회만 호출하고 재시도하지 않으며, 좌표·요청 URI·응답 본문·API Key를 로그·예외 메시지에 남기지 않는다
 * (NFR-OBSERVABILITY-005). 이 클래스는 Logger를 두지 않는다.
 */
final class KakaoMobilityCourseRouteAdapter implements CourseRouteProviderPort {

    private static final int MIN_STOPS = 2;
    private static final int MAX_STOPS = 5;
    /** ADR-ROUTE-001 5.5절: 구간당 형상 좌표 상한이다. */
    private static final int MAX_PATH_POINTS = 500;
    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final KakaoMobilityProperties properties;
    private final CourseRouteQuotaPort quotaPort;

    KakaoMobilityCourseRouteAdapter(HttpClient httpClient, ObjectMapper objectMapper, KakaoMobilityProperties properties) {
        this(httpClient, objectMapper, properties, () -> true);
    }

    KakaoMobilityCourseRouteAdapter(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            KakaoMobilityProperties properties,
            CourseRouteQuotaPort quotaPort) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.quotaPort = quotaPort;
    }

    @Override
    public CourseRouteResult calculate(CourseRouteRequest request) {
        List<CourseRouteWaypoint> stops = validatedStops(request);
        assertFreeTierCallAllowed();
        try {
            if (!quotaPort.tryAcquireRequestPermit()) {
                throw new CourseRouteProviderException(CourseRouteFailureCategory.SERVICE_RATE_LIMIT);
            }
            if (!quotaPort.tryAcquireMonthlyPermit()) {
                throw new CourseRouteProviderException(CourseRouteFailureCategory.PROVIDER_BLOCKED);
            }
            HttpRequest httpRequest = HttpRequest.newBuilder(requestUri(stops))
                    .timeout(properties.getResponseTimeout())
                    .header("Authorization", "KakaoAK " + properties.getRestApiKey())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new CourseRouteProviderException(CourseRouteFailureCategory.RATE_LIMIT);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CourseRouteProviderException(CourseRouteFailureCategory.UPSTREAM);
            }
            return normalize(response.body(), stops.size() - 1);
        } catch (HttpTimeoutException exception) {
            throw new CourseRouteProviderException(CourseRouteFailureCategory.TIMEOUT, exception);
        } catch (IOException exception) {
            throw new CourseRouteProviderException(CourseRouteFailureCategory.UPSTREAM, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CourseRouteProviderException(CourseRouteFailureCategory.TIMEOUT, exception);
        } catch (JacksonException exception) {
            throw new CourseRouteProviderException(CourseRouteFailureCategory.SCHEMA, exception);
        } catch (CourseRouteQuotaUnavailableException exception) {
            throw new CourseRouteProviderException(CourseRouteFailureCategory.PROVIDER_BLOCKED, exception);
        } finally {
            quotaPort.releaseRequestPermit();
        }
    }

    private List<CourseRouteWaypoint> validatedStops(CourseRouteRequest request) {
        List<CourseRouteWaypoint> stops = request == null ? null : request.stops();
        if (stops == null || stops.size() < MIN_STOPS || stops.size() > MAX_STOPS) {
            throw new IllegalArgumentException("CourseRouteRequest must contain between " + MIN_STOPS + " and " + MAX_STOPS + " stops");
        }
        return stops;
    }

    private void assertFreeTierCallAllowed() {
        if (!properties.isEnabled() || !properties.isFreeTierVerified() || properties.isPaidBillingEnabled()
                || properties.getRestApiKey().isBlank()) {
            throw new CourseRouteProviderException(CourseRouteFailureCategory.PROVIDER_BLOCKED);
        }
    }

    private URI requestUri(List<CourseRouteWaypoint> stops) {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        StringBuilder query = new StringBuilder();
        query.append("origin=").append(encode(coordinateValue(stops.get(0))));
        query.append("&destination=").append(encode(coordinateValue(stops.get(stops.size() - 1))));
        if (stops.size() > MIN_STOPS) {
            String waypoints = stops.subList(1, stops.size() - 1).stream()
                    .map(this::coordinateValue)
                    .collect(Collectors.joining("|"));
            query.append("&waypoints=").append(encode(waypoints));
        }
        query.append("&priority=RECOMMEND");
        // ADR-ROUTE-001 5.2절: summary=false로 sections[].roads[].vertexes를 함께 받아 실제 경로
        // 형상을 정규화한다. 코스당 호출 횟수(최대 1회)는 그대로 유지한다.
        query.append("&summary=false");
        return URI.create(baseUrl + "/v1/directions?" + query);
    }

    /** Kakao Mobility는 {@code x,y} = 경도,위도 순서를 요구한다. 뒤집지 않는다. */
    private String coordinateValue(CourseRouteWaypoint stop) {
        return stop.longitude().toPlainString() + "," + stop.latitude().toPlainString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private CourseRouteResult normalize(String body, int expectedLegCount) {
        JsonNode root = objectMapper.readTree(body);
        JsonNode routes = root.path("routes");
        if (!routes.isArray() || routes.isEmpty()) {
            throw new CourseRouteProviderException(CourseRouteFailureCategory.SCHEMA);
        }
        JsonNode route = routes.get(0);
        JsonNode resultCode = route.path("result_code");
        if (!resultCode.isIntegralNumber()) {
            throw new CourseRouteProviderException(CourseRouteFailureCategory.SCHEMA);
        }
        if (resultCode.asInt() != 0) {
            throw new CourseRouteProviderException(CourseRouteFailureCategory.UPSTREAM);
        }
        JsonNode sections = route.path("sections");
        if (!sections.isArray()) {
            throw new CourseRouteProviderException(CourseRouteFailureCategory.SCHEMA);
        }
        if (sections.size() != expectedLegCount) {
            throw new CourseRouteProviderException(CourseRouteFailureCategory.PARTIAL);
        }
        return new CourseRouteResult(toLegs(sections));
    }

    private List<CourseRouteLeg> toLegs(JsonNode sections) {
        List<CourseRouteLeg> legs = new ArrayList<>(sections.size());
        for (JsonNode section : sections) {
            JsonNode distance = section.path("distance");
            JsonNode duration = section.path("duration");
            if (!distance.isIntegralNumber() || !duration.isIntegralNumber()
                    || distance.asLong() < 0 || duration.asLong() < 0) {
                throw new CourseRouteProviderException(CourseRouteFailureCategory.PARTIAL);
            }
            List<CourseRouteVertex> path = toPath(section.path("roads"));
            CourseRouteShapeStatus shapeStatus =
                    path.isEmpty() ? CourseRouteShapeStatus.MISSING : CourseRouteShapeStatus.AVAILABLE;
            legs.add(new CourseRouteLeg(distance.asInt(), duration.asInt(), shapeStatus, path));
        }
        return legs;
    }

    /**
     * BR-COURSE-005·ADR-ROUTE-001 5.5절: {@code roads}가 없거나 모든 {@code vertexes}가 비어 있으면
     * 오류가 아니라 해당 구간의 형상 좌표 없음으로 취급하고 빈 목록을 반환한다.
     */
    private List<CourseRouteVertex> toPath(JsonNode roads) {
        if (!roads.isArray() || roads.isEmpty()) {
            return List.of();
        }
        List<CourseRouteVertex> vertexes = new ArrayList<>();
        for (JsonNode road : roads) {
            JsonNode rawVertexes = road.path("vertexes");
            if (!rawVertexes.isArray()) {
                throw new CourseRouteProviderException(CourseRouteFailureCategory.SCHEMA);
            }
            if (rawVertexes.isEmpty()) {
                continue;
            }
            if (rawVertexes.size() % 2 != 0) {
                throw new CourseRouteProviderException(CourseRouteFailureCategory.SCHEMA);
            }
            for (int i = 0; i < rawVertexes.size(); i += 2) {
                vertexes.add(toVertex(rawVertexes.get(i), rawVertexes.get(i + 1)));
            }
        }
        return downsample(vertexes);
    }

    /** Kakao {@code vertexes}는 {@code [경도, 위도, 경도, 위도, ...]} 순서다. 뒤집지 않는다. */
    private CourseRouteVertex toVertex(JsonNode longitudeNode, JsonNode latitudeNode) {
        if (!longitudeNode.isNumber() || !latitudeNode.isNumber()) {
            throw new CourseRouteProviderException(CourseRouteFailureCategory.SCHEMA);
        }
        double longitude = longitudeNode.asDouble();
        double latitude = latitudeNode.asDouble();
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE
                || longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new CourseRouteProviderException(CourseRouteFailureCategory.SCHEMA);
        }
        return new CourseRouteVertex(latitude, longitude);
    }

    /**
     * 세그먼트당 500개 상한을 초과하면 시작·끝점을 보존한 균등 간격 샘플링으로 줄인다.
     * ADR-ROUTE-001 5.5절: 이 축소는 형상 누락이나 실패가 아니다.
     */
    private List<CourseRouteVertex> downsample(List<CourseRouteVertex> vertexes) {
        if (vertexes.size() <= MAX_PATH_POINTS) {
            return vertexes;
        }
        int lastIndex = vertexes.size() - 1;
        double step = (double) lastIndex / (MAX_PATH_POINTS - 1);
        List<CourseRouteVertex> sampled = new ArrayList<>(MAX_PATH_POINTS);
        for (int i = 0; i < MAX_PATH_POINTS; i++) {
            int index = (int) Math.round(i * step);
            sampled.add(vertexes.get(Math.min(index, lastIndex)));
        }
        return sampled;
    }
}
