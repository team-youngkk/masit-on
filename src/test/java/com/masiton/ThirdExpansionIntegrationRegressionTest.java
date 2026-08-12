package com.masiton;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.masiton.restaurant.application.port.out.CourseRouteFailureCategory;
import com.masiton.restaurant.application.port.out.CourseRouteProviderException;
import com.masiton.restaurant.application.port.out.CourseRouteProviderPort;
import com.masiton.restaurant.application.port.out.NaturalLanguageRateLimitPort;
import com.masiton.test.FullContextIntegrationTest;

/**
 * E3-T11의 교차 경계를 하나의 애플리케이션 컨텍스트에서 검증한다.
 * AI 확정 태그가 자연어·기존 공개 조회에 연결되는지와 코스 외부 실패가 공개 탐색을
 * 오염시키지 않는지를 PostgreSQL 상태와 MockMvc로 함께 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("3차 확장 Workstream 교차 통합 회귀")
class ThirdExpansionIntegrationRegressionTest extends FullContextIntegrationTest {

    private static final UUID MAPO_REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000014");
    private static final UUID KOREAN_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID SPICY_TAG_ID = UUID.fromString("30000000-0000-4000-8000-000000000007");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private NaturalLanguageRateLimitPort naturalLanguageRateLimitPort;

    @MockitoBean
    private CourseRouteProviderPort courseRouteProviderPort;

    @BeforeEach
    void cleanUpState() {
        jdbcTemplate.execute("TRUNCATE TABLE visit_tag, visit, video, creator, restaurant CASCADE");
        given(naturalLanguageRateLimitPort.tryAcquire(any())).willReturn(true);
    }

    @Test
    @DisplayName("AI 확정 태그는 자연어 검색과 기존 공개 맛집·유튜버·상세 조회에 함께 반영된다")
    void ai확정태그_자연어검색과기존공개조회에_같은공개상태로반영된다() throws Exception {
        // given
        Fixture fixture = insertPublicFixture(true);

        // when & then: WS-14 자연어 조회가 WS-15가 만든 공개 VisitTag를 기존 검색 Port로 읽는다.
        mockMvc.perform(post("/api/restaurants/natural-language-search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sentence\":\"매운맛 맛집\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interpretation.status").value("APPLIED"))
                .andExpect(jsonPath("$.interpretation.appliedConditions.tags[0]").value("TASTE_SPICY"))
                .andExpect(jsonPath("$.results.items[0].id").value(fixture.restaurantId().toString()));

        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(fixture.restaurantId().toString()))
                .andExpect(jsonPath("$.items[0].visitedBy[0].id").value(fixture.creatorId().toString()));
        mockMvc.perform(get("/api/creators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(fixture.creatorId().toString()));
        mockMvc.perform(get("/api/restaurants/{restaurantId}", fixture.restaurantId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fixture.restaurantId().toString()))
                .andExpect(jsonPath("$.contentStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.visitedBy[0].id").value(fixture.creatorId().toString()))
                .andExpect(jsonPath("$.videos[0].id").value(fixture.videoId().toString()));
    }

    @Test
    @DisplayName("코스 외부 실패 뒤에도 기존 공개 탐색 3종은 정상이고 영속 데이터는 증가하지 않는다")
    void 코스외부실패_기존공개탐색은정상이고_영속데이터는증가하지않는다() throws Exception {
        // given
        Fixture fixture = insertPublicFixture(false);
        UUID destinationId = insertRestaurant("도착 맛집");
        int restaurantCountBefore = count("restaurant");
        willThrow(new CourseRouteProviderException(CourseRouteFailureCategory.TIMEOUT))
                .given(courseRouteProviderPort).calculate(any());

        // when & then
        mockMvc.perform(post("/api/restaurants/course-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequest(fixture.restaurantId(), destinationId)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("COURSE_ROUTE_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.totalDistanceMeters").doesNotExist())
                .andExpect(jsonPath("$.totalDurationSeconds").doesNotExist())
                .andExpect(jsonPath("$.segments").doesNotExist());
        verify(courseRouteProviderPort).calculate(any());

        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mockMvc.perform(get("/api/creators"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/restaurants/{restaurantId}", fixture.restaurantId()))
                .andExpect(status().isOk());
        Assertions.assertThat(count("restaurant")).isEqualTo(restaurantCountBefore);
    }

    private Fixture insertPublicFixture(boolean withTag) {
        UUID restaurantId = insertRestaurant("매운 맛집");
        UUID creatorId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO creator (id, external_channel_id, channel_name, channel_url, "
                        + "external_status_checked_at) VALUES (?, ?, ?, ?, ?)",
                creatorId, "UC-" + creatorId, "테스트 채널", "https://example.com/channel/" + creatorId,
                OffsetDateTime.now());
        UUID videoId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO video (id, creator_id, external_video_id, publisher_external_channel_id, title, "
                        + "source_url, thumbnail_url, external_status_checked_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                videoId, creatorId, "VID-" + videoId.toString().substring(0, 20), "UC-" + creatorId,
                "테스트 영상", "https://example.com/video/" + videoId,
                "https://example.com/thumbnail/" + videoId, OffsetDateTime.now());
        UUID visitId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO visit (id, restaurant_id, creator_id, video_id) VALUES (?, ?, ?, ?)",
                visitId, restaurantId, creatorId, videoId);
        if (withTag) {
            jdbcTemplate.update(
                    "INSERT INTO visit_tag (id, visit_id, tag_definition_id, source, confidence, evidence, "
                            + "extractor_version) VALUES (?, ?, ?, 'AI_AUTO_CONFIRMED', 0.9500, "
                            + "'{\"type\":\"TIMESTAMP\",\"startMs\":1000,\"endMs\":2000}'::jsonb, 'P1/S1')",
                    UUID.randomUUID(), visitId, SPICY_TAG_ID);
        }
        return new Fixture(restaurantId, creatorId, videoId);
    }

    private UUID insertRestaurant(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number, latitude, longitude) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, MAPO_REGION_ID, KOREAN_CATEGORY_ID, name, "KAKAO-" + id,
                "https://example.com/place/" + id, "서울특별시 마포구 테스트로 1", "02-1234-5678",
                "37.566500", "126.978000");
        return id;
    }

    private String courseRequest(UUID startId, UUID destinationId) {
        return "{\"restaurantIds\":[\"" + startId + "\",\"" + destinationId + "\"]}";
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private record Fixture(UUID restaurantId, UUID creatorId, UUID videoId) {
    }
}
