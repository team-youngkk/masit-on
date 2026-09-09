package com.masiton.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase;
import com.masiton.ai.application.TagTermNormalizer;
import com.masiton.common.web.BusinessException;

@AutoConfigureMockMvc
@DisplayName("관리자 태그 정의 조회와 생성")
class TagDefinitionIntegrationTest extends com.masiton.test.FullContextIntegrationTest {
    private static final UUID ADMIN_ID = UUID.fromString("32000000-0000-4000-8000-000000000001");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ManageTagDefinitionsUseCase tagDefinitions;

    @BeforeEach
    void cleanUpState() {
        cleanupTransactionalState(jdbc);
        jdbc.update("""
                INSERT INTO member_account(id, email, password_hash, status, role, email_verified_at)
                VALUES (?, 'tag-admin@example.com', 'test-hash', 'ACTIVE', 'ADMIN', now())
                """, ADMIN_ID);
        jdbc.update("""
                INSERT INTO tag_definition(id, tag_code, tag_type, display_name, aliases, status, source)
                VALUES ('30000000-0000-4000-8000-000000000001', 'MENU_NAENGMYEON', 'MENU', '냉면', '[]'::jsonb,
                        'ACTIVE', 'SEED')
                ON CONFLICT (tag_code) DO UPDATE SET status = 'ACTIVE', display_name = '냉면'
                """);
        jdbc.update("""
                INSERT INTO tag_definition_term(id, tag_definition_id, term_kind, normalized_term)
                SELECT '31000000-0000-4000-8000-000000000001', id, 'DISPLAY_NAME', '냉면'
                  FROM tag_definition WHERE tag_code = 'MENU_NAENGMYEON'
                ON CONFLICT (normalized_term) DO NOTHING
                """);
    }

    @Test
    @DisplayName("관리자는 표시명과 별칭을 수정하고 비활성화하며 변경 이력을 조회한다")
    void 관리_수정과상태전이_버전과감사를보존한다() throws Exception {
        tagDefinitions.create(new ManageTagDefinitionsUseCase.CreateCommand(
                "OCCASION_FAMILY", "OCCASION", "가족 식사", List.of("가족 외식")));

        mvc.perform(put("/api/admin/tag-definitions/OCCASION_FAMILY")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"displayName":"가족 모임","aliases":["가족 외식","온 가족"],"reason":"검색 용어 보정"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.displayName").value("가족 모임"));
        mvc.perform(post("/api/admin/tag-definitions/OCCASION_FAMILY/status")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"expectedVersion\":1,\"status\":\"DEPRECATED\",\"reason\":\"운영 종료\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("DEPRECATED"));
        mvc.perform(get("/api/admin/tag-definitions/OCCASION_FAMILY/history")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].action").value("DEPRECATE"))
                .andExpect(jsonPath("$.items[1].before.displayName").value("가족 식사"));

        assertThatThrownBy(() -> jdbc.update("UPDATE tag_definition_audit SET reason = '변조'"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM tag_definition_audit"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        jdbc.update("DELETE FROM member_account WHERE id = ?", ADMIN_ID);
        assertThat(jdbc.queryForObject("SELECT changed_by_member_id FROM tag_definition_audit LIMIT 1", UUID.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition_audit", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("낡은 버전과 중복 용어 수정은 409이며 정의와 감사에 부분 변경을 남기지 않는다")
    void 관리_동시수정과용어충돌_409와원자성을보장한다() throws Exception {
        tagDefinitions.create(new ManageTagDefinitionsUseCase.CreateCommand(
                "OCCASION_FAMILY", "OCCASION", "가족 식사", List.of()));
        String body = """
                {"expectedVersion":1,"displayName":"냉면","aliases":[],"reason":"잘못된 버전"}
                """;
        mvc.perform(put("/api/admin/tag-definitions/OCCASION_FAMILY")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_DEFINITION_VERSION_CONFLICT"));
        mvc.perform(put("/api/admin/tag-definitions/OCCASION_FAMILY")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("\"expectedVersion\":1", "\"expectedVersion\":0")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAG_TERM_ALREADY_EXISTS"));

        assertThat(jdbc.queryForObject("SELECT display_name FROM tag_definition WHERE tag_code = 'OCCASION_FAMILY'", String.class)).isEqualTo("가족 식사");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition_audit", Integer.class)).isZero();
    }

    @Test
    @DisplayName("수정 필수 별칭 누락과 미정의 필드는 400이며 정의와 감사를 변경하지 않는다")
    void 관리_별칭누락과미정의필드_400과원자성을보장한다() throws Exception {
        tagDefinitions.create(new ManageTagDefinitionsUseCase.CreateCommand(
                "OCCASION_FAMILY", "OCCASION", "가족 식사", List.of("가족 외식")));

        for (String body : List.of(
                "{\"expectedVersion\":0,\"displayName\":\"가족 모임\",\"reason\":\"보정\"}",
                "{\"expectedVersion\":0,\"displayName\":\"가족 모임\",\"aliases\":[],\"reason\":\"보정\",\"code\":\"MENU_FAKE\"}")) {
            mvc.perform(put("/api/admin/tag-definitions/OCCASION_FAMILY")
                            .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/admin/tag-definitions/OCCASION_FAMILY/status")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"status\":\"DEPRECATED\",\"reason\":\"보정\",\"type\":\"MENU\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(jdbc.queryForObject("SELECT display_name FROM tag_definition WHERE tag_code = 'OCCASION_FAMILY'", String.class)).isEqualTo("가족 식사");
        assertThat(jdbc.queryForObject("SELECT aliases::text FROM tag_definition WHERE tag_code = 'OCCASION_FAMILY'", String.class)).isEqualTo("[\"가족 외식\"]");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition_audit", Integer.class)).isZero();
    }

    @Test
    @DisplayName("같은 버전의 동시 내용 수정은 하나만 성공하고 감사도 한 건만 남는다")
    void 관리_동일버전동시수정_한건만성공한다() throws Exception {
        tagDefinitions.create(new ManageTagDefinitionsUseCase.CreateCommand(
                "OCCASION_FAMILY", "OCCASION", "가족 식사", List.of()));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var commands = List.of(
                    new ManageTagDefinitionsUseCase.UpdateCommand(0L, "가족 모임", List.of("온 가족"), "첫 변경"),
                    new ManageTagDefinitionsUseCase.UpdateCommand(0L, "단체 가족", List.of("가족 나들이"), "둘째 변경"));
            var jobs = commands.stream().map(command -> executor.submit(() -> {
                start.await();
                try {
                    tagDefinitions.update("OCCASION_FAMILY", command, ADMIN_ID.toString());
                    return "OK";
                } catch (BusinessException exception) {
                    return exception.code();
                }
            })).toList();
            start.countDown();
            assertThat(List.of(jobs.get(0).get(), jobs.get(1).get()))
                    .containsExactlyInAnyOrder("OK", "TAG_DEFINITION_VERSION_CONFLICT");
        }
        assertThat(jdbc.queryForObject("SELECT version FROM tag_definition WHERE tag_code = 'OCCASION_FAMILY'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition_audit", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 정의의 용어 교환 수정은 교착 없이 충돌로 수렴한다")
    void 관리_서로다른정의용어교환_교착없이충돌한다() throws Exception {
        tagDefinitions.create(new ManageTagDefinitionsUseCase.CreateCommand(
                "ATMOSPHERE_REVIEW_SWAP_ALPHA", "ATMOSPHERE", "리뷰 교환 알파", List.of()));
        tagDefinitions.create(new ManageTagDefinitionsUseCase.CreateCommand(
                "ATMOSPHERE_REVIEW_SWAP_BETA", "ATMOSPHERE", "리뷰 교환 베타", List.of()));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var jobs = List.of(
                    executor.submit(() -> updateAfterStart(
                            start, "ATMOSPHERE_REVIEW_SWAP_ALPHA", "리뷰 교환 베타")),
                    executor.submit(() -> updateAfterStart(
                            start, "ATMOSPHERE_REVIEW_SWAP_BETA", "리뷰 교환 알파")));
            start.countDown();

            assertThat(List.of(jobs.get(0).get(), jobs.get(1).get()))
                    .containsOnly("TAG_TERM_ALREADY_EXISTS");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition_audit", Integer.class)).isZero();
    }

    @Test
    @DisplayName("매우 큰 양수 페이지는 목록과 이력에서 200 빈 목록을 반환한다")
    void 관리_매우큰양수페이지_빈목록을반환한다() throws Exception {
        mvc.perform(get("/api/admin/tag-definitions/management?page=50000000&size=50")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/admin/tag-definitions/MENU_NAENGMYEON/history?page=50000000&size=50")
                        .with(user(ADMIN_ID.toString()).authorities(() -> "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("ADMIN은 활성 목록을 조회하고 새 정의와 정규화 용어를 원자적으로 생성한다")
    void 요청_ADMIN_목록조회와생성에성공한다() throws Exception {
        mvc.perform(get("/api/admin/tag-definitions").with(user("admin").authorities(() -> "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[?(@.code == 'MENU_NAENGMYEON')]").isNotEmpty());

        mvc.perform(post("/api/admin/tag-definitions").with(user("admin").authorities(() -> "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"OCCASION_FAMILY","type":"OCCASION","displayName":"ＡＢＣ 가족",
                                 "aliases":["가족　외식"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.source").value("MANUAL_OVERRIDE"));

        assertThat(jdbc.queryForList("""
                SELECT term_kind || ':' || normalized_term
                  FROM tag_definition_term t
                  JOIN tag_definition d ON d.id = t.tag_definition_id
                 WHERE d.tag_code = 'OCCASION_FAMILY'
                 ORDER BY term_kind DESC
                """, String.class)).containsExactly("DISPLAY_NAME:abc 가족", "ALIAS:가족 외식");
    }

    @Test
    @DisplayName("정규화 용어가 기존 정의와 겹치면 409이고 부분 정의를 남기지 않는다")
    void 생성_기존정규화용어중복_409와원자성을보장한다() throws Exception {
        String request = """
                {"code":"MENU_COLD_NOODLES","type":"MENU","displayName":" 냉면 ","aliases":[]}
                """;

        mvc.perform(post("/api/admin/tag-definitions").with(user("admin").authorities(() -> "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_TERM_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM tag_definition WHERE tag_code = 'MENU_COLD_NOODLES'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("같은 정규화 용어의 동시 생성은 한 건만 성공하고 다른 요청은 409로 수렴한다")
    void 생성_동일용어동시요청_한건만성공한다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var commands = List.of(
                    new ManageTagDefinitionsUseCase.CreateCommand(
                            "OCCASION_FAMILY", "OCCASION", "가족 식사", List.of()),
                    new ManageTagDefinitionsUseCase.CreateCommand(
                            "OCCASION_FAMILY_DINNER", "OCCASION", " 가족　식사 ", List.of()));
            var jobs = commands.stream().map(command -> executor.submit(() -> {
                start.await();
                try {
                    tagDefinitions.create(command);
                    return "CREATED";
                } catch (BusinessException exception) {
                    return exception.code();
                }
            })).toList();

            start.countDown();

            assertThat(List.of(jobs.get(0).get(), jobs.get(1).get()))
                    .containsExactlyInAnyOrder("CREATED", "TAG_TERM_ALREADY_EXISTS");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition WHERE tag_code LIKE 'OCCASION_FAMILY%'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("익명과 MEMBER는 목록과 생성을 사용할 수 없다")
    void 요청_관리자권한없음_401과403을반환한다() throws Exception {
        mvc.perform(get("/api/admin/tag-definitions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/tag-definitions").with(user("member").authorities(() -> "MEMBER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/tag-definitions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Java와 PostgreSQL 정규화는 Unicode corpus에서 같은 결과를 만든다")
    void 정규화_Java와PostgreSQL_동등하다() {
        List<String> corpus = List.of(
                " ＡＢＣ\u00a0가족 ",
                "A\tB\u2003C",
                "Iİıi",
                "Σ Straße",
                "가족\u2028외식");

        for (String value : corpus) {
            assertThat(jdbc.queryForObject("SELECT normalize_tag_definition_term(?)", String.class, value))
                    .as("정규화 corpus: %s", value)
                    .isEqualTo(TagTermNormalizer.normalize(value));
        }
    }

    private String updateAfterStart(CountDownLatch start, String code, String displayName) throws Exception {
        start.await();
        try {
            tagDefinitions.update(code, new ManageTagDefinitionsUseCase.UpdateCommand(
                    0L, displayName, List.of(), "용어 교환"), ADMIN_ID.toString());
            return "OK";
        } catch (BusinessException exception) {
            return exception.code();
        }
    }
}
