package com.masiton.personal.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.personal.application.port.out.RecentRestaurantViewRepository;
import com.masiton.test.TestProfile;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestProfile
@Testcontainers
@DisplayName("최근 본 맛집 PostgreSQL 저장소")
class JdbcRecentRestaurantViewRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.10-alpine")
            .withDatabaseName("masiton")
            .withUsername("masiton")
            .withPassword("masiton_test");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RecentRestaurantViewRepository recentRestaurantViewRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("늦게 도착한 조회 기록은 최신 시각을 되돌리지 않고 회원별 최신 50건만 유지한다")
    void 최근조회기록_upsert지연요청_최신시각을보존하고최신50건만유지한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID regionId = jdbcTemplate.queryForObject(
                "SELECT id FROM region ORDER BY sort_order LIMIT 1", UUID.class);
        UUID foodCategoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM food_category ORDER BY sort_order LIMIT 1", UUID.class);
        createActiveMember(memberId);

        UUID recentlyViewedRestaurantId = createRestaurant(regionId, foodCategoryId, 0);
        Instant firstViewedAt = Instant.parse("2026-07-30T01:00:00Z");
        Instant delayedViewedAt = Instant.parse("2026-07-30T00:00:00Z");
        recentRestaurantViewRepository.upsert(memberId, recentlyViewedRestaurantId, firstViewedAt);
        recentRestaurantViewRepository.upsert(memberId, recentlyViewedRestaurantId, delayedViewedAt);

        UUID oldestAdditionalRestaurantId = null;
        UUID retainedAdditionalRestaurantId = null;

        // when: 나중에 기록된 50건을 더해 51건 중 가장 오래된 행 하나를 정리한다.
        for (int sequence = 1; sequence <= 50; sequence++) {
            UUID restaurantId = createRestaurant(regionId, foodCategoryId, sequence);
            recentRestaurantViewRepository.upsert(
                    memberId,
                    restaurantId,
                    delayedViewedAt.plusSeconds(sequence)
            );
            if (sequence == 1) {
                oldestAdditionalRestaurantId = restaurantId;
            }
            if (sequence == 2) {
                retainedAdditionalRestaurantId = restaurantId;
            }
        }

        // then
        assertThat(findLastViewedAt(memberId, recentlyViewedRestaurantId)).contains(firstViewedAt);
        assertThat(findLastViewedAt(memberId, oldestAdditionalRestaurantId)).isEmpty();
        assertThat(findLastViewedAt(memberId, retainedAdditionalRestaurantId)).isPresent();
        assertThat(recentViewCount(memberId)).isEqualTo(50);
    }

    private void createActiveMember(UUID memberId) {
        jdbcTemplate.update(
                "INSERT INTO member_account (id, email, password_hash, email_verified_at, status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                memberId,
                memberId + "@example.com",
                "test-password-hash",
                java.sql.Timestamp.from(Instant.parse("2026-07-30T00:00:00Z"))
        );
    }

    private UUID createRestaurant(UUID regionId, UUID foodCategoryId, int sequence) {
        UUID restaurantId = UUID.randomUUID();
        String externalPlaceId = "recent-view-" + UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, "
                        + "kakao_place_url, road_address, phone_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                restaurantId,
                regionId,
                foodCategoryId,
                "최근 본 맛집 " + sequence,
                externalPlaceId,
                "https://place.example.com/" + externalPlaceId,
                "서울시 테스트로 " + sequence,
                "02-1234-5678"
        );
        return restaurantId;
    }

    private Optional<Instant> findLastViewedAt(UUID memberId, UUID restaurantId) {
        return jdbcTemplate.query(
                        "SELECT last_viewed_at FROM recent_restaurant_view "
                                + "WHERE member_id = ? AND restaurant_id = ?",
                        (resultSet, rowNum) -> resultSet.getTimestamp("last_viewed_at").toInstant(),
                        memberId,
                        restaurantId)
                .stream()
                .findFirst();
    }

    private int recentViewCount(UUID memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM recent_restaurant_view WHERE member_id = ?",
                Integer.class,
                memberId
        );
    }
}
