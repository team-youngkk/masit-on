package com.masiton.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase;
import com.masiton.common.web.BusinessException;

@AutoConfigureMockMvc
@DisplayName("관리자 태그 정의 조회와 생성")
class TagDefinitionIntegrationTest extends com.masiton.test.FullContextIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ManageTagDefinitionsUseCase tagDefinitions;

    @BeforeEach
    void cleanUpState() {
        cleanupTransactionalState(jdbc);
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
}
