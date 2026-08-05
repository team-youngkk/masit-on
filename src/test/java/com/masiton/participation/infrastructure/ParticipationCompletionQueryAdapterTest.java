package com.masiton.participation.infrastructure;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import com.masiton.orchestration.infrastructure.query.ParticipationCompletionQueryAdapter;
import com.masiton.participation.domain.ModerationActionType;
import com.masiton.participation.domain.ParticipationTargetType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("제보·신고 원본 조치 완료 조회")
class ParticipationCompletionQueryAdapterTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ParticipationCompletionQueryAdapter adapter =
            new ParticipationCompletionQueryAdapter(jdbcTemplate);

    @Test
    @DisplayName("맛집 제보는 후보 이름과 주소 및 생성 시각을 함께 검증한다")
    void 맛집생성_후보연결_이름주소와생성시각을검증한다() {
        given(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(Object[].class)))
                .willReturn(true);

        assertThat(adapter.isCompleted(ModerationActionType.CREATED, ParticipationTargetType.RESTAURANT,
                UUID.randomUUID(), OffsetDateTime.parse("2026-08-05T12:00:00Z"),
                Map.of("name", "후보 맛집", "roadAddress", "서울시 테스트로 1"))).isTrue();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Boolean.class), any(Object[].class));
        assertThat(sql.getValue()).contains("t.name = ?", "t.road_address = ?", "t.created_at >= ?");
    }

    @Test
    @DisplayName("방문 관계 수정은 후보 식별자와 연결 원본 공개 상태를 함께 검증한다")
    void 방문관계수정_연결원본_후보와공개가용상태를검증한다() {
        given(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(Object[].class)))
                .willReturn(true);
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();

        adapter.isCompleted(ModerationActionType.UPDATED, ParticipationTargetType.VISIT_RELATIONSHIP,
                UUID.randomUUID(), OffsetDateTime.parse("2026-08-05T12:00:00Z"),
                Map.of("restaurantId", restaurantId.toString(), "creatorId", creatorId.toString(),
                        "videoId", videoId.toString()));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Boolean.class), any(Object[].class));
        assertThat(sql.getValue()).contains(
                "JOIN restaurant", "JOIN creator", "JOIN video", "t.restaurant_id = ?",
                "c.external_availability_status = 'AVAILABLE'",
                "vi.external_availability_status = 'AVAILABLE'", "t.updated_at > ?");
    }

    @Test
    @DisplayName("숨김 완료는 방문 관계 자체의 비공개와 수정 시각만 검증한다")
    void 방문관계숨김_자체행_비공개와수정시각만검증한다() {
        given(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(Object[].class)))
                .willReturn(true);

        adapter.isCompleted(ModerationActionType.HIDDEN, ParticipationTargetType.VISIT_RELATIONSHIP,
                UUID.randomUUID(), OffsetDateTime.parse("2026-08-05T12:00:00Z"), null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Boolean.class), any(Object[].class));
        assertThat(sql.getValue()).contains("FROM visit t", "t.publication_status = 'PRIVATE'", "t.updated_at > ?")
                .doesNotContain("JOIN restaurant", "JOIN creator", "JOIN video");
    }
}
