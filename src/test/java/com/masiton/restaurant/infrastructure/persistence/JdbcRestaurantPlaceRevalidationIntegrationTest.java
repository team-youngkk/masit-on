package com.masiton.restaurant.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.masiton.restaurant.application.port.out.KakaoPlaceRevalidationPort;
import com.masiton.restaurant.application.port.out.VerifiedPlace;
import com.masiton.test.FullContextIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Import(JdbcRestaurantPlaceRevalidationIntegrationTest.RevalidationTestConfiguration.class)
@ResourceLock("shared-test-infrastructure")
@TestPropertySource(properties = {
        "masiton.restaurant.place-revalidation.enabled=true",
        "masiton.restaurant.place-revalidation.poll-interval=PT1H"
})
@DisplayName("Kakao 장소 재검증 stale 충돌 통합 검증")
class JdbcRestaurantPlaceRevalidationIntegrationTest extends FullContextIntegrationTest {

    private static final UUID MAPO_REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000014");
    private static final UUID KOREAN_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KakaoPlaceRevalidationPort kakao;

    @BeforeEach
    void setUp() {
        cleanupTransactionalState(jdbcTemplate);
    }

    @Test
    @DisplayName("동시 본문 변경은 409로 변환하고 상태·감사·맛집 보정을 저장하지 않는다")
    void 재검증_동시본문변경_409와부분저장없음() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId);
        when(kakao.verify(any(), any(), any())).thenAnswer(invocation -> {
            jdbcTemplate.update("UPDATE restaurant SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    "동시 변경 맛집", restaurantId);
            return KakaoPlaceRevalidationPort.Result.found(new VerifiedPlace(
                    "kakao-" + restaurantId, "Kakao 보정 맛집", "https://place.map.kakao.com/" + restaurantId,
                    "서울특별시 마포구 월드컵로 2", "02-0000-0001",
                    new BigDecimal("37.5665"), new BigDecimal("126.9780")));
        });

        mockMvc.perform(post("/api/admin/restaurants/{restaurantId}/place-revalidation", restaurantId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESTAURANT_PLACE_REVALIDATION_STALE"));

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM restaurant WHERE id = ?", String.class, restaurantId))
                .isEqualTo("동시 변경 맛집");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM restaurant_kakao_revalidation_audit WHERE restaurant_id = ?", Integer.class,
                restaurantId)).isZero();
        Map<String, Object> state = jdbcTemplate.queryForMap(
                "SELECT status, attempt_count FROM restaurant_kakao_revalidation WHERE restaurant_id = ?", restaurantId);
        assertThat(state).containsEntry("status", "RUNNING").containsEntry("attempt_count", 1);
    }

    private void insertRestaurant(UUID restaurantId) {
        jdbcTemplate.update(
                "INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number, latitude, longitude) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                restaurantId, MAPO_REGION_ID, KOREAN_CATEGORY_ID, "원본 맛집", "kakao-" + restaurantId,
                "https://place.map.kakao.com/" + restaurantId, "서울특별시 마포구 월드컵로 1", "02-0000-0000",
                new BigDecimal("37.5665"), new BigDecimal("126.9780"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RevalidationTestConfiguration {
        @Bean
        @Primary
        KakaoPlaceRevalidationPort kakaoPlaceRevalidationPort() {
            return mock(KakaoPlaceRevalidationPort.class);
        }
    }
}
