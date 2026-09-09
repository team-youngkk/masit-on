package com.masiton.visit;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.masiton.common.web.BusinessException;
import com.masiton.member.application.MemberPrincipal;
import com.masiton.member.application.port.out.MemberSessionStore;
import com.masiton.member.application.port.out.MemberTokenIssuer;
import com.masiton.member.domain.model.MemberRole;
import com.masiton.restaurant.application.port.out.RestaurantSearchCriteria;
import com.masiton.restaurant.application.port.out.RestaurantSearchQueryPort;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase.Change;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@AutoConfigureMockMvc
@DisplayName("관리자 방문 태그 조회와 사후 보정")
class VisitTagIntegrationTest extends com.masiton.test.FullContextIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ManageVisitTagsUseCase tags;
    @Autowired RestaurantSearchQueryPort search;
    @Autowired MockMvc mvc;
    @Autowired MemberSessionStore sessions;
    @Autowired MemberTokenIssuer tokens;

    @Test
    @DisplayName("실제 관리자 JWT로 방문 태그를 조회하고 저장한 결과를 다시 확인한다")
    void 요청_활성관리자JWT_조회수정과캐시금지를검증한다() throws Exception {
        // Given
        Fixture f = fixture();
        String authorization = adminAuthorization(f);
        String url = "/api/admin/restaurants/" + f.restaurant;
        String initialVersion = version(f);
        // When / Then
        mvc.perform(get(url + "/visit-tags").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].visitId").value(f.visit.toString()))
                .andExpect(jsonPath("$.items[0].tags").isEmpty())
                .andExpect(jsonPath("$.items[0].version").value(initialVersion))
                .andExpect(jsonPath("$.tagOptions").isNotEmpty());
        mvc.perform(put(url + "/visits/" + f.visit + "/tags").header("Authorization", authorization)
                        .contentType("application/json").content("""
                                {"expectedVersion":"%s","tagCodes":["MENU_NAENGMYEON"],"reason":"영상 확인"}
                                """.formatted(initialVersion)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(url + "/visit-tags").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].tags[0].code").value("MENU_NAENGMYEON"))
                .andExpect(jsonPath("$.items[0].tags[0].source").value("ADMIN_OVERRIDE"));
        assertThat(jdbc.queryForObject("SELECT changed_by_member_id FROM visit_tag_revision WHERE visit_id = ?",
                UUID.class, f.visit)).isEqualTo(f.member);
    }

    @Test
    @DisplayName("실제 관리자 JWT의 잘못된 방문 식별자는 저장하지 않고 400을 반환한다")
    void 수정요청_활성관리자JWT와잘못된방문ID_400을반환한다() throws Exception {
        // Given
        Fixture f = fixture();
        String authorization = adminAuthorization(f);
        // When / Then
        mvc.perform(put("/api/admin/restaurants/" + f.restaurant + "/visits/not-an-id/tags")
                        .header("Authorization", authorization).contentType("application/json")
                        .content("""
                                {"expectedVersion":"version","tagCodes":[],"reason":"영상 확인"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_tag_revision WHERE visit_id = ?",
                Integer.class, f.visit)).isZero();
    }

    private String adminAuthorization(Fixture f) {
        var session = sessions.issue(f.member.toString(), java.time.Duration.ofMinutes(10));
        return "Bearer " + tokens.issueAccessToken(
                new MemberPrincipal(f.member.toString(), session.sessionId(), MemberRole.ADMIN));
    }

    @Test
    @DisplayName("태그 없는 방문을 조회하고 보정하면 검색에 즉시 반영된다")
    void 보정_태그없는방문_검색과감사에반영한다() {
        // Given
        Fixture f = fixture();
        var initial = tags.list(f.restaurant.toString()).items().getFirst();
        assertThat(initial.tags()).isEmpty();
        // When
        replace(f, initial.version(), List.of("MENU_NAENGMYEON", "OCCASION_SOLO"));
        // Then
        assertThat(search.search(new RestaurantSearchCriteria(null, null, null, null,
                Set.of("MENU_NAENGMYEON", "OCCASION_SOLO"), 1, 21)).totalElements()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_tag_revision WHERE visit_id = ?",
                Integer.class, f.visit)).isEqualTo(1);
        String version = version(f);
        replace(f, version, List.of("MENU_NAENGMYEON", "OCCASION_SOLO"));
        assertThat(version(f)).isEqualTo(version);
        replace(f, version, List.of());
        assertThat(search.search(new RestaurantSearchCriteria(null, null, null, null,
                Set.of("MENU_NAENGMYEON"), 1, 21)).totalElements()).isZero();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM visit_tag_revision WHERE visit_id = ?", f.visit))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    @DisplayName("동일 버전의 동시 저장은 한 건만 성공하고 다른 요청은 충돌한다")
    void 저장_동일버전동시요청_한건만성공한다() throws Exception {
        // Given
        Fixture f = fixture();
        String version = version(f);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var jobs = List.of("MENU_NAENGMYEON", "OCCASION_SOLO").stream().map(code -> executor.submit(() -> {
                start.await();
                try {
                    replace(f, version, List.of(code));
                    return "OK";
                } catch (BusinessException exception) {
                    return exception.code();
                }
            })).toList();
            // When
            start.countDown();
            // Then
            assertThat(List.of(jobs.get(0).get(), jobs.get(1).get()))
                    .containsExactlyInAnyOrder("OK", "VISIT_TAG_CONCURRENT_UPDATE");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_tag_revision WHERE visit_id = ?",
                Integer.class, f.visit)).isEqualTo(1);
    }

    @Test
    @DisplayName("잘못된 소속과 비활성 태그를 거부하고 감사 실패는 연결을 롤백한다")
    void 저장_잘못된입력과감사실패_연결변경이없다() {
        // Given
        Fixture f = fixture();
        String version = version(f);
        // When / Then
        assertThatThrownBy(() -> tags.replace(UUID.randomUUID().toString(), f.visit.toString(),
                new Change(version, List.of(), "사유"), f.member.toString()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.status().value()).isEqualTo(404));
        jdbc.update("UPDATE tag_definition SET status = 'DEPRECATED' WHERE tag_code = 'MENU_NAENGMYEON'");
        assertThatThrownBy(() -> replace(f, version, List.of("MENU_NAENGMYEON")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("INVALID_FIELD_VALUE"));
        assertThatThrownBy(() -> tags.replace(f.restaurant.toString(), f.visit.toString(),
                new Change(version, List.of("OCCASION_SOLO"), "사유"), UUID.randomUUID().toString()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(tags.list(f.restaurant.toString()).items().getFirst().tags()).isEmpty();
        assertThat(version(f)).isEqualTo(version);
    }

    @Test
    @DisplayName("유지 태그의 근거를 보존하고 외부 연결 변경 후 이전 버전을 거절한다")
    void 저장_기존태그유지_근거보존과외부변경을검증한다() {
        // Given
        Fixture f = fixture();
        replace(f, version(f), List.of("MENU_NAENGMYEON"));
        String rowBefore = jdbc.queryForObject("SELECT to_jsonb(vt)::text FROM visit_tag vt WHERE visit_id = ?",
                String.class, f.visit);
        // When
        replace(f, version(f), List.of("MENU_NAENGMYEON", "OCCASION_SOLO"));
        // Then
        assertThat(jdbc.queryForList("SELECT to_jsonb(vt)::text FROM visit_tag vt WHERE visit_id = ?",
                String.class, f.visit)).contains(rowBefore);
        String stale = version(f);
        jdbc.update("DELETE FROM visit_tag WHERE visit_id = ?", f.visit);
        assertThatThrownBy(() -> replace(f, stale, List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo("VISIT_TAG_CONCURRENT_UPDATE"));
    }

    @Test
    @DisplayName("비활성 태그의 기존 연결은 유지와 제거를 허용하고 제거 뒤 재연결은 거절한다")
    void 저장_기존태그비활성화_유지제거와신규연결경계를검증한다() {
        Fixture f = fixture();
        replace(f, version(f), List.of("MENU_NAENGMYEON"));
        jdbc.update("UPDATE tag_definition SET status = 'DEPRECATED' WHERE tag_code = 'MENU_NAENGMYEON'");

        replace(f, version(f), List.of("MENU_NAENGMYEON", "OCCASION_SOLO"));
        assertThat(tags.list(f.restaurant.toString()).items().getFirst().tags())
                .extracting(ManageVisitTagsUseCase.Tag::code)
                .containsExactly("MENU_NAENGMYEON", "OCCASION_SOLO");

        replace(f, version(f), List.of("OCCASION_SOLO"));
        assertThatThrownBy(() -> replace(f, version(f), List.of("MENU_NAENGMYEON", "OCCASION_SOLO")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_FIELD_VALUE"));
    }

    @Test
    @DisplayName("익명과 일반 회원의 관리자 태그 조회 및 수정을 차단한다")
    void 요청_관리자권한없음_401과403을반환한다() throws Exception {
        // Given
        String url = "/api/admin/restaurants/" + UUID.randomUUID();
        // When / Then
        mvc.perform(get(url + "/visit-tags")).andExpect(status().isUnauthorized());
        mvc.perform(get(url + "/visit-tags").with(user("member").authorities(() -> "MEMBER")))
                .andExpect(status().isForbidden());
        mvc.perform(put(url + "/visits/visit/tags").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(url + "/visits/visit/tags").with(user("member").authorities(() -> "MEMBER"))
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 계정 삭제는 감사 작성자만 익명화하고 일반 감사 변경은 금지한다")
    void 감사_계정삭제_작성자만익명화한다() {
        // Given
        Fixture f = fixture();
        replace(f, version(f), List.of("MENU_NAENGMYEON"));
        String before = jdbc.queryForObject("SELECT (to_jsonb(r) - 'changed_by_member_id')::text "
                + "FROM visit_tag_revision r WHERE visit_id = ?", String.class, f.visit);
        // When / Then
        assertThatThrownBy(() -> jdbc.update("UPDATE visit_tag_revision SET changed_by_member_id = NULL WHERE visit_id = ?", f.visit))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE visit_tag_revision SET reason = '변조' WHERE visit_id = ?", f.visit))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        jdbc.update("DELETE FROM member_account WHERE id = ?", f.member);
        assertThat(jdbc.queryForObject("SELECT changed_by_member_id FROM visit_tag_revision WHERE visit_id = ?",
                UUID.class, f.visit)).isNull();
        assertThat(jdbc.queryForObject("SELECT (to_jsonb(r) - 'changed_by_member_id')::text "
                + "FROM visit_tag_revision r WHERE visit_id = ?", String.class, f.visit)).isEqualTo(before);
        assertThatThrownBy(() -> jdbc.update("UPDATE visit_tag_revision SET reason = '변조' WHERE visit_id = ?", f.visit))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    @DisplayName("중복과 미등록 태그를 거절하고 비공개 방문은 조회와 수정에서 제외한다")
    void 저장_중복미등록과비공개_변경하지않는다() {
        // Given
        Fixture f = fixture();
        String version = version(f);
        // When / Then
        for (List<String> codes : List.of(List.of("OCCASION_SOLO", "OCCASION_SOLO"), List.of("UNKNOWN"))) {
            assertThatThrownBy(() -> replace(f, version, codes)).isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.code()).isEqualTo("INVALID_FIELD_VALUE"));
        }
        jdbc.update("UPDATE visit SET publication_status = 'PRIVATE' WHERE id = ?", f.visit);
        assertThat(tags.list(f.restaurant.toString()).items()).isEmpty();
        assertThatThrownBy(() -> replace(f, version, List.of())).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.status().value()).isEqualTo(404));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_tag_revision WHERE visit_id = ?", Integer.class, f.visit)).isZero();
    }

    @Test
    @DisplayName("관리자 요청의 잘못된 식별자는 400과 공통 오류 코드를 반환한다")
    void 요청_잘못된식별자_400을반환한다() throws Exception {
        // Given / When / Then
        mvc.perform(get("/api/admin/restaurants/not-an-id/visit-tags")
                        .with(user(UUID.randomUUID().toString()).authorities(() -> "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"));
    }

    private String version(Fixture f) { return tags.list(f.restaurant.toString()).items().getFirst().version(); }
    private void replace(Fixture f, String version, List<String> codes) {
        tags.replace(f.restaurant.toString(), f.visit.toString(), new Change(version, codes, "영상 확인 후 수정"),
                f.member.toString());
    }

    private Fixture fixture() {
        UUID r = UUID.randomUUID(), c = UUID.randomUUID(), vi = UUID.randomUUID(), v = UUID.randomUUID(), m = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO restaurant(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                                       road_address, phone_number)
                VALUES (?, '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
                        '태그 테스트', ?, 'https://example.com/place', '서울 종로구 테스트로 1', '02-1234-5678')
                """, r, r.toString());
        jdbc.update("""
                INSERT INTO creator(id, external_channel_id, channel_name, channel_url, external_availability_status,
                                    external_status_checked_at) VALUES (?, ?, '채널', 'https://youtube.com/channel/test', 'AVAILABLE', now())
                """, c, c.toString());
        jdbc.update("""
                INSERT INTO video(id, creator_id, external_video_id, publisher_external_channel_id, title,
                                  source_url, thumbnail_url, external_availability_status, external_status_checked_at)
                VALUES (?, ?, ?, ?, '영상', 'https://youtube.com/watch?v=test', 'https://example.com/image', 'AVAILABLE', now())
                """, vi, c, vi.toString().substring(0, 20), c.toString());
        jdbc.update("INSERT INTO visit(id, restaurant_id, creator_id, video_id) VALUES (?, ?, ?, ?)", v, r, c, vi);
        jdbc.update("""
                INSERT INTO member_account(id, email, password_hash, status, role, email_verified_at, created_at, updated_at)
                VALUES (?, ?, 'test-hash', 'ACTIVE', 'ADMIN', now(), now(), now())
                """, m, m + "@example.com");
        return new Fixture(r, v, m);
    }

    private record Fixture(UUID restaurant, UUID visit, UUID member) { }
}
