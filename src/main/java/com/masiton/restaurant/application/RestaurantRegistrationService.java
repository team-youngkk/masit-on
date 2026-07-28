package com.masiton.restaurant.application;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.port.in.RestaurantRegistrationUseCase;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.application.port.out.PlaceVerificationPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.application.port.out.RestaurantRepositoryPort;
import com.masiton.restaurant.application.port.out.VerifiedPlace;
import com.masiton.restaurant.domain.model.FoodCategory;
import com.masiton.restaurant.domain.model.LifecycleStatus;
import com.masiton.restaurant.domain.model.PublicationStatus;
import com.masiton.restaurant.domain.model.Region;
import com.masiton.restaurant.domain.model.Restaurant;
import com.masiton.security.application.AcquiredConfirmationToken;
import com.masiton.security.application.ConfirmationTokenIssueCommand;
import com.masiton.security.application.IssuedConfirmationToken;
import com.masiton.security.application.port.in.ConfirmationTokenUseCase;
import com.masiton.security.domain.model.ConfirmationTokenResourceType;
import com.masiton.security.domain.model.ConfirmationTokenStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 미리보기에서는 외부 HTTP 호출을 트랜잭션 밖에서 끝내고, 확정에서는 저장된 Snapshot만 사용한다.
 */
@Service
public class RestaurantRegistrationService implements RestaurantRegistrationUseCase {

    private static final short SNAPSHOT_SCHEMA_VERSION = 1;
    private static final Pattern SEOUL_ROAD_ADDRESS = Pattern.compile("^서울특별시\\s+([^\\s]+구)\\s+.+$");
    private static final Pattern PHONE_NUMBER = Pattern.compile("^[0-9 +()\\-]{7,20}$");

    private final PlaceVerificationPort placeVerificationPort;
    private final RestaurantRepositoryPort restaurantRepository;
    private final RegionRepositoryPort regionRepository;
    private final FoodCategoryRepositoryPort foodCategoryRepository;
    private final ConfirmationTokenUseCase confirmationTokenUseCase;
    private final ObjectMapper objectMapper;

    public RestaurantRegistrationService(
            PlaceVerificationPort placeVerificationPort,
            RestaurantRepositoryPort restaurantRepository,
            RegionRepositoryPort regionRepository,
            FoodCategoryRepositoryPort foodCategoryRepository,
            ConfirmationTokenUseCase confirmationTokenUseCase,
            ObjectMapper objectMapper
    ) {
        this.placeVerificationPort = placeVerificationPort;
        this.restaurantRepository = restaurantRepository;
        this.regionRepository = regionRepository;
        this.foodCategoryRepository = foodCategoryRepository;
        this.confirmationTokenUseCase = confirmationTokenUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public RestaurantPreviewResult preview(RestaurantPreviewCommand command) {
        ValidatedPreviewInput input = validate(command);
        Optional<VerifiedPlace> verifiedPlace;
        try {
            verifiedPlace = placeVerificationPort.verify(input.name(), input.kakaoPlaceUrl(), input.phoneNumber());
        } catch (PlaceVerificationFailedException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        if (verifiedPlace.isEmpty()) {
            return new RestaurantPreviewResult(
                    RestaurantPreviewResult.Decision.REVIEW_REQUIRED, null, null, null, null);
        }

        RestaurantCandidateSnapshot snapshot;
        try {
            snapshot = buildSnapshot(input, verifiedPlace.get());
        } catch (PlaceVerificationFailedException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        Optional<Restaurant> existing = restaurantRepository.findByKakaoPlaceId(snapshot.kakaoPlaceId());
        if (existing.isPresent()) {
            return new RestaurantPreviewResult(
                    RestaurantPreviewResult.Decision.DUPLICATE,
                    null,
                    null,
                    toCandidate(snapshot, null),
                    toExisting(existing.get()));
        }

        IssuedConfirmationToken token = confirmationTokenUseCase.issue(new ConfirmationTokenIssueCommand(
                input.adminAccountId(),
                ConfirmationTokenResourceType.RESTAURANT,
                SNAPSHOT_SCHEMA_VERSION,
                snapshot.kakaoPlaceId(),
                serialize(snapshot)));
        return new RestaurantPreviewResult(
                RestaurantPreviewResult.Decision.READY,
                token.rawToken(),
                token.expiresAt(),
                toCandidate(snapshot, null),
                null);
    }

    @Override
    @Transactional
    public RestaurantCreationResult create(RestaurantCreateCommand command) {
        if (command == null || command.adminAccountId() == null) {
            throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_TOKEN);
        }
        AcquiredConfirmationToken token = confirmationTokenUseCase.acquire(
                command.confirmationToken(), command.adminAccountId(), ConfirmationTokenResourceType.RESTAURANT);
        if (token.isReplay()) {
            Restaurant existing = findResult(token.resultResourceId());
            RestaurantCandidateSnapshot snapshot = deserialize(token);
            if (token.status() == ConfirmationTokenStatus.DUPLICATE) {
                return new RestaurantCreationResult(toCandidate(snapshot, existing.getId()), false, true);
            }
            return new RestaurantCreationResult(toCandidate(snapshot, existing.getId()), false, false);
        }

        RestaurantCandidateSnapshot snapshot = deserialize(token);
        Optional<Restaurant> existing = restaurantRepository.findByKakaoPlaceId(snapshot.kakaoPlaceId());
        if (existing.isPresent()) {
            confirmationTokenUseCase.completeDuplicate(token.tokenId(), existing.get().getId());
            return new RestaurantCreationResult(toCandidate(existing.get()), false, true);
        }

        Restaurant restaurant = new Restaurant(
                UUID.randomUUID(),
                snapshot.regionId(),
                snapshot.foodCategoryId(),
                snapshot.name(),
                snapshot.kakaoPlaceId(),
                snapshot.kakaoPlaceUrl(),
                snapshot.roadAddress(),
                snapshot.detailAddress(),
                snapshot.phoneNumber(),
                PublicationStatus.PUBLIC,
                LifecycleStatus.ACTIVE,
                null,
                null,
                null);
        Optional<Restaurant> inserted = restaurantRepository.insertIfAbsent(restaurant);
        if (inserted.isEmpty()) {
            Restaurant concurrent = restaurantRepository.findByKakaoPlaceId(snapshot.kakaoPlaceId())
                    .orElseThrow(() -> new IllegalStateException("Concurrent restaurant result was not found."));
            confirmationTokenUseCase.completeDuplicate(token.tokenId(), concurrent.getId());
            return new RestaurantCreationResult(toCandidate(snapshot, concurrent.getId()), false, true);
        }
        confirmationTokenUseCase.completeCreated(token.tokenId(), inserted.get().getId());
        return new RestaurantCreationResult(toCandidate(snapshot, inserted.get().getId()), true, false);
    }

    private ValidatedPreviewInput validate(RestaurantPreviewCommand command) {
        if (command == null || command.adminAccountId() == null) {
            throw missing("adminAccountId");
        }
        String name = required(command.name(), "name", 100);
        URI kakaoPlaceUrl = kakaoPlaceUrl(command.kakaoPlaceUrl());
        required(command.roadAddress(), "roadAddress", 255);
        if (!command.roadAddress().trim().startsWith("서울특별시")) {
            throw invalid("roadAddress");
        }
        String detailAddress = optional(command.detailAddress(), "detailAddress", 200);
        String phoneNumber = required(command.phoneNumber(), "phoneNumber", 20);
        if (!PHONE_NUMBER.matcher(phoneNumber).matches()) {
            throw invalid("phoneNumber");
        }
        String category = required(command.category(), "category", 30);
        return new ValidatedPreviewInput(command.adminAccountId(), name, kakaoPlaceUrl, detailAddress, phoneNumber, category);
    }

    private RestaurantCandidateSnapshot buildSnapshot(ValidatedPreviewInput input, VerifiedPlace place) {
        verifyProviderValue(place.identityKey(), 64);
        verifyProviderValue(place.name(), 100);
        verifyProviderValue(place.kakaoPlaceUrl(), 2048);
        verifyProviderValue(place.roadAddress(), 255);
        verifyProviderValue(place.phoneNumber(), 20);
        String district = districtOf(place.roadAddress());
        Region region = regionRepository.findByName(district)
                .filter(Region::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_VERIFICATION_REQUIRED));
        FoodCategory category = foodCategoryRepository.findByName(input.category())
                .filter(FoodCategory::isActive)
                .orElseThrow(() -> invalid("category"));
        if (!PHONE_NUMBER.matcher(place.phoneNumber()).matches() || place.phoneNumber().length() > 20) {
            throw new PlaceVerificationFailedException();
        }
        return new RestaurantCandidateSnapshot(
                region.getId(),
                category.getId(),
                place.identityKey(),
                place.name(),
                district,
                category.getName(),
                place.kakaoPlaceUrl(),
                place.roadAddress(),
                input.detailAddress(),
                place.phoneNumber());
    }

    private void verifyProviderValue(String value, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new PlaceVerificationFailedException();
        }
    }

    private RestaurantCandidateSnapshot deserialize(AcquiredConfirmationToken token) {
        if (token.candidateSchemaVersion() != SNAPSHOT_SCHEMA_VERSION) {
            throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_TOKEN);
        }
        try {
            RestaurantCandidateSnapshot snapshot = objectMapper.readValue(
                    token.candidateSnapshot(), RestaurantCandidateSnapshot.class);
            if (!snapshot.kakaoPlaceId().equals(token.identityKey())) {
                throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_TOKEN);
            }
            return snapshot;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_TOKEN);
        }
    }

    private String serialize(RestaurantCandidateSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Restaurant confirmation snapshot could not be serialized.", exception);
        }
    }

    private Restaurant findResult(UUID id) {
        if (id == null) {
            throw new IllegalStateException("Completed confirmation token has no resource id.");
        }
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Completed restaurant result was not found."));
    }

    private String districtOf(String roadAddress) {
        Matcher matcher = SEOUL_ROAD_ADDRESS.matcher(roadAddress.trim());
        if (!matcher.matches()) {
            throw new PlaceVerificationFailedException();
        }
        return matcher.group(1);
    }

    private URI kakaoPlaceUrl(String value) {
        String normalized = required(value, "kakaoPlaceUrl", 2048);
        try {
            URI uri = URI.create(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"place.map.kakao.com".equalsIgnoreCase(uri.getHost())
                    || uri.getPath() == null
                    || uri.getPath().isBlank()) {
                throw invalid("kakaoPlaceUrl");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw invalid("kakaoPlaceUrl");
        }
    }

    private String required(String value, String field, int maxLength) {
        if (value == null) {
            throw missing(field);
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw invalid(field);
        }
        return normalized;
    }

    private String optional(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw invalid(field);
        }
        return normalized;
    }

    private BusinessException missing(String field) {
        return new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, field + " is required.");
    }

    private BusinessException invalid(String field) {
        return new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field + " is invalid.");
    }

    private RestaurantCandidate toCandidate(RestaurantCandidateSnapshot snapshot, UUID id) {
        return new RestaurantCandidate(
                id,
                snapshot.name(),
                snapshot.district(),
                snapshot.category(),
                snapshot.roadAddress(),
                snapshot.detailAddress(),
                snapshot.phoneNumber(),
                snapshot.kakaoPlaceUrl());
    }

    private RestaurantCandidate toCandidate(Restaurant restaurant) {
        return new RestaurantCandidate(
                restaurant.getId(),
                restaurant.getName(),
                null,
                null,
                restaurant.getRoadAddress(),
                restaurant.getDetailAddress(),
                restaurant.getPhoneNumber(),
                restaurant.getKakaoPlaceUrl());
    }

    private ExistingRestaurant toExisting(Restaurant restaurant) {
        return new ExistingRestaurant(restaurant.getId(), restaurant.getName(), restaurant.getRoadAddress());
    }

    private record ValidatedPreviewInput(
            UUID adminAccountId,
            String name,
            URI kakaoPlaceUrl,
            String detailAddress,
            String phoneNumber,
            String category) {
    }

    private record RestaurantCandidateSnapshot(
            UUID regionId,
            UUID foodCategoryId,
            String kakaoPlaceId,
            String name,
            String district,
            String category,
            String kakaoPlaceUrl,
            String roadAddress,
            String detailAddress,
            String phoneNumber) {
    }
}
