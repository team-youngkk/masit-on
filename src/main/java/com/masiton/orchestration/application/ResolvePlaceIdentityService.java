package com.masiton.orchestration.application;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.masiton.orchestration.application.port.in.ResolvePlaceIdentityUseCase;
import com.masiton.restaurant.application.SeoulRoadAddressNormalizer;
import com.masiton.restaurant.application.port.in.SearchPlacesByNameUseCase;
import com.masiton.restaurant.application.port.out.PlaceSearchCandidate;

/**
 * {@code BR-AIEXTRACT-009} 장소 동일성 자동 확정을 수행한다. 상호명 완전일치와 도로명주소
 * 시·구 단위 일치를 함께 만족하는 Kakao 검색 결과가 정확히 1건일 때만 확정한다.
 */
@Service
class ResolvePlaceIdentityService implements ResolvePlaceIdentityUseCase {

    private static final String MATCHED_BY_NAME_AND_DISTRICT = "NAME_AND_DISTRICT";

    private final SearchPlacesByNameUseCase searchPlacesByName;

    ResolvePlaceIdentityService(SearchPlacesByNameUseCase searchPlacesByName) {
        this.searchPlacesByName = searchPlacesByName;
    }

    @Override
    public PlaceIdentityResult resolve(PlaceIdentityCommand command) {
        Objects.requireNonNull(command, "command");
        if (blank(command.restaurantName()) || blank(command.candidateAddress())) {
            return PlaceIdentityResult.notFound();
        }

        Optional<String> candidateDistrict = extractDistrict(command.candidateAddress());
        if (candidateDistrict.isEmpty()) {
            return PlaceIdentityResult.notFound();
        }

        String normalizedName = normalize(command.restaurantName());
        List<PlaceSearchCandidate> qualifying = searchPlacesByName.search(command.restaurantName()).stream()
                .filter(this::hasRequiredFields)
                .filter(candidate -> normalize(candidate.placeName()).equals(normalizedName))
                .filter(candidate -> matchesDistrict(candidate.roadAddress(), candidateDistrict.get()))
                .toList();

        if (qualifying.isEmpty()) {
            return PlaceIdentityResult.notFound();
        }
        if (qualifying.size() > 1) {
            return PlaceIdentityResult.ambiguous();
        }

        PlaceSearchCandidate matched = qualifying.get(0);
        return PlaceIdentityResult.confirmed(new ConfirmedPlace(
                matched.kakaoPlaceUrl(), matched.roadAddress(), MATCHED_BY_NAME_AND_DISTRICT,
                matched.placeCategory()));
    }

    private boolean hasRequiredFields(PlaceSearchCandidate candidate) {
        return !blank(candidate.kakaoPlaceUrl()) && !blank(candidate.placeName()) && !blank(candidate.roadAddress());
    }

    private boolean matchesDistrict(String roadAddress, String candidateDistrict) {
        return extractDistrict(roadAddress)
                .map(candidateDistrict::equals)
                .orElse(false);
    }

    private Optional<String> extractDistrict(String roadAddress) {
        if (blank(roadAddress)) {
            return Optional.empty();
        }
        return SeoulRoadAddressNormalizer.extractDistrict(SeoulRoadAddressNormalizer.normalize(roadAddress));
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
