package com.masiton.orchestration.presentation.detail;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-CREATOR-DETAIL-002·003 유튜버 방문 맛집·근거 영상 조회를 실제 PostgreSQL과 MockMvc로 끝까지
 * 검증한다. Fixture는 각 테스트가 JdbcTemplate으로 직접 적재하고 다른 테스트가 만든 데이터에 의존하지
 * 않는다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("유튜버 방문 맛집·근거 영상 조회 API")
class CreatorVisitContentApiTest {

    private static final UUID SEED_REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.10-alpine")
                    .withDatabaseName("masiton")
                    .withUsername("masiton")
                    .withPassword("masiton_local");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("방문 맛집 목록은 계약된 필드와 기본 페이지로 응답한다")
    void 방문맛집조회_공개유효관계존재_계약필드와기본페이지를반환한다() throws Exception {
        // given
        UUID creatorId = insertCreator("PUBLIC", "ACTIVE", "AVAILABLE");
        UUID restaurantId = insertRestaurant("테스트 맛집", "PUBLIC", "ACTIVE");
        UUID videoId = insertVideo(creatorId, "테스트 영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE", OffsetDateTime.now());

        // when & then
        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(restaurantId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("테스트 맛집"))
                .andExpect(jsonPath("$.items[0].district").value("종로구"))
                .andExpect(jsonPath("$.items[0].category").value("한식"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.hasNext").value(false));
    }

    @Test
    @DisplayName("근거 영상 목록은 계약된 필드와 기본 페이지로 응답한다")
    void 근거영상조회_공개유효관계존재_계약필드와기본페이지를반환한다() throws Exception {
        // given
        UUID creatorId = insertCreator("PUBLIC", "ACTIVE", "AVAILABLE");
        UUID restaurantId = insertRestaurant("테스트 맛집2", "PUBLIC", "ACTIVE");
        UUID videoId = insertVideo(creatorId, "테스트 영상2", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE", OffsetDateTime.now());

        // when & then
        mockMvc.perform(get("/api/creators/{creatorId}/videos", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(videoId.toString()))
                .andExpect(jsonPath("$.items[0].title").value("테스트 영상2"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value("https://i.ytimg.com/" + videoId + ".jpg"))
                .andExpect(jsonPath("$.items[0].sourceUrl")
                        .value("https://www.youtube.com/watch?v=" + videoId))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.hasNext").value(false));
    }

    @Test
    @DisplayName("유효한 유튜버지만 방문 맛집 관계가 없으면 200과 빈 items를 반환한다")
    void 방문맛집조회_유효유튜버관계없음_200과빈items를반환한다() throws Exception {
        // given
        UUID creatorId = insertCreator("PUBLIC", "ACTIVE", "AVAILABLE");

        // when & then
        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(0));
    }

    @Test
    @DisplayName("유효한 유튜버지만 근거 영상 관계가 없으면 200과 빈 items를 반환한다")
    void 근거영상조회_유효유튜버관계없음_200과빈items를반환한다() throws Exception {
        // given
        UUID creatorId = insertCreator("PUBLIC", "ACTIVE", "AVAILABLE");

        // when & then
        mockMvc.perform(get("/api/creators/{creatorId}/videos", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(0));
    }

    @Test
    @DisplayName("범위 밖 유효 페이지는 200과 빈 items, 실제 totalElements를 반환한다")
    void 방문맛집조회_범위밖페이지_200과빈items및실제전체개수를반환한다() throws Exception {
        // given
        UUID creatorId = insertCreator("PUBLIC", "ACTIVE", "AVAILABLE");
        UUID restaurantId = insertRestaurant("범위밖맛집", "PUBLIC", "ACTIVE");
        UUID videoId = insertVideo(creatorId, "범위밖영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE", OffsetDateTime.now());

        // when & then
        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", creatorId).param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.page.number").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.hasNext").value(false));
    }

    @Test
    @DisplayName("size 10, 20, 50은 허용하고 그 외 값은 400 INVALID_FIELD_VALUE를 반환한다")
    void 방문맛집조회_size허용값검증_10과20과50은허용하고그외는400을반환한다() throws Exception {
        // given
        UUID creatorId = insertCreator("PUBLIC", "ACTIVE", "AVAILABLE");

        // when & then
        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", creatorId).param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(10));
        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", creatorId).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(50));
        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", creatorId).param("size", "30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("page가 0이거나 정수가 아니면 400 INVALID_FIELD_VALUE를 반환한다")
    void 근거영상조회_page오류값검증_0또는문자열은400을반환한다() throws Exception {
        // given
        UUID creatorId = insertCreator("PUBLIC", "ACTIVE", "AVAILABLE");

        // when & then
        mockMvc.perform(get("/api/creators/{creatorId}/videos", creatorId).param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
        mockMvc.perform(get("/api/creators/{creatorId}/videos", creatorId).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
    }

    @Test
    @DisplayName("정의되지 않은 쿼리 파라미터가 있으면 400 INVALID_REQUEST를 반환한다")
    void 방문맛집조회_정의되지않은쿼리파라미터_400INVALID_REQUEST를반환한다() throws Exception {
        // given
        UUID creatorId = insertCreator("PUBLIC", "ACTIVE", "AVAILABLE");

        // when & then
        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", creatorId).param("sort", "name"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("존재하지 않는 식별자는 두 API 모두 404 CREATOR_NOT_FOUND를 반환한다")
    void 조회_존재하지않는유튜버_두API모두404CREATOR_NOT_FOUND를반환한다() throws Exception {
        UUID missingCreatorId = UUID.randomUUID();

        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", missingCreatorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
        mockMvc.perform(get("/api/creators/{creatorId}/videos", missingCreatorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("비공개 유튜버는 존재 여부를 누설하지 않고 두 API 모두 404 CREATOR_NOT_FOUND를 반환한다")
    void 조회_비공개유튜버_두API모두404CREATOR_NOT_FOUND를반환한다() throws Exception {
        UUID creatorId = insertCreator("PRIVATE", "ACTIVE", "AVAILABLE");

        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", creatorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
        mockMvc.perform(get("/api/creators/{creatorId}/videos", creatorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
    }

    @Test
    @DisplayName("삭제된 유튜버는 존재 여부를 누설하지 않고 두 API 모두 404 CREATOR_NOT_FOUND를 반환한다")
    void 조회_삭제된유튜버_두API모두404CREATOR_NOT_FOUND를반환한다() throws Exception {
        UUID creatorId = insertCreator("PRIVATE", "DELETED", "AVAILABLE");

        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", creatorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
        mockMvc.perform(get("/api/creators/{creatorId}/videos", creatorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
    }

    @Test
    @DisplayName("외부 이용 불가 유튜버는 두 API 모두 404 CREATOR_NOT_FOUND를 반환한다")
    void 조회_외부이용불가유튜버_두API모두404CREATOR_NOT_FOUND를반환한다() throws Exception {
        UUID creatorId = insertCreator("PRIVATE", "ACTIVE", "UNAVAILABLE");

        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", creatorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
        mockMvc.perform(get("/api/creators/{creatorId}/videos", creatorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
    }

    @Test
    @DisplayName("UUID 형식이 아닌 식별자는 두 API 모두 400 INVALID_IDENTIFIER를 반환한다")
    void 조회_UUID형식아닌식별자_두API모두400INVALID_IDENTIFIER를반환한다() throws Exception {
        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
        mockMvc.perform(get("/api/creators/{creatorId}/videos", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("두 목록의 페이지 상태는 독립적이다: 방문 맛집 2페이지 조회가 근거 영상 1페이지 결과에 영향을 주지 않는다")
    void 조회_두목록의페이지상태_서로독립적이다() throws Exception {
        /*
         * 허용 크기가 10·20·50뿐이므로(pagination-contract.md) 방문 맛집에 2페이지를 만들려면
         * 11건이 필요하다. 근거 영상은 1건만 두어 다른 목록의 페이지 이동과 무관함을 확인한다.
         */
        UUID creatorId = insertCreator("PUBLIC", "ACTIVE", "AVAILABLE");
        UUID videoId = insertVideo(creatorId, "독립성검증영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        for (int index = 1; index <= 11; index++) {
            UUID restaurantId = insertRestaurant("독립성검증맛집" + index, "PUBLIC", "ACTIVE");
            insertVisit(restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE", OffsetDateTime.now());
        }

        // when & then: 1페이지는 10건과 다음 페이지 존재를, 2페이지는 남은 1건을 반환한다.
        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", creatorId)
                        .param("size", "10").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.page.hasNext").value(true));

        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", creatorId)
                        .param("size", "10").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.page.number").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(11))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.page.hasNext").value(false));

        mockMvc.perform(get("/api/creators/{creatorId}/videos", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(videoId.toString()))
                .andExpect(jsonPath("$.page.number").value(1));
    }

    private UUID insertRestaurant(String name, String publicationStatus, String lifecycleStatus) {
        UUID id = UUID.randomUUID();
        OffsetDateTime deletedAt = "DELETED".equals(lifecycleStatus) ? OffsetDateTime.now() : null;
        jdbcTemplate.update(
                "INSERT INTO restaurant "
                        + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number, publication_status, lifecycle_status, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, SEED_REGION_ID, SEED_FOOD_CATEGORY_ID, name, "KAKAO-" + id,
                "https://example.com/place/" + id, "서울특별시 종로구 테스트로 1", "02-1234-5678",
                publicationStatus, lifecycleStatus, deletedAt);
        return id;
    }

    private UUID insertCreator(String publicationStatus, String lifecycleStatus, String externalAvailabilityStatus) {
        UUID id = UUID.randomUUID();
        OffsetDateTime deletedAt = "DELETED".equals(lifecycleStatus) ? OffsetDateTime.now() : null;
        jdbcTemplate.update(
                "INSERT INTO creator "
                        + "(id, external_channel_id, channel_name, channel_url, publication_status, "
                        + "lifecycle_status, external_availability_status, external_status_checked_at, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, "UC-" + id, "테스트 채널", "https://www.youtube.com/channel/" + id,
                publicationStatus, lifecycleStatus, externalAvailabilityStatus, OffsetDateTime.now(), deletedAt);
        return id;
    }

    private UUID insertVideo(
            UUID creatorId, String title, String publicationStatus, String lifecycleStatus,
            String externalAvailabilityStatus) {
        UUID id = UUID.randomUUID();
        OffsetDateTime deletedAt = "DELETED".equals(lifecycleStatus) ? OffsetDateTime.now() : null;
        String publisherExternalChannelId = jdbcTemplate.queryForObject(
                "SELECT external_channel_id FROM creator WHERE id = ?", String.class, creatorId);
        jdbcTemplate.update(
                "INSERT INTO video "
                        + "(id, creator_id, external_video_id, publisher_external_channel_id, title, "
                        + "source_url, thumbnail_url, publication_status, lifecycle_status, "
                        + "external_availability_status, external_status_checked_at, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, creatorId, shortId("VID-"), publisherExternalChannelId, title,
                "https://www.youtube.com/watch?v=" + id, "https://i.ytimg.com/" + id + ".jpg",
                publicationStatus, lifecycleStatus, externalAvailabilityStatus, OffsetDateTime.now(), deletedAt);
        return id;
    }

    private void insertVisit(
            UUID restaurantId, UUID creatorId, UUID videoId, String publicationStatus, String lifecycleStatus,
            OffsetDateTime createdAt) {
        OffsetDateTime deletedAt = "DELETED".equals(lifecycleStatus) ? OffsetDateTime.now() : null;
        jdbcTemplate.update(
                "INSERT INTO visit "
                        + "(id, restaurant_id, creator_id, video_id, publication_status, lifecycle_status, "
                        + "created_at, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), restaurantId, creatorId, videoId, publicationStatus, lifecycleStatus,
                createdAt, deletedAt);
    }

    /** varchar(32) 컬럼(external_video_id)에 맞도록 UUID를 잘라 짧은 식별자를 만든다. */
    private String shortId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 20);
    }
}
