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

import com.masiton.restaurant.application.PlaceVerificationFailedException;
import com.masiton.restaurant.application.port.out.PlaceVerificationPort;
import com.masiton.restaurant.application.port.out.VerifiedPlace;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Kakao Local Keyword API를 호출해 제출된 Kakao 장소 URL의 동일성을 확인한다.
 */
@Component
class KakaoPlaceVerificationAdapter implements PlaceVerificationPort {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String restApiKey;

    @Autowired
    KakaoPlaceVerificationAdapter(
            ObjectMapper objectMapper,
            @Value("${masiton.integration.kakao.base-url:https://dapi.kakao.com}") String baseUrl,
            @Value("${masiton.integration.kakao.rest-api-key:}") String restApiKey
    ) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), objectMapper, baseUrl, restApiKey);
    }

    KakaoPlaceVerificationAdapter(
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
    public Optional<VerifiedPlace> verify(String restaurantName, URI kakaoPlaceUrl, String fallbackPhoneNumber) {
        try {
            String query = URLEncoder.encode(restaurantName, StandardCharsets.UTF_8);
            URI requestUri = baseUri.resolve("/v2/local/search/keyword.json?query=" + query);
            HttpRequest.Builder request = HttpRequest.newBuilder(requestUri)
                    .timeout(RESPONSE_TIMEOUT)
                    .GET();
            if (!restApiKey.isBlank()) {
                request.header("Authorization", "KakaoAK " + restApiKey);
            }

            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PlaceVerificationFailedException();
            }

            return selectPlace(response.body(), kakaoPlaceUrl, fallbackPhoneNumber);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PlaceVerificationFailedException(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof PlaceVerificationFailedException) {
                throw exception;
            }
            throw new PlaceVerificationFailedException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<VerifiedPlace> selectPlace(String responseBody, URI submittedUrl, String fallbackPhoneNumber) {
        try {
            Map<String, Object> payload = objectMapper.readValue(responseBody, MAP_TYPE);
            Object documentsValue = payload.get("documents");
            if (!(documentsValue instanceof List<?> documents)) {
                throw new PlaceVerificationFailedException();
            }
            return documents.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(document -> toVerifiedPlace((Map<String, Object>) document, fallbackPhoneNumber))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(place -> samePlaceUrl(place.kakaoPlaceUrl(), submittedUrl))
                    .findFirst();
        } catch (JacksonException exception) {
            throw new PlaceVerificationFailedException(exception);
        }
    }

    private Optional<VerifiedPlace> toVerifiedPlace(Map<String, Object> document, String fallbackPhoneNumber) {
        String id = stringValue(document.get("id"));
        String name = stringValue(document.get("place_name"));
        String placeUrl = stringValue(document.get("place_url"));
        String roadAddress = stringValue(document.get("road_address_name"));
        String phoneNumber = stringValue(document.get("phone"));
        if (phoneNumber == null) {
            phoneNumber = fallbackPhoneNumber;
        }
        if (id == null || name == null || placeUrl == null || roadAddress == null || phoneNumber == null) {
            throw new PlaceVerificationFailedException();
        }
        return Optional.of(new VerifiedPlace(
                id, name, canonicalPlaceUrl(placeUrl), canonicalRoadAddress(roadAddress), phoneNumber));
    }

    /**
     * Kakao는 시도명을 축약해 {@code 서울 강남구 ...}로 준다. 도메인은 계약대로
     * {@code 서울특별시 ...} 전체 표기를 쓰므로(reference-data-api 맛집 등록 규칙,
     * 자치구 추출 패턴) 여기서 맞춘다. 제공자 표기를 도메인 표기로 바꾸는 것은
     * Adapter의 책임이다.
     *
     * 서울 밖 주소는 바꾸지 않고 그대로 넘긴다. 등록 서비스가 자치구 추출에서
     * 거부해야 하며, 여기서 서울로 보이게 만들면 그 판정을 무력화한다.
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
     * Kakao 응답의 {@code place_url}은 {@code http}로 온다. 저장·노출하는 값은 https로
     * 맞춘다. 같은 호스트가 https로 서비스하므로 scheme만 바꿔도 같은 자원을 가리킨다.
     */
    private String canonicalPlaceUrl(String placeUrl) {
        try {
            URI uri = URI.create(placeUrl);
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return placeUrl;
            }
            return new URI("https", uri.getAuthority(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
        } catch (IllegalArgumentException | URISyntaxException exception) {
            throw new PlaceVerificationFailedException(exception);
        }
    }

    private boolean samePlaceUrl(String verifiedUrl, URI submittedUrl) {
        try {
            URI verifiedUri = URI.create(verifiedUrl);
            String scheme = verifiedUri.getScheme();
            String host = verifiedUri.getHost();
            if (scheme == null || host == null) {
                return false;
            }
            // 제공자가 http로 주므로 scheme을 http·https 둘 다 허용한다. https만 받으면
            // 실제 Kakao 응답의 모든 후보가 탈락해 맛집 등록이 성립하지 않는다.
            // 동일성 판정은 host와 path로 한다.
            return (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                    && host.equalsIgnoreCase("place.map.kakao.com")
                    && verifiedUri.getPath().equals(submittedUrl.getPath());
        } catch (IllegalArgumentException exception) {
            throw new PlaceVerificationFailedException(exception);
        }
    }

    private String stringValue(Object value) {
        if (!(value instanceof String string)) {
            return null;
        }
        String normalized = string.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
