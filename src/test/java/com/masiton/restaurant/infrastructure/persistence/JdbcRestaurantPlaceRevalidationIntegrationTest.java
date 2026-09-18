package com.masiton.restaurant.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.restaurant.application.RestaurantPlaceRevalidationStaleException;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore.ClaimedRestaurant;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore.Decision;
import com.masiton.restaurant.domain.model.LifecycleStatus;
import com.masiton.restaurant.domain.model.PublicationStatus;
import com.masiton.restaurant.domain.model.Restaurant;

@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
@DisplayName("Kakao 장소 재검증 stale 충돌 통합 검증")
class JdbcRestaurantPlaceRevalidationIntegrationTest {

    private static final KeyPair JWT_KEY_PAIR = generateJwtKeyPair();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.10-alpine")
            .withDatabaseName("masiton")
            .withUsername("masiton")
            .withPassword("masiton_test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8-alpine")
            .withExposedPorts(6379);

    static {
        System.setProperty("JWT_KEY_ID", "stale-integration-test");
        System.setProperty("JWT_PRIVATE_KEY_PEM", pem("PRIVATE KEY", JWT_KEY_PAIR.getPrivate().getEncoded()));
        System.setProperty("JWT_PUBLIC_KEY_PEM", pem("PUBLIC KEY", JWT_KEY_PAIR.getPublic().getEncoded()));
    }

    @DynamicPropertySource
    static void dependencyProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static final UUID MAPO_REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000014");
    private static final UUID KOREAN_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private JdbcRestaurantPlaceRevalidationStore store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("동시 본문 변경은 rollback하고 상태·감사·맛집 보정을 저장하지 않는다")
    void 재검증_동시본문변경_rollback과부분저장없음() {
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId);
        OffsetDateTime claimNow = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM restaurant WHERE id = ?", OffsetDateTime.class, restaurantId);
        OffsetDateTime updatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM restaurant WHERE id = ?", OffsetDateTime.class, restaurantId);
        Restaurant claimedRestaurant = new Restaurant(
                restaurantId, MAPO_REGION_ID, KOREAN_CATEGORY_ID, "원본 맛집", "kakao-" + restaurantId,
                "https://place.map.kakao.com/" + restaurantId, "서울특별시 마포구 월드컵로 1", null,
                "02-0000-0000", new BigDecimal("37.5665"), new BigDecimal("126.9780"),
                PublicationStatus.PUBLIC, LifecycleStatus.ACTIVE, createdAt, updatedAt, null);
        UUID executionId = UUID.randomUUID();
        String owner = "integration-test-owner";
        jdbcTemplate.update(
                "INSERT INTO restaurant_kakao_revalidation "
                        + "(restaurant_id, status, attempt_count, lease_owner, lease_expires_at, "
                        + "last_execution_id, next_attempt_at) VALUES (?, 'RUNNING', 1, ?, ?, ?, NULL)",
                restaurantId, owner, claimNow.plusMinutes(5), executionId);
        ClaimedRestaurant claimed = new ClaimedRestaurant(claimedRestaurant, 1, executionId, owner);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

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
                        + "road_address, phone_number, latitude, longitude, publication_status, lifecycle_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLIC', 'ACTIVE')",
                restaurantId, MAPO_REGION_ID, KOREAN_CATEGORY_ID, "원본 맛집", "kakao-" + restaurantId,
                "https://place.map.kakao.com/" + restaurantId, "서울특별시 마포구 월드컵로 1", "02-0000-0000",
                new BigDecimal("37.5665"), new BigDecimal("126.9780"));
    }

    private static KeyPair generateJwtKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN %s-----\n%s\n-----END %s-----".formatted(
                type, Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded), type);
    }
}
