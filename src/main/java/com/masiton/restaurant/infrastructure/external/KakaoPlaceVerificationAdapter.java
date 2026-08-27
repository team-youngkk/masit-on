package com.masiton.restaurant.infrastructure.external;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.masiton.common.address.SeoulRoadAddressNormalizer;
import com.masiton.restaurant.application.PlaceVerificationFailedException;
import com.masiton.restaurant.application.port.out.PlaceVerificationPort;
import com.masiton.restaurant.application.port.out.VerifiedPlace;
import tools.jackson.databind.ObjectMapper;

/**
 * Kakao Local Keyword API를 호출해 제출된 Kakao 장소 URL의 동일성을 확인한다.
 */
@Component
class KakaoPlaceVerificationAdapter implements PlaceVerificationPort {

    private final KakaoLocalKeywordClient client;

    @Autowired
    KakaoPlaceVerificationAdapter(KakaoLocalKeywordClient client) {
        this.client = client;
    }

    KakaoPlaceVerificationAdapter(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            String restApiKey
    ) {
        this(new KakaoLocalKeywordClient(httpClient, objectMapper, baseUrl, restApiKey));
    }

    KakaoPlaceVerificationAdapter(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            String restApiKey,
            String allowedOrigins
    ) {
        this(new KakaoLocalKeywordClient(httpClient, objectMapper, baseUrl, restApiKey, allowedOrigins));
    }

    @Override
    public Optional<VerifiedPlace> verify(String restaurantName, URI kakaoPlaceUrl, String fallbackPhoneNumber) {
        try {
            KakaoLocalKeywordClient.KakaoKeywordResponse response = client.search(restaurantName);
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PlaceVerificationFailedException();
            }

            return selectPlace(response.documents(), kakaoPlaceUrl, fallbackPhoneNumber);
        } catch (RuntimeException exception) {
            if (exception instanceof PlaceVerificationFailedException) {
                throw exception;
            }
            throw new PlaceVerificationFailedException(exception);
        }
    }

    private Optional<VerifiedPlace> selectPlace(
            List<Map<String, Object>> documents,
            URI submittedUrl,
            String fallbackPhoneNumber
    ) {
        return documents.stream()
                .map(document -> toVerifiedPlace(document, fallbackPhoneNumber))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(place -> samePlaceUrl(place.kakaoPlaceUrl(), submittedUrl))
                .findFirst();
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
        BigDecimal longitude = decimalValue(document.get("x"), -180, 180);
        BigDecimal latitude = decimalValue(document.get("y"), -90, 90);
        if (latitude == null || longitude == null) {
            latitude = null;
            longitude = null;
        }
        URI canonicalPlaceUrl = KakaoPlaceUrlPolicy.canonicalize(placeUrl)
                .orElseThrow(PlaceVerificationFailedException::new);
        return Optional.of(new VerifiedPlace(
                id, name, canonicalPlaceUrl.toString(), SeoulRoadAddressNormalizer.normalize(roadAddress), phoneNumber,
                latitude, longitude));
    }

    /** 좌표는 등록 필수 항목이 아니므로 값이 없거나 범위를 벗어나면 검증 실패 대신 null로 취급한다. */
    private BigDecimal decimalValue(Object value, int minimum, int maximum) {
        String raw = stringValue(value);
        if (raw == null) {
            return null;
        }
        try {
            BigDecimal decimal = new BigDecimal(raw);
            if (decimal.compareTo(BigDecimal.valueOf(minimum)) < 0 || decimal.compareTo(BigDecimal.valueOf(maximum)) > 0) {
                return null;
            }
            return decimal;
        } catch (NumberFormatException exception) {
            return null;
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
            // 여기 오는 값은 canonicalPlaceUrl을 이미 통과했다. 그 메서드가 http·https만
            // 허용하고 https로 정규화하며 user-info와 비표준 포트를 거부하므로, 이 시점의
            // 값은 scheme이 https이고 authority가 host뿐이다. 그 불변식을 조건으로 남겨
            // 정규화 단계가 바뀌면 판정이 조용히 느슨해지지 않게 한다.
            // 동일성 판정은 host와 path로 한다.
            return scheme.equalsIgnoreCase("https")
                    && KakaoPlaceUrlPolicy.hasKakaoPlaceHost(verifiedUri)
                    && verifiedUri.getPath().equals(submittedUrl.getPath());
        } catch (IllegalArgumentException exception) {
            throw new PlaceVerificationFailedException(exception);
        }
    }

    private String stringValue(Object value) {
        if (!(value instanceof String string)) {
            return null;
        }
        String normalized = string.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
