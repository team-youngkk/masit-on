package com.masiton.restaurant.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import com.masiton.restaurant.domain.model.Restaurant;

public interface RestaurantPlaceRevalidationStore {
    List<ClaimedRestaurant> claimDue(OffsetDateTime now, OffsetDateTime leaseUntil, String owner, int limit);
    Optional<ClaimedRestaurant> claim(UUID restaurantId, OffsetDateTime now, OffsetDateTime leaseUntil, String owner);
    boolean apply(ClaimedRestaurant claimed, Decision decision, OffsetDateTime now);
    Optional<State> state(UUID restaurantId);
    List<Audit> audits(UUID restaurantId, int limit);

    record ClaimedRestaurant(Restaurant restaurant, int attemptCount, UUID executionId, String leaseOwner) { }
    record Decision(Outcome outcome, Restaurant correctedRestaurant, Map<String, Object> observedValues,
                    String reasonCode, String errorCode, String errorMessage, OffsetDateTime nextAttemptAt) { }
    record State(UUID restaurantId, String status, int attemptCount, OffsetDateTime nextAttemptAt,
                 OffsetDateTime lastCheckedAt, String reasonCode, String errorCode) { }
    record Audit(Outcome outcome, Map<String, Object> observedValues, Map<String, Object> previousValues,
                 Map<String, Object> appliedValues, String reasonCode, String errorCode,
                 OffsetDateTime checkedAt, OffsetDateTime nextAttemptAt) { }
    enum Outcome { VERIFIED, AUTO_CORRECTED, REVIEW_REQUIRED, MATCH_NOT_FOUND, RETRY_SCHEDULED, RETRY_EXHAUSTED }
}
