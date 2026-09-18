package com.masiton.restaurant.application;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.masiton.common.web.BusinessException;
import com.masiton.restaurant.application.port.in.RestaurantPlaceRevalidationUseCase;
import com.masiton.restaurant.application.port.out.KakaoPlaceRevalidationPort;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore.Decision;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore.Outcome;
import com.masiton.restaurant.application.port.out.VerifiedPlace;
import com.masiton.restaurant.domain.model.Restaurant;

@Service
public class RestaurantPlaceRevalidationService implements RestaurantPlaceRevalidationUseCase {

    private static final Logger log = LoggerFactory.getLogger(RestaurantPlaceRevalidationService.class);
    private static final Pattern DISTRICT = Pattern.compile("^서울특별시\\s+([^\\s]+구)\\s+.*$");

    private final RestaurantPlaceRevalidationStore store;
    private final KakaoPlaceRevalidationPort kakao;
    private final RestaurantPlaceRevalidationClaimService claimService;
    private final RestaurantPlaceRevalidationPersistenceService persistence;
    private final RestaurantPlaceRevalidationProperties properties;
    private final Clock clock;
    private final AtomicBoolean polling = new AtomicBoolean();

    public RestaurantPlaceRevalidationService(RestaurantPlaceRevalidationStore store,
                                              KakaoPlaceRevalidationPort kakao,
                                              RestaurantPlaceRevalidationClaimService claimService,
                                              RestaurantPlaceRevalidationPersistenceService persistence,
                                              RestaurantPlaceRevalidationProperties properties,
                                              @Qualifier("restaurantPlaceRevalidationClock") Clock clock) {
        this.store = store;
        this.kakao = kakao;
        this.claimService = claimService;
        this.persistence = persistence;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Optional<Result> run(UUID restaurantId) {
        if (!properties.isEnabled()) {
            throw new BusinessException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "RESTAURANT_PLACE_REVALIDATION_DISABLED",
                    "Restaurant place revalidation is disabled.");
        }
        if (restaurantId == null) {
            return Optional.empty();
        }
        return claimService.claim(restaurantId).map(this::verifyOutsideTransaction);
    }

    @Override
    public void poll() {
        if (!properties.isEnabled() || !polling.compareAndSet(false, true)) {
            return;
        }
        try {
            claimService.claimDue().forEach(this::verifyOutsideTransaction);
        } finally {
            polling.set(false);
        }
    }

    private Result verifyOutsideTransaction(RestaurantPlaceRevalidationStore.ClaimedRestaurant claimed) {
        Restaurant restaurant = claimed.restaurant();
        KakaoPlaceRevalidationPort.Result result;
        try {
            result = kakao.verify(restaurant.getKakaoPlaceId(), restaurant.getName(),
                    URI.create(restaurant.getKakaoPlaceUrl()));
        } catch (RuntimeException exception) {
            result = KakaoPlaceRevalidationPort.Result.of(KakaoPlaceRevalidationPort.Kind.EXTERNAL_FAILURE);
        }
        Decision decision = classify(claimed, result);
        boolean applied = persistence.apply(claimed, decision, now());
        if (!applied) {
            log.warn("Restaurant place revalidation result discarded because the lease was lost: restaurantId={}, executionId={}",
                    restaurant.getId(), claimed.executionId());
            return new Result(restaurant.getId(), "STALE_DISCARDED", null);
        }
        log.info("Restaurant place revalidation completed: restaurantId={}, outcome={}",
                restaurant.getId(), decision.outcome());
        return new Result(restaurant.getId(), decision.outcome().name(), decision.nextAttemptAt());
    }

    private Decision classify(RestaurantPlaceRevalidationStore.ClaimedRestaurant claimed,
                              KakaoPlaceRevalidationPort.Result result) {
        return switch (result.kind()) {
            case NOT_FOUND -> decision(Outcome.MATCH_NOT_FOUND, result, "KAKAO_PLACE_NOT_FOUND", null, null);
            case PLACE_ID_MISMATCH -> decision(Outcome.REVIEW_REQUIRED, result, "KAKAO_PLACE_ID_MISMATCH", null, null);
            case URL_MISMATCH -> decision(Outcome.REVIEW_REQUIRED, result, "KAKAO_PLACE_URL_MISMATCH", null, null);
            case IDENTITY_AMBIGUOUS -> decision(Outcome.REVIEW_REQUIRED, result, "IDENTITY_AMBIGUOUS", null, null);
            case QUOTA_EXCEEDED -> retry(claimed, "HTTP_429");
            case TIMEOUT -> retry(claimed, "TIMEOUT");
            case EXTERNAL_FAILURE -> retry(claimed, "HTTP_5XX");
            case FOUND -> classifyFound(result.place(), claimed.restaurant());
        };
    }

    private Decision classifyFound(VerifiedPlace observed, Restaurant current) {
        String currentDistrict = district(current.getRoadAddress());
        String observedDistrict = district(observed.roadAddress());
        if (currentDistrict == null || observedDistrict == null || !currentDistrict.equals(observedDistrict)) {
            return decision(Outcome.REVIEW_REQUIRED, KakaoPlaceRevalidationPort.Result.found(observed),
                    "DISTRICT_CHANGED", null, null);
        }
        Restaurant corrected = corrected(current, observed);
        return corrected == null
                ? decision(Outcome.VERIFIED, KakaoPlaceRevalidationPort.Result.found(observed), "NO_CHANGE", null, null)
                : decision(Outcome.AUTO_CORRECTED, KakaoPlaceRevalidationPort.Result.found(observed),
                        "SAFE_FIELDS_CHANGED", corrected, null);
    }

    private Decision retry(RestaurantPlaceRevalidationStore.ClaimedRestaurant claimed, String errorCode) {
        if (claimed.attemptCount() >= properties.getMaxAttempts()) {
            return new Decision(Outcome.RETRY_EXHAUSTED, null, Map.of("errorCode", errorCode),
                    "RETRY_EXHAUSTED", null, null, null);
        }
        long multiplier = 1L << Math.min(20, Math.max(0, claimed.attemptCount() - 1));
        Duration delay = properties.getFirstBackoff().multipliedBy(multiplier);
        if (delay.compareTo(properties.getMaxBackoff()) > 0) {
            delay = properties.getMaxBackoff();
        }
        return new Decision(Outcome.RETRY_SCHEDULED, null, Map.of("errorCode", errorCode), null,
                errorCode, "Kakao Local revalidation failed", now().plus(delay));
    }

    private Decision decision(Outcome outcome, KakaoPlaceRevalidationPort.Result result, String reasonCode,
                              Restaurant corrected, OffsetDateTime nextAttemptAt) {
        if (nextAttemptAt == null && outcome != Outcome.RETRY_EXHAUSTED) {
            nextAttemptAt = now().plus(properties.getRevalidationInterval());
        }
        return new Decision(outcome, corrected, observedValues(result), reasonCode, null, null, nextAttemptAt);
    }

    private Restaurant corrected(Restaurant current, VerifiedPlace observed) {
        String phone = observed.phoneNumber() == null ? current.getPhoneNumber() : observed.phoneNumber();
        BigDecimal latitude = observed.latitude() != null && observed.longitude() != null
                ? observed.latitude() : current.getLatitude();
        BigDecimal longitude = observed.latitude() != null && observed.longitude() != null
                ? observed.longitude() : current.getLongitude();
        if (Objects.equals(current.getName(), observed.name())
                && Objects.equals(current.getRoadAddress(), observed.roadAddress())
                && Objects.equals(current.getPhoneNumber(), phone)
                && same(current.getLatitude(), latitude)
                && same(current.getLongitude(), longitude)) {
            return null;
        }
        return new Restaurant(current.getId(), current.getRegionId(), current.getFoodCategoryId(), observed.name(),
                current.getKakaoPlaceId(), current.getKakaoPlaceUrl(), observed.roadAddress(), current.getDetailAddress(),
                phone, latitude, longitude, current.getPublicationStatus(), current.getLifecycleStatus(),
                current.getCreatedAt(), current.getUpdatedAt(), current.getDeletedAt());
    }

    private Map<String, Object> observedValues(KakaoPlaceRevalidationPort.Result result) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("kind", result.kind().name());
        if (result.place() != null) {
            VerifiedPlace place = result.place();
            values.put("name", place.name());
            values.put("roadAddress", place.roadAddress());
            values.put("phoneNumber", place.phoneNumber());
            values.put("latitude", place.latitude());
            values.put("longitude", place.longitude());
            values.put("kakaoPlaceUrl", place.kakaoPlaceUrl());
        }
        return values;
    }

    private String district(String address) {
        var matcher = DISTRICT.matcher(address == null ? "" : address.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private boolean same(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    @Override
    public Optional<RestaurantPlaceRevalidationStore.State> state(UUID restaurantId) {
        return store.state(restaurantId);
    }

    @Override
    public List<RestaurantPlaceRevalidationStore.Audit> audits(UUID restaurantId, int limit) {
        return store.audits(restaurantId, Math.min(100, Math.max(1, limit)));
    }
}
