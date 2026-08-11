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
import com.masiton.restaurant.application.port.out.CourseRouteRequest;
import com.masiton.restaurant.application.port.out.CourseRouteResult;
import com.masiton.restaurant.application.port.out.CourseRouteWaypoint;

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
        if (!quotaPort.tryAcquireRequestPermit()) {
            throw new CourseRouteProviderException(CourseRouteFailureCategory.SERVICE_RATE_LIMIT);
        }
        try {
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
        query.append("&summary=true");
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
            legs.add(new CourseRouteLeg(distance.asInt(), duration.asInt()));
        }
        return legs;
    }
}
