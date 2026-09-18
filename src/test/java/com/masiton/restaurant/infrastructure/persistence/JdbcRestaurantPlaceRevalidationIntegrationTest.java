package com.masiton.restaurant.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.masiton.restaurant.application.RestaurantPlaceRevalidationStaleException;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore.ClaimedRestaurant;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore.Decision;
import com.masiton.restaurant.domain.model.Restaurant;
import com.masiton.test.FullContextIntegrationTest;

@SpringBootTest
@ResourceLock("shared-test-infrastructure")
@DisplayName("Kakao 장소 재검증 stale 충돌 통합 검증")
class JdbcRestaurantPlaceRevalidationIntegrationTest extends FullContextIntegrationTest {

    private static final UUID MAPO_REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000014");
    private static final UUID KOREAN_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private JdbcRestaurantPlaceRevalidationStore store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        cleanupTransactionalState(jdbcTemplate);
    }

    @Test
    @DisplayName("동시 본문 변경은 rollback하고 상태·감사·맛집 보정을 저장하지 않는다")
    void 재검증_동시본문변경_rollback과부분저장없음() {
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ClaimedRestaurant claimed = store.claim(restaurantId, now, now.plusMinutes(5), "integration-test-owner")
                .orElseThrow();

        jdbcTemplate.update("UPDATE restaurant SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                "동시 변경 맛집", restaurantId);
        Restaurant corrected = new Restaurant(
                claimed.restaurant().getId(), claimed.restaurant().getRegionId(), claimed.restaurant().getFoodCategoryId(),
                "Kakao 보정 맛집", claimed.restaurant().getKakaoPlaceId(), claimed.restaurant().getKakaoPlaceUrl(),
                "서울특별시 마포구 월드컵로 2", claimed.restaurant().getDetailAddress(), "02-0000-0001",
                new BigDecimal("37.5665"), new BigDecimal("126.9780"),
                claimed.restaurant().getPublicationStatus(), claimed.restaurant().getLifecycleStatus(),
                claimed.restaurant().getCreatedAt(), claimed.restaurant().getUpdatedAt(), claimed.restaurant().getDeletedAt());
        Decision decision = new Decision(RestaurantPlaceRevalidationStore.Outcome.AUTO_CORRECTED, corrected,
                Map.of("kind", "FOUND"), "SAFE_FIELDS_CHANGED", null, null, now.plusDays(1));

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            store.apply(claimed, decision, now);
            return null;
        })).isInstanceOf(RestaurantPlaceRevalidationStaleException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM restaurant WHERE id = ?", String.class, restaurantId))
                .isEqualTo("동시 변경 맛집");
        assertThat(store.audits(restaurantId, 20)).isEmpty();
        assertThat(store.state(restaurantId)).get()
                .extracting(RestaurantPlaceRevalidationStore.State::status,
                        RestaurantPlaceRevalidationStore.State::attemptCount)
                .containsExactly("RUNNING", 1);
    }

    private void insertRestaurant(UUID restaurantId) {
        jdbcTemplate.update(
                "INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number, latitude, longitude) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                restaurantId, MAPO_REGION_ID, KOREAN_CATEGORY_ID, "원본 맛집", "kakao-" + restaurantId,
                "https://place.map.kakao.com/" + restaurantId, "서울특별시 마포구 월드컵로 1", "02-0000-0000",
                new BigDecimal("37.5665"), new BigDecimal("126.9780"));
    }
}
