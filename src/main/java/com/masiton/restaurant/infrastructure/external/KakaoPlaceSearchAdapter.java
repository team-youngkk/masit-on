package com.masiton.restaurant.infrastructure.external;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.masiton.restaurant.application.PlaceSearchFailedException;
import com.masiton.restaurant.application.SeoulRoadAddressNormalizer;
import com.masiton.restaurant.application.port.out.PlaceSearchCandidate;
import com.masiton.restaurant.application.port.out.PlaceSearchPort;
import tools.jackson.databind.ObjectMapper;

/**
 * Kakao Local Keyword API를 호출해 상호명으로 장소 후보를 검색한다. 등록에 쓸 수 없는
 * 문서(도로명주소·장소 링크 없음)는 예외를 던지지 않고 조용히 제외한다.
 */
@Component
class KakaoPlaceSearchAdapter implements PlaceSearchPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(KakaoPlaceSearchAdapter.class);

    private final KakaoLocalKeywordClient client;

    @Autowired
    KakaoPlaceSearchAdapter(KakaoLocalKeywordClient client) {
        this.client = client;
    }

    KakaoPlaceSearchAdapter(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            String restApiKey
    ) {
        this(new KakaoLocalKeywordClient(httpClient, objectMapper, baseUrl, restApiKey));
    }

    @Override
    public List<PlaceSearchCandidate> search(String name) {
        try {
            KakaoLocalKeywordClient.KakaoKeywordResponse response = client.search(name);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PlaceSearchFailedException();
            }
            return parseDocuments(response);
        } catch (RuntimeException exception) {
            if (exception instanceof PlaceSearchFailedException) {
                throw exception;
            }
            throw new PlaceSearchFailedException(exception);
        }
    }

    private List<PlaceSearchCandidate> parseDocuments(KakaoLocalKeywordClient.KakaoKeywordResponse response) {
        List<CandidateConversion> conversions = response.documents().stream().map(this::toCandidate).toList();
        List<PlaceSearchCandidate> candidates = conversions.stream()
                .map(CandidateConversion::candidate)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        if (response.documentCount() > 0 && candidates.isEmpty()) {
            long missingRequired = conversions.stream()
                    .filter(conversion -> conversion.exclusionReason() == ExclusionReason.MISSING_REQUIRED)
                    .count();
            long invalidPlaceUrl = conversions.stream()
                    .filter(conversion -> conversion.exclusionReason() == ExclusionReason.INVALID_PLACE_URL)
                    .count();
            int invalidDocument = response.documentCount() - response.documents().size();
            LOGGER.warn(
                    "kakao place search response excluded all documents: total={}, missingRequired={}, "
                            + "invalidPlaceUrl={}, invalidDocument={}",
                    response.documentCount(), missingRequired, invalidPlaceUrl, invalidDocument);
        }
        return candidates;
    }

    /** 도로명주소나 장소 링크가 없으면 등록에 쓸 수 없으므로 이 문서를 조용히 제외한다. */
    private CandidateConversion toCandidate(Map<String, Object> document) {
        String name = stringValue(document.get("place_name"));
        String placeUrl = stringValue(document.get("place_url"));
        String roadAddress = stringValue(document.get("road_address_name"));
        String phoneNumber = stringValue(document.get("phone"));
        String category = stringValue(document.get("category_name"));
        if (name == null || placeUrl == null || roadAddress == null) {
            return CandidateConversion.excluded(ExclusionReason.MISSING_REQUIRED);
        }
        Optional<URI> canonicalUrl = KakaoPlaceUrlPolicy.canonicalize(placeUrl)
                .filter(KakaoPlaceUrlPolicy::hasKakaoPlaceHost);
        if (canonicalUrl.isEmpty()) {
            return CandidateConversion.excluded(ExclusionReason.INVALID_PLACE_URL);
        }
        return CandidateConversion.included(new PlaceSearchCandidate(
                name, canonicalUrl.get().toString(), SeoulRoadAddressNormalizer.normalize(roadAddress), phoneNumber,
                category));
    }

    private String stringValue(Object value) {
        if (!(value instanceof String string)) {
            return null;
        }
        String normalized = string.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private enum ExclusionReason {
        NONE,
        MISSING_REQUIRED,
        INVALID_PLACE_URL
    }

    private record CandidateConversion(Optional<PlaceSearchCandidate> candidate, ExclusionReason exclusionReason) {
        private static CandidateConversion included(PlaceSearchCandidate candidate) {
            return new CandidateConversion(Optional.of(candidate), ExclusionReason.NONE);
        }

        private static CandidateConversion excluded(ExclusionReason reason) {
            return new CandidateConversion(Optional.empty(), reason);
        }
    }
}
