package com.masiton.restaurant.application;

import java.net.URI;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.masiton.common.address.SeoulRoadAddressNormalizer;
import com.masiton.restaurant.application.port.in.ResolvePlacePhysicalReferenceUseCase;
import com.masiton.restaurant.application.port.out.PlaceVerificationPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.application.port.out.VerifiedPlace;
import com.masiton.restaurant.domain.model.Region;

/**
 * {@link ResolvePlacePhysicalReferenceUseCase}의 구현체다. {@link ResolveVerifiedRestaurantReferenceService}와
 * 같은 완전성 판정 규칙을 쓰되 대표 음식 카테고리는 다루지 않는다({@code BR-AIEXTRACT-010}은
 * 별도 use case가 담당한다).
 */
@Service
class ResolvePlacePhysicalReferenceService implements ResolvePlacePhysicalReferenceUseCase {

    private final PlaceVerificationPort placeVerification;
    private final RegionRepositoryPort regionRepository;

    ResolvePlacePhysicalReferenceService(PlaceVerificationPort placeVerification,
                                         RegionRepositoryPort regionRepository) {
        this.placeVerification = placeVerification;
        this.regionRepository = regionRepository;
    }

    @Override
    public Optional<VerifiedPlacePhysicalReference> resolve(String restaurantName, URI kakaoPlaceUrl) {
        if (blank(restaurantName) || kakaoPlaceUrl == null) {
            return Optional.empty();
        }
        Optional<VerifiedPlace> verifiedPlace = placeVerification.verify(restaurantName, kakaoPlaceUrl, null);
        if (verifiedPlace.isEmpty() || !complete(verifiedPlace.get())) {
            return Optional.empty();
        }
        VerifiedPlace place = verifiedPlace.get();
        Optional<String> district = SeoulRoadAddressNormalizer.extractDistrict(
                SeoulRoadAddressNormalizer.normalize(place.roadAddress()));
        if (district.isEmpty()) {
            return Optional.empty();
        }
        Region region = regionRepository.findByName(district.get()).filter(Region::isActive).orElse(null);
        if (region == null) {
            return Optional.empty();
        }
        return Optional.of(new VerifiedPlacePhysicalReference(
                region.getId(), place.name(), place.identityKey(), place.kakaoPlaceUrl(), place.roadAddress(),
                place.phoneNumber(), place.latitude(), place.longitude()));
    }

    private boolean complete(VerifiedPlace place) {
        return !blank(place.identityKey()) && !blank(place.name()) && !blank(place.kakaoPlaceUrl())
                && !blank(place.roadAddress()) && !blank(place.phoneNumber())
                && (place.latitude() == null) == (place.longitude() == null);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
