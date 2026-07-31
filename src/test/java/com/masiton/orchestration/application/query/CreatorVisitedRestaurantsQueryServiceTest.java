package com.masiton.orchestration.application.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.common.web.BusinessException;
import com.masiton.creator.application.port.in.FindCreatorReferenceUseCase;
import com.masiton.orchestration.application.port.in.CreatorVisitedRestaurantsResult;
import com.masiton.orchestration.application.port.out.CreatorVisitedRestaurantPageResult;
import com.masiton.orchestration.application.port.out.CreatorVisitedRestaurantQueryPort;
import com.masiton.orchestration.application.port.out.CreatorVisitedRestaurantRow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * API-CREATOR-DETAIL-002 유튜버 방문 맛집 조회 조합을 Application 단위로 검증한다.
 * 중복 제거·정렬 자체는 {@link CreatorVisitedRestaurantQueryPort} 구현체(Adapter)의 책임이므로
 * 이 테스트는 404 경계와 페이지 계산만 검증한다.
 */
@DisplayName("유튜버 방문 맛집 조회 조합")
class CreatorVisitedRestaurantsQueryServiceTest {

    private static final UUID CREATOR_ID = UUID.randomUUID();

    private final FindCreatorReferenceUseCase findCreatorReferenceUseCase =
            mock(FindCreatorReferenceUseCase.class);
    private final CreatorVisitedRestaurantQueryPort creatorVisitedRestaurantQueryPort =
            mock(CreatorVisitedRestaurantQueryPort.class);
    private final CreatorVisitedRestaurantsQueryService service = new CreatorVisitedRestaurantsQueryService(
            findCreatorReferenceUseCase, creatorVisitedRestaurantQueryPort);

    @Test
    @DisplayName("Creator 공개 유효성 확인이 false이면 목록 Port를 호출하지 않고 CREATOR_NOT_FOUND 예외를 던진다")
    void 방문맛집조회_Creator무효_목록조회없이CREATOR_NOT_FOUND예외를던진다() {
        // given
        when(findCreatorReferenceUseCase.findCreatorReference(CREATOR_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getVisitedRestaurants(CREATOR_ID, 1, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("CREATOR_NOT_FOUND"));
        verify(creatorVisitedRestaurantQueryPort, never()).findPage(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Creator가 유효하고 결과가 없으면 200에 해당하는 빈 목록과 totalPages 0을 반환한다")
    void 방문맛집조회_Creator유효결과없음_빈목록과totalPages0을반환한다() {
        // given
        when(findCreatorReferenceUseCase.findCreatorReference(CREATOR_ID)).thenReturn(publicCreatorReference());
        when(creatorVisitedRestaurantQueryPort.findPage(CREATOR_ID, 1, 20))
                .thenReturn(new CreatorVisitedRestaurantPageResult(List.of(), 0L));

        // when
        CreatorVisitedRestaurantsResult result = service.getVisitedRestaurants(CREATOR_ID, 1, 20);

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("범위 밖 유효 페이지는 빈 items와 실제 totalElements·totalPages를 유지한다")
    void 방문맛집조회_범위밖페이지_빈items와실제전체개수를유지한다() {
        // given: 전체 5건, size 20 기준 totalPages는 1이지만 요청 페이지가 2인 경우
        when(findCreatorReferenceUseCase.findCreatorReference(CREATOR_ID)).thenReturn(publicCreatorReference());
        when(creatorVisitedRestaurantQueryPort.findPage(CREATOR_ID, 2, 20))
                .thenReturn(new CreatorVisitedRestaurantPageResult(List.of(), 5L));

        // when
        CreatorVisitedRestaurantsResult result = service.getVisitedRestaurants(CREATOR_ID, 2, 20);

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(5L);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("Port가 반환한 Row를 재조합 없이 응답 항목으로 그대로 매핑한다")
    void 방문맛집조회_Row존재_필드를그대로매핑한다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        CreatorVisitedRestaurantRow row =
                new CreatorVisitedRestaurantRow(restaurantId, "테스트 맛집", "마포구", "한식");
        when(findCreatorReferenceUseCase.findCreatorReference(CREATOR_ID)).thenReturn(publicCreatorReference());
        when(creatorVisitedRestaurantQueryPort.findPage(CREATOR_ID, 1, 20))
                .thenReturn(new CreatorVisitedRestaurantPageResult(List.of(row), 1L));

        // when
        CreatorVisitedRestaurantsResult result = service.getVisitedRestaurants(CREATOR_ID, 1, 20);

        // then
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(restaurantId);
        assertThat(result.items().get(0).name()).isEqualTo("테스트 맛집");
        assertThat(result.items().get(0).district()).isEqualTo("마포구");
        assertThat(result.items().get(0).category()).isEqualTo("한식");
        assertThat(result.pageNumber()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
    }

    /** Creator가 공개·활성·외부 이용 가능한 정상 Snapshot이다. */
    private static Optional<FindCreatorReferenceUseCase.CreatorReference> publicCreatorReference() {
        return Optional.of(new FindCreatorReferenceUseCase.CreatorReference(
                CREATOR_ID, "UC-public-channel", true, true));
    }
}
