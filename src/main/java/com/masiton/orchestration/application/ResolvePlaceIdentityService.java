package com.masiton.orchestration.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.masiton.common.address.SeoulRoadAddressNormalizer;
import com.masiton.orchestration.application.port.in.ResolvePlaceIdentityUseCase;
import com.masiton.orchestration.application.port.out.PlaceIdentityMatchingPolicy;
import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase;
import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase.MappingOutcome;
import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase.MappingResolution;
import com.masiton.restaurant.application.port.in.SearchPlacesByNameUseCase;
import com.masiton.restaurant.application.port.out.PlaceSearchCandidate;

/**
 * {@code BR-AIEXTRACT-009} 장소 동일성 자동 확정을 수행한다. 상호명 완전일치와 도로명주소
 * 시·구 단위 일치를 함께 만족하는 Kakao 검색 결과를 우선하고, 그 결과가 없을 때만 제한된
 * 상호명 포함·카테고리 근거 경로를 사용한다.
 */
@Service
class ResolvePlaceIdentityService implements ResolvePlaceIdentityUseCase {

    private static final String MATCHED_BY_NAME_AND_DISTRICT = "NAME_AND_DISTRICT";
    private static final String MATCHED_BY_NAME_CONTAINMENT_AND_DISTRICT_AND_CATEGORY =
            "NAME_CONTAINMENT_AND_DISTRICT_AND_CATEGORY";
    private static final Pattern BRANCH_SUFFIX = Pattern.compile(
            "(?i)(?:\\s+|\\()(?:(?:본점|본관|별관|지점)|\\d+호점)\\)?$");
    private static final Pattern COMPACT_BRANCH_SUFFIX = Pattern.compile(
            "(?i)(?:본점|본관|별관|지점|\\d+호점)$");

    private final SearchPlacesByNameUseCase searchPlacesByName;
    private final LookupFoodCategoryMappingUseCase lookupFoodCategoryMapping;
    private final PlaceIdentityMatchingPolicy placeIdentityMatchingPolicy;

    ResolvePlaceIdentityService(SearchPlacesByNameUseCase searchPlacesByName,
                                LookupFoodCategoryMappingUseCase lookupFoodCategoryMapping,
                                PlaceIdentityMatchingPolicy placeIdentityMatchingPolicy) {
        this.searchPlacesByName = searchPlacesByName;
        this.lookupFoodCategoryMapping = lookupFoodCategoryMapping;
        this.placeIdentityMatchingPolicy = placeIdentityMatchingPolicy;
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
        List<PlaceSearchCandidate> candidates = searchCandidates(command.restaurantName());
        List<PlaceSearchCandidate> exactMatches = candidates.stream()
                .filter(this::hasRequiredFields)
                .filter(candidate -> normalize(candidate.placeName()).equals(normalizedName))
                .filter(candidate -> matchesDistrict(candidate.roadAddress(), candidateDistrict.get()))
                .toList();

        if (!exactMatches.isEmpty()) {
            return toResult(exactMatches, MATCHED_BY_NAME_AND_DISTRICT);
        }

        if (!placeIdentityMatchingPolicy.relaxedMatchingEnabled()) {
            return PlaceIdentityResult.notFound();
        }

        String baseName = stripBranchSuffix(command.restaurantName());
        if (!blank(baseName) && !baseName.equals(command.restaurantName().strip())) {
            candidates = mergeCandidates(candidates, searchCandidates(baseName));
        }
        List<PlaceSearchCandidate> relaxedMatches = candidates.stream()
                .filter(this::hasRelaxedRequiredFields)
                .filter(candidate -> matchesDistrict(candidate.roadAddress(), candidateDistrict.get()))
                .filter(candidate -> isNameContainmentMatch(candidate.placeName(), normalizedName))
                .filter(candidate -> hasSameCategoryEvidence(candidate, command.menuExpression()))
                .toList();
        return toResult(relaxedMatches, MATCHED_BY_NAME_CONTAINMENT_AND_DISTRICT_AND_CATEGORY);
    }

    private List<PlaceSearchCandidate> searchCandidates(String restaurantName) {
        return mergeCandidates(List.of(), searchPlacesByName.search(restaurantName));
    }

    private List<PlaceSearchCandidate> mergeCandidates(List<PlaceSearchCandidate> initial,
                                                       List<PlaceSearchCandidate> additional) {
        Map<String, PlaceSearchCandidate> candidates = new LinkedHashMap<>();
        for (PlaceSearchCandidate candidate : initial) {
            addCandidate(candidates, candidate);
        }
        for (PlaceSearchCandidate candidate : additional) {
            addCandidate(candidates, candidate);
        }
        return List.copyOf(candidates.values());
    }

    private void addCandidate(Map<String, PlaceSearchCandidate> candidates, PlaceSearchCandidate candidate) {
        if (candidate != null) {
            String key = blank(candidate.kakaoPlaceUrl())
                    ? normalize(candidate.placeName()) + "|" + normalize(candidate.roadAddress())
                    : candidate.kakaoPlaceUrl();
            candidates.putIfAbsent(key, candidate);
        }
    }

    private String stripBranchSuffix(String restaurantName) {
        String stripped = BRANCH_SUFFIX.matcher(restaurantName).replaceFirst("").strip();
        if (stripped.equals(restaurantName)) {
            stripped = COMPACT_BRANCH_SUFFIX.matcher(restaurantName).replaceFirst("").strip();
        }
        return stripped;
    }

    private PlaceIdentityResult toResult(List<PlaceSearchCandidate> qualifying, String matchedBy) {
        if (qualifying.isEmpty()) {
            return PlaceIdentityResult.notFound();
        }
        if (qualifying.size() > 1) {
            return PlaceIdentityResult.ambiguous();
        }

        PlaceSearchCandidate matched = qualifying.get(0);
        return PlaceIdentityResult.confirmed(new ConfirmedPlace(
                matched.kakaoPlaceUrl(), matched.roadAddress(), matchedBy, matched.placeCategory()));
    }

    private boolean hasRequiredFields(PlaceSearchCandidate candidate) {
        return candidate != null
                && !blank(candidate.kakaoPlaceUrl())
                && !blank(candidate.placeName())
                && !blank(candidate.roadAddress());
    }

    private boolean hasRelaxedRequiredFields(PlaceSearchCandidate candidate) {
        return hasRequiredFields(candidate) && !blank(candidate.placeCategory());
    }

    private boolean isNameContainmentMatch(String candidateName, String normalizedName) {
        String normalizedCandidateName = normalize(candidateName);
        return !normalizedName.isEmpty()
                && !normalizedCandidateName.equals(normalizedName)
                && (normalizedCandidateName.contains(normalizedName)
                || normalizedName.contains(normalizedCandidateName));
    }

    private boolean hasSameCategoryEvidence(PlaceSearchCandidate candidate, String menuExpression) {
        if (blank(menuExpression)) {
            return false;
        }

        MappingResolution kakaoResolution = lookupFoodCategoryMapping
                .resolveByKakaoPlaceCategory(candidate.placeCategory());
        MappingResolution menuResolution = lookupFoodCategoryMapping.resolveByMenuExpression(menuExpression);
        return isMatched(kakaoResolution)
                && isMatched(menuResolution)
                && sameFoodCategory(kakaoResolution, menuResolution);
    }

    private boolean isMatched(MappingResolution resolution) {
        return resolution != null && resolution.outcome() == MappingOutcome.MATCHED && resolution.match() != null;
    }

    private boolean sameFoodCategory(MappingResolution kakaoResolution, MappingResolution menuResolution) {
        return kakaoResolution.match().foodCategoryId() != null
                && menuResolution.match().foodCategoryId() != null
                && kakaoResolution.match().foodCategoryId().equals(menuResolution.match().foodCategoryId());
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
