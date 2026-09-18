package com.masiton.restaurant.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore;
import com.masiton.restaurant.domain.model.LifecycleStatus;
import com.masiton.restaurant.domain.model.PublicationStatus;
import com.masiton.restaurant.domain.model.Restaurant;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** V19 상태·감사 테이블을 전용 JDBC Adapter로 접근한다. */
@Component
class JdbcRestaurantPlaceRevalidationStore implements RestaurantPlaceRevalidationStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcRestaurantPlaceRevalidationStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ClaimedRestaurant> claimDue(OffsetDateTime now, OffsetDateTime leaseUntil,
                                            String owner, int limit) {
        List<UUID> ids = jdbc.query(
                """
                SELECT r.id
                FROM restaurant r
                LEFT JOIN restaurant_kakao_revalidation s ON s.restaurant_id = r.id
                WHERE r.lifecycle_status = 'ACTIVE'
                  AND (
                    s.restaurant_id IS NULL
                    OR (s.status IN ('PENDING', 'RETRY_SCHEDULED', 'VERIFIED', 'AUTO_CORRECTED',
                                     'REVIEW_REQUIRED', 'MATCH_NOT_FOUND')
                        AND s.next_attempt_at <= ?)
                    OR (s.status = 'RUNNING' AND s.lease_expires_at <= ?)
                  )
                ORDER BY COALESCE(s.next_attempt_at, r.updated_at), r.id
                FOR UPDATE OF r SKIP LOCKED
                LIMIT ?
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class), now, now, limit);
        return ids.stream()
                .map(id -> claim(id, now, leaseUntil, owner))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<ClaimedRestaurant> claim(UUID restaurantId, OffsetDateTime now,
                                             OffsetDateTime leaseUntil, String owner) {
        UUID executionId = UUID.randomUUID();
        return jdbc.query(
                """
                WITH candidate AS (
                    SELECT r.id
                    FROM restaurant r
                    LEFT JOIN restaurant_kakao_revalidation s ON s.restaurant_id = r.id
                    WHERE r.id = ? AND r.lifecycle_status = 'ACTIVE'
                      AND (s.restaurant_id IS NULL OR s.status <> 'RUNNING' OR s.lease_expires_at <= ?)
                    FOR UPDATE OF r SKIP LOCKED
                ), claimed AS (
                    INSERT INTO restaurant_kakao_revalidation(
                        restaurant_id, status, attempt_count, lease_owner, lease_expires_at,
                        last_execution_id, next_attempt_at, updated_at
                    )
                    SELECT id, 'RUNNING', 1, ?, ?, ?, NULL, ?
                    FROM candidate
                    ON CONFLICT (restaurant_id) DO UPDATE SET
                        status = 'RUNNING',
                        attempt_count = restaurant_kakao_revalidation.attempt_count + 1,
                        lease_owner = EXCLUDED.lease_owner,
                        lease_expires_at = EXCLUDED.lease_expires_at,
                        last_execution_id = EXCLUDED.last_execution_id,
                        last_checked_at = NULL,
                        next_attempt_at = NULL,
                        last_reason_code = NULL,
                        last_error_code = NULL,
                        last_error_message = NULL,
                        updated_at = EXCLUDED.updated_at
                    RETURNING restaurant_id
                )
                SELECT r.*, s.attempt_count, s.last_execution_id, s.lease_owner
                FROM claimed c
                JOIN restaurant r ON r.id = c.restaurant_id
                JOIN restaurant_kakao_revalidation s ON s.restaurant_id = c.restaurant_id
                """,
                (rs, rowNum) -> claimed(rs), restaurantId, now, owner, leaseUntil, executionId, now)
                .stream().findFirst();
    }

    @Override
    public boolean apply(ClaimedRestaurant claimed, Decision decision, OffsetDateTime now) {
        int nextAttemptCount = resetsRetryBudget(decision.outcome()) ? 0 : claimed.attemptCount();
        int updated = jdbc.update(
                """
                UPDATE restaurant_kakao_revalidation
                SET status = ?, attempt_count = ?, next_attempt_at = ?, last_checked_at = ?, last_reason_code = ?,
                    last_error_code = ?, last_error_message = ?, lease_owner = NULL,
                    lease_expires_at = NULL, updated_at = ?
                WHERE restaurant_id = ? AND lease_owner = ? AND last_execution_id = ?
                  AND lease_expires_at > ?
                """,
                decision.outcome().name(), nextAttemptCount, decision.nextAttemptAt(), now, decision.reasonCode(),
                decision.errorCode(), decision.errorMessage(), now, claimed.restaurant().getId(),
                claimed.leaseOwner(), claimed.executionId(), now);
        if (updated != 1) {
            return false;
        }
        if (decision.correctedRestaurant() != null
                && !updateRestaurant(claimed, decision.correctedRestaurant(), now)) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
        jdbc.update(
                """
                INSERT INTO restaurant_kakao_revalidation_audit(
                    id, execution_id, restaurant_id, status, observed_values, previous_values,
                    applied_values, reason_code, error_code, error_message, checked_at, next_attempt_at
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), claimed.executionId(), claimed.restaurant().getId(), decision.outcome().name(),
                json(decision.observedValues()), json(previousValues(claimed.restaurant())),
                decision.correctedRestaurant() == null ? null : json(previousValues(decision.correctedRestaurant())),
                decision.reasonCode(), decision.errorCode(), decision.errorMessage(), now, decision.nextAttemptAt());
        return true;
    }

    private boolean resetsRetryBudget(Outcome outcome) {
        return switch (outcome) {
            case VERIFIED, AUTO_CORRECTED, REVIEW_REQUIRED, MATCH_NOT_FOUND -> true;
            case RETRY_SCHEDULED, RETRY_EXHAUSTED -> false;
        };
    }

    private boolean updateRestaurant(ClaimedRestaurant claimed, Restaurant restaurant, OffsetDateTime now) {
        return jdbc.update(
                """
                UPDATE restaurant
                SET name = ?, road_address = ?, phone_number = ?, latitude = ?, longitude = ?, updated_at = ?
                WHERE id = ? AND kakao_place_id = ? AND lifecycle_status = 'ACTIVE' AND updated_at = ?
                """,
                restaurant.getName(), restaurant.getRoadAddress(), restaurant.getPhoneNumber(),
                restaurant.getLatitude(), restaurant.getLongitude(), now,
                claimed.restaurant().getId(), claimed.restaurant().getKakaoPlaceId(),
                claimed.restaurant().getUpdatedAt()) == 1;
    }

    @Override
    public Optional<State> state(UUID restaurantId) {
        return jdbc.query(
                "SELECT restaurant_id, status, attempt_count, next_attempt_at, last_checked_at, "
                        + "last_reason_code, last_error_code FROM restaurant_kakao_revalidation WHERE restaurant_id = ?",
                (rs, rowNum) -> new State(rs.getObject("restaurant_id", UUID.class), rs.getString("status"),
                        rs.getInt("attempt_count"), rs.getObject("next_attempt_at", OffsetDateTime.class),
                        rs.getObject("last_checked_at", OffsetDateTime.class), rs.getString("last_reason_code"),
                        rs.getString("last_error_code")), restaurantId).stream().findFirst();
    }

    @Override
    public List<Audit> audits(UUID restaurantId, int limit) {
        return jdbc.query(
                """
                SELECT status, observed_values::text, previous_values::text, applied_values::text,
                       reason_code, error_code, checked_at, next_attempt_at
                FROM restaurant_kakao_revalidation_audit
                WHERE restaurant_id = ? ORDER BY checked_at DESC, id DESC LIMIT ?
                """,
                (rs, rowNum) -> new Audit(
                        Outcome.valueOf(rs.getString("status")), map(rs.getString("observed_values")),
                        map(rs.getString("previous_values")), map(rs.getString("applied_values")),
                        rs.getString("reason_code"), rs.getString("error_code"),
                        rs.getObject("checked_at", OffsetDateTime.class),
                        rs.getObject("next_attempt_at", OffsetDateTime.class)), restaurantId, limit);
    }

    private ClaimedRestaurant claimed(ResultSet rs) throws SQLException {
        return new ClaimedRestaurant(restaurant(rs), rs.getInt("attempt_count"),
                rs.getObject("last_execution_id", UUID.class), rs.getString("lease_owner"));
    }

    private Restaurant restaurant(ResultSet rs) throws SQLException {
        return new Restaurant(rs.getObject("id", UUID.class), rs.getObject("region_id", UUID.class),
                rs.getObject("food_category_id", UUID.class), rs.getString("name"), rs.getString("kakao_place_id"),
                rs.getString("kakao_place_url"), rs.getString("road_address"), rs.getString("detail_address"),
                rs.getString("phone_number"), rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"),
                PublicationStatus.valueOf(rs.getString("publication_status")),
                LifecycleStatus.valueOf(rs.getString("lifecycle_status")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("deleted_at", OffsetDateTime.class));
    }

    private Map<String, Object> previousValues(Restaurant restaurant) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", restaurant.getName());
        values.put("roadAddress", restaurant.getRoadAddress());
        values.put("phoneNumber", restaurant.getPhoneNumber());
        values.put("latitude", restaurant.getLatitude());
        values.put("longitude", restaurant.getLongitude());
        values.put("kakaoPlaceUrl", restaurant.getKakaoPlaceUrl());
        return values;
    }

    private String json(Map<String, Object> values) {
        if (values == null) return null;
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Kakao revalidation audit could not be serialized.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(String value) {
        if (value == null) return null;
        try {
            return objectMapper.readValue(value, Map.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Kakao revalidation audit could not be read.", exception);
        }
    }
}
