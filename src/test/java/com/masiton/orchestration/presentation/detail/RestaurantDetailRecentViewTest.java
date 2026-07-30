package com.masiton.orchestration.presentation.detail;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.masiton.orchestration.application.port.in.GetRestaurantDetailQuery;
import com.masiton.orchestration.application.query.ContentStatus;
import com.masiton.orchestration.application.query.RestaurantDetailResult;
import com.masiton.orchestration.application.query.RestaurantDetailWithMemberContextService;
import com.masiton.personal.application.port.in.RecordRecentRestaurantViewUseCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("맛집 상세 최근 본 기록")
class RestaurantDetailRecentViewTest {

    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private final GetRestaurantDetailQuery detailQuery = mock(GetRestaurantDetailQuery.class);
    private final RecordRecentRestaurantViewUseCase recorder = mock(RecordRecentRestaurantViewUseCase.class);
    private final RestaurantDetailWithMemberContextService detailWithMemberContext =
            new RestaurantDetailWithMemberContextService(detailQuery, recorder);
    private final RestaurantDetailController controller = new RestaurantDetailController(detailWithMemberContext);

    @Test
    @DisplayName("회원의 정상 상세 조회는 최근 본 기록을 남긴다")
    void 상세조회_회원인증_최근기록을남긴다() {
        // given
        when(detailQuery.getRestaurantDetail(RESTAURANT_ID)).thenReturn(detail());
        Authentication authentication = memberAuthentication();

        // when
        controller.getRestaurantDetail(RESTAURANT_ID.toString(), authentication);

        // then
        verify(recorder).record(eq(MEMBER_ID), eq(RESTAURANT_ID));
    }

    @Test
    @DisplayName("익명 상세 조회는 최근 본 기록을 남기지 않는다")
    void 상세조회_익명_최근기록을남기지않는다() {
        // given
        when(detailQuery.getRestaurantDetail(RESTAURANT_ID)).thenReturn(detail());

        // when
        controller.getRestaurantDetail(RESTAURANT_ID.toString(), null);

        // then
        verify(recorder, never()).record(any(), any());
    }

    @Test
    @DisplayName("최근 기록 저장 실패가 발생해도 상세 응답은 그대로 반환한다")
    void 상세조회_최근기록실패_상세응답은성공한다() {
        // given
        when(detailQuery.getRestaurantDetail(RESTAURANT_ID)).thenReturn(detail());
        Authentication authentication = memberAuthentication();
        doThrow(new RuntimeException("저장소 장애"))
                .when(recorder).record(eq(MEMBER_ID), eq(RESTAURANT_ID));

        // when
        RestaurantDetailResponse response = controller.getRestaurantDetail(
                RESTAURANT_ID.toString(), authentication);

        // then
        assertThat(response.id()).isEqualTo(RESTAURANT_ID.toString());
        assertThat(response.name()).isEqualTo("테스트 맛집");
    }

    private RestaurantDetailResult detail() {
        return new RestaurantDetailResult(
                RESTAURANT_ID, "테스트 맛집", "한식", "서울특별시 마포구 월드컵로 1",
                null, "02-000-0000", "https://place.map.kakao.com/example",
                ContentStatus.AVAILABLE, List.of(), List.of());
    }

    private Authentication memberAuthentication() {
        return new TestingAuthenticationToken(
                MEMBER_ID.toString(), null, List.of(new SimpleGrantedAuthority("MEMBER")));
    }
}
