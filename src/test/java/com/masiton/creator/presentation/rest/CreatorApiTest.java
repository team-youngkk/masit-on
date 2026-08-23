package com.masiton.creator.presentation.rest;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-CREATOR-DISCOVERY-001. 이 클래스의 테스트는 creator 테이블에 아무 것도 적재하지 않으므로
 * 빈 목록 계약 검증과 쿼리 파라미터 검증이 서로의 데이터에 영향을 주지 않는다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@DisplayName("유튜버 필터 선택 목록 API")
class CreatorApiTest extends com.masiton.test.FullContextIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("공개 유튜버의 프로필 이미지 URL을 반환하고 미등록 값은 null로 유지한다")
    void 조회_프로필이미지등록여부혼재_값을반환하고null을유지한다() throws Exception {
        // given
        UUID withProfileImageId = UUID.randomUUID();
        UUID withoutProfileImageId = UUID.randomUUID();
        insertPublicCreator(
                withProfileImageId, "가나다 채널", "https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg");
        insertPublicCreator(withoutProfileImageId, "마바사 채널", null);

        // when & then
        mockMvc.perform(get("/api/creators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(withProfileImageId.toString()))
                .andExpect(jsonPath("$.items[0].channelName").value("가나다 채널"))
                .andExpect(jsonPath("$.items[0].profileImageUrl")
                        .value("https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg"))
                .andExpect(jsonPath("$.items[1].id").value(withoutProfileImageId.toString()))
                .andExpect(jsonPath("$.items[1].channelName").value("마바사 채널"))
                .andExpect(jsonPath("$.items[1].profileImageUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("등록된 공개 유튜버가 없으면 200과 빈 items를 반환한다")
    void 조회_등록된공개유튜버없음_200과빈items를반환한다() throws Exception {
        mockMvc.perform(get("/api/creators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("지원하지 않는 쿼리 파라미터가 있으면 400 INVALID_REQUEST를 반환한다")
    void 조회_지원하지않는쿼리파라미터존재_400INVALID_REQUEST를반환한다() throws Exception {
        mockMvc.perform(get("/api/creators").param("page", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    private void insertPublicCreator(UUID id, String channelName, String profileImageUrl) {
        String externalChannelId = "UC-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO creator "
                        + "(id, external_channel_id, channel_name, channel_url, profile_image_url, "
                        + "publication_status, lifecycle_status, external_availability_status, "
                        + "external_status_checked_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'PUBLIC', 'ACTIVE', 'AVAILABLE', ?)",
                id,
                externalChannelId,
                channelName,
                "https://www.youtube.com/channel/" + externalChannelId,
                profileImageUrl,
                OffsetDateTime.now());
    }
}
