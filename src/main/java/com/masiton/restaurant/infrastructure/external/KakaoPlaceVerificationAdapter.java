package com.masiton.restaurant.infrastructure.external;

import java.io.IOException;
import java.net.URI;
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
    public Optional<VerifiedPlace> verify(String restaurantName, URI kakaoPlaceUrl) {
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

            return selectPlace(response.body(), kakaoPlaceUrl);
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
    private Optional<VerifiedPlace> selectPlace(String responseBody, URI submittedUrl) {
        try {
            Map<String, Object> payload = objectMapper.readValue(responseBody, MAP_TYPE);
            Object documentsValue = payload.get("documents");
            if (!(documentsValue instanceof List<?> documents)) {
                throw new PlaceVerificationFailedException();
            }
            return documents.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(document -> toVerifiedPlace((Map<String, Object>) document))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(place -> samePlaceUrl(place.kakaoPlaceUrl(), submittedUrl))
                    .findFirst();
        } catch (JacksonException exception) {
            throw new PlaceVerificationFailedException(exception);
        }
    }

    private Optional<VerifiedPlace> toVerifiedPlace(Map<String, Object> document) {
        String id = stringValue(document.get("id"));
        String name = stringValue(document.get("place_name"));
        String placeUrl = stringValue(document.get("place_url"));
        String roadAddress = stringValue(document.get("road_address_name"));
        String phoneNumber = stringValue(document.get("phone"));
        if (id == null || name == null || placeUrl == null || roadAddress == null || phoneNumber == null) {
            throw new PlaceVerificationFailedException();
        }
        return Optional.of(new VerifiedPlace(id, name, placeUrl, roadAddress, phoneNumber));
    }

    private boolean samePlaceUrl(String verifiedUrl, URI submittedUrl) {
        try {
            URI verifiedUri = URI.create(verifiedUrl);
            return verifiedUri.getScheme().equalsIgnoreCase("https")
                    && verifiedUri.getHost().equalsIgnoreCase("place.map.kakao.com")
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
