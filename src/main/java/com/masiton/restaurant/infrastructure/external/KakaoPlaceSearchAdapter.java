package com.masiton.restaurant.infrastructure.external;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.masiton.restaurant.application.PlaceSearchFailedException;
import com.masiton.restaurant.application.port.out.PlaceSearchCandidate;
import com.masiton.restaurant.application.port.out.PlaceSearchPort;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Kakao Local Keyword API를 호출해 상호명으로 장소 후보를 검색한다. 등록에 쓸 수 없는
 * 문서(도로명주소·장소 링크 없음)는 예외를 던지지 않고 조용히 제외한다.
 */
@Component
class KakaoPlaceSearchAdapter implements PlaceSearchPort {

    private static final String KAKAO_PLACE_HOST = "place.map.kakao.com";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String restApiKey;

    @Autowired
    KakaoPlaceSearchAdapter(
            ObjectMapper objectMapper,
            @Value("${masiton.integration.kakao.base-url:https://dapi.kakao.com}") String baseUrl,
            @Value("${masiton.integration.kakao.rest-api-key:}") String restApiKey
    ) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), objectMapper, baseUrl, restApiKey);
    }

    KakaoPlaceSearchAdapter(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            String restApiKey
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUri = URI.create(baseUrl);
        this.restApiKey = restApiKey;
    }

    @Override
    public List<PlaceSearchCandidate> search(String name) {
        try {
            String query = URLEncoder.encode(name, StandardCharsets.UTF_8);
            URI requestUri = baseUri.resolve("/v2/local/search/keyword.json?query=" + query);
            HttpRequest.Builder request = HttpRequest.newBuilder(requestUri)
                    .timeout(RESPONSE_TIMEOUT)
                    .GET();
            if (!restApiKey.isBlank()) {
                request.header("Authorization", "KakaoAK " + restApiKey);
            }

            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PlaceSearchFailedException();
            }

            return parseDocuments(response.body());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PlaceSearchFailedException(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof PlaceSearchFailedException) {
                throw exception;
            }
            throw new PlaceSearchFailedException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<PlaceSearchCandidate> parseDocuments(String responseBody) {
        try {
            Map<String, Object> payload = objectMapper.readValue(responseBody, MAP_TYPE);
            Object documentsValue = payload.get("documents");
            if (!(documentsValue instanceof List<?> documents)) {
                throw new PlaceSearchFailedException();
            }
            return documents.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(document -> toCandidate((Map<String, Object>) document))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } catch (JacksonException exception) {
            throw new PlaceSearchFailedException(exception);
        }
    }

    /** 도로명주소나 장소 링크가 없으면 등록에 쓸 수 없으므로 이 문서를 조용히 제외한다. */
    private Optional<PlaceSearchCandidate> toCandidate(Map<String, Object> document) {
        String name = stringValue(document.get("place_name"));
        String placeUrl = stringValue(document.get("place_url"));
        String roadAddress = stringValue(document.get("road_address_name"));
        String phoneNumber = stringValue(document.get("phone"));
        if (name == null || placeUrl == null || roadAddress == null) {
            return Optional.empty();
        }
        Optional<String> canonicalUrl = canonicalPlaceUrl(placeUrl);
        if (canonicalUrl.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PlaceSearchCandidate(
                name, canonicalUrl.get(), canonicalRoadAddress(roadAddress), phoneNumber));
    }

    /**
     * Kakao는 시도명을 축약해 {@code 서울 강남구 ...}로 준다. 등록 계약은
     * {@code 서울특별시 ...} 전체 표기를 쓰므로 검색 결과도 같은 표기로 맞춘다.
     * 서울 밖 주소는 그대로 넘긴다.
     */
    private String canonicalRoadAddress(String roadAddress) {
        String normalized = roadAddress.trim();
        if (normalized.startsWith("서울특별시")) {
            return normalized;
        }
        if (normalized.startsWith("서울 ")) {
            return "서울특별시 " + normalized.substring("서울 ".length()).trim();
        }
        return normalized;
    }

    /**
     * {@code KakaoPlaceVerificationAdapter}의 place_url 정규화와 같은 규칙이지만,
     * 검색은 계약 위반 문서를 예외 대신 제외해야 하므로 실패를 {@link Optional#empty()}로 알린다.
     */
    private Optional<String> canonicalPlaceUrl(String placeUrl) {
        try {
            URI uri = URI.create(placeUrl);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                return Optional.empty();
            }
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null) {
                return Optional.empty();
            }
            if (!host.equalsIgnoreCase(KAKAO_PLACE_HOST)) {
                return Optional.empty();
            }
            int port = uri.getPort();
            if (port != -1 && port != defaultPort(scheme)) {
                return Optional.empty();
            }
            return Optional.of(new URI("https", null, host, -1, uri.getPath(), uri.getQuery(), uri.getFragment())
                    .toString());
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return Optional.empty();
        }
    }

    private int defaultPort(String scheme) {
        return scheme.equalsIgnoreCase("https") ? 443 : 80;
    }

    private String stringValue(Object value) {
        if (!(value instanceof String string)) {
            return null;
        }
        String normalized = string.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
