package com.masiton.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase;
import com.masiton.common.web.BusinessException;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryPort;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@DisplayName("관리자 중복 태그 병합")
class TagDefinitionMergeIntegrationTest extends com.masiton.test.FullContextIntegrationTest {
    private static final UUID ADMIN_ID = UUID.fromString("32000000-0000-4000-8000-000000000066");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ManageTagDefinitionsUseCase definitions;
    @Autowired ManageVisitTagsUseCase visitTags;
    @Autowired ActiveTagDictionaryPort dictionary;

    @BeforeEach
    void setUp() {
        jdbc.update("""
                INSERT INTO member_account(id, email, password_hash, status, role, email_verified_at)
                VALUES (?, 'tag-merge-admin@example.com', 'hash', 'ACTIVE', 'ADMIN', now())
                """, ADMIN_ID);
        definitions.create(new ManageTagDefinitionsUseCase.CreateCommand(
                "OCCASION_MERGE_SOURCE", "OCCASION", "가족 잔치", List.of("가족 행사")));
        definitions.create(new ManageTagDefinitionsUseCase.CreateCommand(
                "OCCASION_MERGE_TARGET", "OCCASION", "가족 모임", List.of("가족 외식")));
        definitions.create(new ManageTagDefinitionsUseCase.CreateCommand(
                "OCCASION_MERGE_FINAL", "OCCASION", "모임 장소", List.of("회식 장소")));
    }

    @Test
    @Transactional
    @DisplayName("미리보기와 같은 병합은 연결을 이전하고 중복 근거와 자연어 별칭을 보존한다")
    void 병합_이전과중복제거와감사를원자적으로보존한다() throws Exception {
        UUID movedVisit = insertVisit("moved");
        UUID duplicateVisit = insertVisit("duplicate");
        UUID movedSourceId = insertVisitTag(movedVisit, "OCCASION_MERGE_SOURCE", "AI_AUTO_CONFIRMED");
        UUID duplicateSourceId = insertVisitTag(duplicateVisit, "OCCASION_MERGE_SOURCE", "ADMIN_OVERRIDE");
        UUID existingTargetId = insertVisitTag(duplicateVisit, "OCCASION_MERGE_TARGET", "ADMIN_OVERRIDE");

        JsonNode preview = preview("OCCASION_MERGE_SOURCE", "OCCASION_MERGE_TARGET");
        mvc.perform(post("/api/admin/tag-definitions/OCCASION_MERGE_SOURCE/merge")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetCode":"OCCASION_MERGE_TARGET","expectedSourceVersion":0,
                                 "expectedTargetVersion":0,"previewFingerprint":"%s","reason":"중복 정의 통합"}
                                """.formatted(preview.get("previewFingerprint").asText())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.affectedVisitCount").value(2))
                .andExpect(jsonPath("$.movedVisitTagCount").value(1))
                .andExpect(jsonPath("$.deduplicatedVisitTagCount").value(1))
                .andExpect(jsonPath("$.sourceVersion").value(1))
                .andExpect(jsonPath("$.mergedAt").isNotEmpty());

        UUID targetId = definitionId("OCCASION_MERGE_TARGET");
        assertThat(jdbc.queryForObject("SELECT tag_definition_id FROM visit_tag WHERE id=?", UUID.class,
                movedSourceId)).isEqualTo(targetId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_tag WHERE id=?", Integer.class,
                duplicateSourceId)).isZero();
        assertThat(jdbc.queryForObject("SELECT id FROM visit_tag WHERE visit_id=? AND tag_definition_id=?",
                UUID.class, duplicateVisit, targetId)).isEqualTo(existingTargetId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_tag_merge_provenance", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition_merge", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition_audit WHERE action='DEPRECATE'",
                Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM tag_definition WHERE tag_code='OCCASION_MERGE_SOURCE'",
                String.class)).isEqualTo("DEPRECATED");

        ActiveTagDictionaryPort.TagDefinition target = dictionary.getActiveTagDictionary().definitions().stream()
                .filter(value -> value.code().equals("OCCASION_MERGE_TARGET"))
                .findFirst().orElseThrow();
        assertThat(target.terms()).contains("가족 잔치", "가족 행사", "가족 모임", "가족 외식");

        mvc.perform(post("/api/admin/tag-definitions/OCCASION_MERGE_SOURCE/status")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"status\":\"ACTIVE\",\"reason\":\"복원 시도\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_DEFINITION_MERGE_CONFLICT"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/tag-definitions/OCCASION_MERGE_SOURCE")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":1,"displayName":"바뀐 용어","aliases":[],"reason":"수정 시도"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_DEFINITION_MERGE_CONFLICT"));

        JsonNode chainPreview = preview("OCCASION_MERGE_TARGET", "OCCASION_MERGE_FINAL");
        mvc.perform(post("/api/admin/tag-definitions/OCCASION_MERGE_TARGET/merge")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetCode":"OCCASION_MERGE_FINAL","expectedSourceVersion":0,
                                 "expectedTargetVersion":0,"previewFingerprint":"%s","reason":"최종 정의로 통합"}
                                """.formatted(chainPreview.get("previewFingerprint").asText())))
                .andExpect(status().isOk());
        ActiveTagDictionaryPort.TagDefinition finalTarget = dictionary.getActiveTagDictionary().definitions().stream()
                .filter(value -> value.code().equals("OCCASION_MERGE_FINAL"))
                .findFirst().orElseThrow();
        assertThat(finalTarget.terms()).contains("가족 잔치", "가족 행사", "가족 모임", "가족 외식", "모임 장소", "회식 장소");

        jdbc.update("DELETE FROM member_account WHERE id = ?", ADMIN_ID);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition_merge WHERE merged_by_member_id IS NULL",
                Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("미리보기 뒤 연결이 바뀌면 stale 병합을 거절하고 부분 변경을 남기지 않는다")
    void 병합_stale미리보기_전체롤백한다() throws Exception {
        JsonNode preview = preview("OCCASION_MERGE_SOURCE", "OCCASION_MERGE_TARGET");
        insertVisitTag(insertVisit("late"), "OCCASION_MERGE_SOURCE", "ADMIN_OVERRIDE");

        mvc.perform(post("/api/admin/tag-definitions/OCCASION_MERGE_SOURCE/merge")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetCode":"OCCASION_MERGE_TARGET","expectedSourceVersion":0,
                                 "expectedTargetVersion":0,"previewFingerprint":"%s","reason":"오래된 미리보기"}
                                """.formatted(preview.get("previewFingerprint").asText())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_DEFINITION_MERGE_CONFLICT"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition_merge", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_tag_merge_provenance", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM tag_definition WHERE tag_code='OCCASION_MERGE_SOURCE'",
                String.class)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("자기 병합과 유형 불일치는 400이고 비활성 대상과 비관리자는 거절한다")
    void 병합_잘못된대상과권한_계약오류를반환한다() throws Exception {
        mvc.perform(get("/api/admin/tag-definitions/OCCASION_MERGE_SOURCE/merge-preview")
                        .param("targetCode", "OCCASION_MERGE_SOURCE")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
        mvc.perform(get("/api/admin/tag-definitions/OCCASION_MERGE_SOURCE/merge-preview")
                        .param("targetCode", "MENU_NAENGMYEON")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
        mvc.perform(post("/api/admin/tag-definitions/OCCASION_MERGE_SOURCE/merge")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetCode":"MENU_NAENGMYEON","expectedSourceVersion":0,
                                 "expectedTargetVersion":0,"previewFingerprint":"%s","reason":"유형 오류"}
                                """.formatted("a".repeat(64))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
        definitions.changeStatus("OCCASION_MERGE_TARGET",
                new ManageTagDefinitionsUseCase.StatusCommand(0L, "DEPRECATED", "대상 종료"), ADMIN_ID.toString());
        mvc.perform(get("/api/admin/tag-definitions/OCCASION_MERGE_SOURCE/merge-preview")
                        .param("targetCode", "OCCASION_MERGE_TARGET")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_DEFINITION_MERGE_CONFLICT"));
        mvc.perform(get("/api/admin/tag-definitions/OCCASION_MERGE_SOURCE/merge-preview")
                        .param("targetCode", "OCCASION_MERGE_TARGET")
                        .with(user("member").authorities(() -> "MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("같은 미리보기의 동시 병합은 한 요청만 성공한다")
    void 병합_동시요청_하나만성공한다() throws Exception {
        JsonNode preview = preview("OCCASION_MERGE_SOURCE", "OCCASION_MERGE_TARGET");
        String body = """
                {"targetCode":"OCCASION_MERGE_TARGET","expectedSourceVersion":0,
                 "expectedTargetVersion":0,"previewFingerprint":"%s","reason":"동시 병합 검증"}
                """.formatted(preview.get("previewFingerprint").asText());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> request = () -> {
                ready.countDown();
                start.await();
                return mvc.perform(post("/api/admin/tag-definitions/OCCASION_MERGE_SOURCE/merge")
                                .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> first = executor.submit(request);
            Future<Integer> second = executor.submit(request);
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition_merge", Integer.class)).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("병합과 같은 방문의 관리자 태그 교체가 경합하면 오래된 요청을 충돌 처리한다")
    void 병합_방문태그교체경합_한요청만반영한다() throws Exception {
        UUID visitId = insertVisit("replace-race");
        insertVisitTag(visitId, "OCCASION_MERGE_SOURCE", "ADMIN_OVERRIDE");
        UUID restaurantId = jdbc.queryForObject("SELECT restaurant_id FROM visit WHERE id = ?",
                UUID.class, visitId);
        String expectedVersion = visitTags.list(restaurantId.toString()).items().getFirst().version();
        ManageTagDefinitionsUseCase.MergePreview preview = definitions.previewMerge(
                "OCCASION_MERGE_SOURCE", "OCCASION_MERGE_TARGET");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> merge = executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    definitions.merge("OCCASION_MERGE_SOURCE", new ManageTagDefinitionsUseCase.MergeCommand(
                            "OCCASION_MERGE_TARGET", 0L, 0L, preview.previewFingerprint(),
                            "관리자 교체와 경합"), ADMIN_ID.toString());
                    return "MERGE_OK";
                } catch (BusinessException exception) {
                    return exception.code();
                }
            });
            Future<String> replace = executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    visitTags.replace(restaurantId.toString(), visitId.toString(),
                            new ManageVisitTagsUseCase.Change(expectedVersion,
                                    List.of("OCCASION_MERGE_FINAL"), "병합 전 관리자 교체"),
                            ADMIN_ID.toString());
                    return "REPLACE_OK";
                } catch (BusinessException exception) {
                    return exception.code();
                }
            });

            ready.await();
            start.countDown();
            List<String> outcomes = List.of(merge.get(10, TimeUnit.SECONDS),
                    replace.get(10, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(value -> value.endsWith("_OK"))).hasSize(1);
            assertThat(outcomes.stream().filter(value -> value.endsWith("CONFLICT")
                    || value.endsWith("CONCURRENT_UPDATE"))).hasSize(1);
        }

        List<String> stored = visitTags.list(restaurantId.toString()).items().getFirst().tags().stream()
                .map(ManageVisitTagsUseCase.Tag::code).toList();
        assertThat(stored).isIn(List.of("OCCASION_MERGE_TARGET"), List.of("OCCASION_MERGE_FINAL"));
    }

    private JsonNode preview(String sourceCode, String targetCode) throws Exception {
        String body = mvc.perform(get("/api/admin/tag-definitions/{code}/merge-preview", sourceCode)
                        .param("targetCode", targetCode)
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private UUID definitionId(String code) {
        return jdbc.queryForObject("SELECT id FROM tag_definition WHERE tag_code=?", UUID.class, code);
    }

    private UUID insertVisitTag(UUID visitId, String code, String source) {
        UUID id = UUID.randomUUID();
        String evidence = source.equals("AI_AUTO_CONFIRMED")
                ? "{\"type\":\"TIMESTAMP\",\"startMs\":0,\"endMs\":1000}"
                : "{\"type\":\"ADMIN_OVERRIDE\"}";
        jdbc.update("""
                INSERT INTO visit_tag(id, visit_id, tag_definition_id, source, confidence, evidence, extractor_version)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """, id, visitId, definitionId(code), source,
                source.equals("AI_AUTO_CONFIRMED") ? new java.math.BigDecimal("0.9000") : null,
                evidence, source.equals("AI_AUTO_CONFIRMED") ? "test-model" : null);
        return id;
    }

    private UUID insertVisit(String suffix) {
        UUID creatorId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID regionId = jdbc.queryForObject("SELECT id FROM region ORDER BY sort_order LIMIT 1", UUID.class);
        UUID categoryId = jdbc.queryForObject("SELECT id FROM food_category ORDER BY sort_order LIMIT 1", UUID.class);
        jdbc.update("INSERT INTO creator(id, external_channel_id, channel_name, channel_url, external_status_checked_at) "
                        + "VALUES (?, ?, '채널', 'https://example.com/channel', now())",
                creatorId, "merge-channel-" + suffix);
        jdbc.update("INSERT INTO restaurant(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number) VALUES (?, ?, ?, '맛집', ?, 'https://example.com/place', "
                        + "'서울특별시', '02-0000-0000')",
                restaurantId, regionId, categoryId, "merge-place-" + suffix);
        jdbc.update("INSERT INTO video(id, creator_id, external_video_id, publisher_external_channel_id, title, "
                        + "source_url, thumbnail_url, external_status_checked_at) VALUES (?, ?, ?, ?, '영상', "
                        + "'https://example.com/video', 'https://example.com/thumb', now())",
                videoId, creatorId, "merge-video-" + suffix, "merge-channel-" + suffix);
        jdbc.update("INSERT INTO visit(id, restaurant_id, creator_id, video_id) VALUES (?, ?, ?, ?)",
                visitId, restaurantId, creatorId, videoId);
        return visitId;
    }
}
