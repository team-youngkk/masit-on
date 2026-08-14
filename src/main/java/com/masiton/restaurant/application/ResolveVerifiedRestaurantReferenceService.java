package com.masiton.restaurant.application;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.masiton.restaurant.application.port.in.ResolveVerifiedRestaurantReferenceUseCase;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.application.port.out.PlaceVerificationPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.application.port.out.VerifiedPlace;

/** 외부 장소 동일성·주소·지역·대표 카테고리 판정을 Restaurant 경계에서 수행한다. */
@Service
class ResolveVerifiedRestaurantReferenceService implements ResolveVerifiedRestaurantReferenceUseCase {

    private static final Pattern SEOUL_ROAD_ADDRESS = Pattern.compile("^서울특별시\\s+([^\\s]+구)\\s+.+$");
    private static final Map<String, String> MENU_CATEGORY = Map.ofEntries(
            Map.entry("한식", "한식"), Map.entry("한식집", "한식"), Map.entry("한식당", "한식"),
            Map.entry("냉면", "한식"), Map.entry("물냉면", "한식"), Map.entry("비빔냉면", "한식"),
            Map.entry("국밥", "한식"), Map.entry("삼겹살", "한식"),
            Map.entry("중식", "중식"), Map.entry("중식집", "중식"), Map.entry("중국집", "중식"),
            Map.entry("일식", "일식"), Map.entry("일식집", "일식"), Map.entry("라멘", "일식"),
            Map.entry("스시", "일식"), Map.entry("초밥", "일식"),
            Map.entry("양식", "양식"), Map.entry("양식집", "양식"), Map.entry("이탈리안", "양식"),
            Map.entry("프렌치", "양식"), Map.entry("피자", "양식"),
            Map.entry("동남아", "동남아 음식"), Map.entry("동남아음식", "동남아 음식"),
            Map.entry("태국음식", "동남아 음식"), Map.entry("베트남음식", "동남아 음식"),
            Map.entry("인도음식", "인도·남아시아 음식"), Map.entry("인도·남아시아음식", "인도·남아시아 음식"),
            Map.entry("커리", "인도·남아시아 음식"),
            Map.entry("분식", "분식"), Map.entry("분식집", "분식"), Map.entry("김밥", "분식"),
            Map.entry("떡볶이", "분식"),
            Map.entry("카페", "카페·디저트"), Map.entry("디저트", "카페·디저트"),
            Map.entry("카페·디저트", "카페·디저트"), Map.entry("술집", "술집·주점"),
            Map.entry("주점", "술집·주점"), Map.entry("포차", "술집·주점"), Map.entry("기타", "기타"));

    private final PlaceVerificationPort placeVerification;
    private final RegionRepositoryPort regionRepository;
    private final FoodCategoryRepositoryPort foodCategoryRepository;

    ResolveVerifiedRestaurantReferenceService(PlaceVerificationPort placeVerification,
                                               RegionRepositoryPort regionRepository,
                                               FoodCategoryRepositoryPort foodCategoryRepository) {
        this.placeVerification = placeVerification;
        this.regionRepository = regionRepository;
        this.foodCategoryRepository = foodCategoryRepository;
    }

    @Override
    public Optional<VerifiedRestaurantReference> resolve(String restaurantName, String candidateAddress,
                                                         URI kakaoPlaceUrl, String menuExpression) {
        if (blank(restaurantName) || blank(candidateAddress) || kakaoPlaceUrl == null || blank(menuExpression)) {
            return Optional.empty();
        }
        Optional<VerifiedPlace> verifiedPlace = placeVerification.verify(restaurantName, kakaoPlaceUrl, null);
        if (verifiedPlace.isEmpty() || !complete(verifiedPlace.get())
                || !matches(restaurantName, verifiedPlace.get().name())
                || !matches(candidateAddress, verifiedPlace.get().roadAddress())) {
            return Optional.empty();
        }

        String categoryName = MENU_CATEGORY.get(normalize(menuExpression));
        if (categoryName == null) {
            return Optional.empty();
        }
        Matcher districtMatcher = SEOUL_ROAD_ADDRESS.matcher(verifiedPlace.get().roadAddress().trim());
        if (!districtMatcher.matches()) {
            return Optional.empty();
        }
        var region = regionRepository.findByName(districtMatcher.group(1))
                .filter(value -> value.isActive())
                .orElse(null);
        var foodCategory = foodCategoryRepository.findByName(categoryName)
                .filter(value -> value.isActive())
                .orElse(null);
        if (region == null || foodCategory == null) {
            return Optional.empty();
        }

        VerifiedPlace place = verifiedPlace.get();
        return Optional.of(new VerifiedRestaurantReference(
                region.getId(), foodCategory.getId(), place.name(), place.identityKey(), place.kakaoPlaceUrl(),
                place.roadAddress(), place.phoneNumber(), place.latitude(), place.longitude()));
    }

    private boolean matches(String left, String right) {
        return !blank(right) && normalize(left).equals(normalize(right));
    }

    private boolean complete(VerifiedPlace place) {
        return !blank(place.identityKey()) && !blank(place.name()) && !blank(place.kakaoPlaceUrl())
                && !blank(place.roadAddress()) && !blank(place.phoneNumber())
                && (place.latitude() == null) == (place.longitude() == null);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
