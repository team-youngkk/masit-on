package com.masiton.restaurant.application.course;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.masiton.restaurant.application.port.in.RestaurantCourseCommand;
import com.masiton.restaurant.application.port.in.RestaurantCourseResult;
import com.masiton.restaurant.application.port.in.RestaurantCourseStop;
import com.masiton.restaurant.application.port.out.CourseRouteFailureCategory;
import com.masiton.restaurant.application.port.out.CourseRouteLeg;
import com.masiton.restaurant.application.port.out.CourseRouteProviderException;
import com.masiton.restaurant.application.port.out.CourseRouteProviderPort;
import com.masiton.restaurant.application.port.out.CourseRouteRequest;
import com.masiton.restaurant.application.port.out.CourseRouteResult;
import com.masiton.restaurant.application.port.out.RestaurantRepositoryPort;
import com.masiton.restaurant.domain.course.CourseStopRole;
import com.masiton.restaurant.domain.model.LifecycleStatus;
import com.masiton.restaurant.domain.model.PublicationStatus;
import com.masiton.restaurant.domain.model.Restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 근거: TST-E3-COURSE-001, docs/05-specs/api/discovery/restaurant-course-recommendation-api.md,
 * ADR-ROUTE-001. 입력 검증·정상 흐름·외부 호출 횟수·30km 상한·외부 실패 매핑·비저장을 순수 단위 테스트로 검증한다.
 * Spring 컨텍스트를 띄우지 않고 Mockito mock과 고정 Clock을 직접 주입한다.
 */
@DisplayName("맛집 코스 추천 Application 서비스")
class RestaurantCourseRecommendationServiceTest {

    private static final UUID ID_1 = id(1);
    private static final UUID ID_2 = id(2);
    private static final UUID ID_3 = id(3);
    private static final UUID ID_4 = id(4);
    private static final UUID ID_5 = id(5);

    private final RestaurantRepositoryPort restaurantRepositoryPort = mock(RestaurantRepositoryPort.class);
    private final CourseRouteProviderPort courseRouteProviderPort = mock(CourseRouteProviderPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneOffset.UTC);
    private final RestaurantCourseRecommendationService service =
            new RestaurantCourseRecommendationService(restaurantRepositoryPort, courseRouteProviderPort, clock);

    // ---------------------------------------------------------------------
    // 입력 검증 (BR-COURSE-001, BR-COURSE-002)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("선택 맛집이 1개면 INVALID_COURSE_SIZE 예외를 던지고 외부 호출을 하지 않는다")
    void recommend_선택맛집이1개인경우_INVALID_COURSE_SIZE예외를던진다() {
        // given
        RestaurantCourseCommand command = new RestaurantCourseCommand(List.of(ID_1));

        // when & then
        assertCourseException(() -> service.recommend(command), "INVALID_COURSE_SIZE", HttpStatus.BAD_REQUEST);
        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("선택 맛집이 6개면 INVALID_COURSE_SIZE 예외를 던지고 외부 호출을 하지 않는다")
    void recommend_선택맛집이6개인경우_INVALID_COURSE_SIZE예외를던진다() {
        // given
        RestaurantCourseCommand command =
                new RestaurantCourseCommand(List.of(id(1), id(2), id(3), id(4), id(5), id(6)));

        // when & then
        assertCourseException(() -> service.recommend(command), "INVALID_COURSE_SIZE", HttpStatus.BAD_REQUEST);
        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("선택 맛집이 빈 리스트면 INVALID_COURSE_SIZE 예외를 던지고 외부 호출을 하지 않는다")
    void recommend_선택맛집이빈리스트인경우_INVALID_COURSE_SIZE예외를던진다() {
        // given
        RestaurantCourseCommand command = new RestaurantCourseCommand(List.of());

        // when & then
        assertCourseException(() -> service.recommend(command), "INVALID_COURSE_SIZE", HttpStatus.BAD_REQUEST);
        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("동일 식별자를 반복 선택하면 DUPLICATE_RESTAURANT_IN_COURSE 예외를 던지고 외부 호출을 하지 않는다")
    void recommend_동일식별자를반복선택한경우_DUPLICATE_RESTAURANT_IN_COURSE예외를던진다() {
        // given
        RestaurantCourseCommand command = new RestaurantCourseCommand(List.of(ID_1, ID_1));

        // when & then
        assertCourseException(
                () -> service.recommend(command), "DUPLICATE_RESTAURANT_IN_COURSE", HttpStatus.BAD_REQUEST);
        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("조회 결과가 요청보다 적으면 RESTAURANT_NOT_FOUND 예외를 던지고 외부 호출을 하지 않는다")
    void recommend_조회결과가요청보다적은경우_RESTAURANT_NOT_FOUND예외를던진다() {
        // given
        Restaurant onlyFound = activeRestaurant(ID_1, 37.5000, 127.0000);
        when(restaurantRepositoryPort.findAllByIds(anyCollection())).thenReturn(List.of(onlyFound));
        RestaurantCourseCommand command = new RestaurantCourseCommand(List.of(ID_1, ID_2));

        // when & then
        assertCourseException(() -> service.recommend(command), "RESTAURANT_NOT_FOUND", HttpStatus.NOT_FOUND);
        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("비공개 맛집이 포함되면 RESTAURANT_NOT_PUBLIC 예외를 던지고 외부 호출을 하지 않는다")
    void recommend_비공개맛집이포함된경우_RESTAURANT_NOT_PUBLIC예외를던진다() {
        // given
        Restaurant privateRestaurant = restaurant(
                ID_1, PublicationStatus.PRIVATE, LifecycleStatus.ACTIVE,
                BigDecimal.valueOf(37.5000), BigDecimal.valueOf(127.0000));
        Restaurant publicRestaurant = activeRestaurant(ID_2, 37.5010, 127.0000);
        when(restaurantRepositoryPort.findAllByIds(anyCollection()))
                .thenReturn(List.of(privateRestaurant, publicRestaurant));
        RestaurantCourseCommand command = new RestaurantCourseCommand(List.of(ID_1, ID_2));

        // when & then
        RestaurantCourseException exception = assertCourseException(
                () -> service.recommend(command), "RESTAURANT_NOT_PUBLIC", HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(((RestaurantCourseSelectionDetails) exception.details()).selectedRestaurants())
                .containsExactly(new RestaurantCourseFailureDetails.SelectedRestaurant(
                        ID_1.toString(), privateRestaurant.getName(), 1));
        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("삭제된 맛집이 포함되면 RESTAURANT_NOT_PUBLIC 예외를 던지고 외부 호출을 하지 않는다")
    void recommend_삭제된맛집이포함된경우_RESTAURANT_NOT_PUBLIC예외를던진다() {
        // given
        Restaurant deletedRestaurant = restaurant(
                ID_1, PublicationStatus.PUBLIC, LifecycleStatus.DELETED,
                BigDecimal.valueOf(37.5000), BigDecimal.valueOf(127.0000));
        Restaurant activeStop = activeRestaurant(ID_2, 37.5010, 127.0000);
        when(restaurantRepositoryPort.findAllByIds(anyCollection()))
                .thenReturn(List.of(deletedRestaurant, activeStop));
        RestaurantCourseCommand command = new RestaurantCourseCommand(List.of(ID_1, ID_2));

        // when & then
        RestaurantCourseException exception = assertCourseException(
                () -> service.recommend(command), "RESTAURANT_NOT_PUBLIC", HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(((RestaurantCourseSelectionDetails) exception.details()).selectedRestaurants())
                .containsExactly(new RestaurantCourseFailureDetails.SelectedRestaurant(
                        ID_1.toString(), deletedRestaurant.getName(), 1));
        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("좌표가 null인 맛집이 포함되면 RESTAURANT_COORDINATE_REQUIRED 예외를 던지고 외부 호출을 하지 않는다")
    void recommend_좌표가null인맛집이포함된경우_RESTAURANT_COORDINATE_REQUIRED예외를던진다() {
        // given
        Restaurant missingLatitude = restaurant(
                ID_1, PublicationStatus.PUBLIC, LifecycleStatus.ACTIVE, null, BigDecimal.valueOf(127.0000));
        Restaurant validStop = activeRestaurant(ID_2, 37.5010, 127.0000);
        when(restaurantRepositoryPort.findAllByIds(anyCollection()))
                .thenReturn(List.of(missingLatitude, validStop));
        RestaurantCourseCommand command = new RestaurantCourseCommand(List.of(ID_1, ID_2));

        // when & then
        RestaurantCourseException exception = assertCourseException(
                () -> service.recommend(command), "RESTAURANT_COORDINATE_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(((RestaurantCourseSelectionDetails) exception.details()).selectedRestaurants())
                .containsExactly(new RestaurantCourseFailureDetails.SelectedRestaurant(
                        ID_1.toString(), missingLatitude.getName(), 1));
        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("위도가 범위를 벗어난 맛집이 포함되면 RESTAURANT_COORDINATE_REQUIRED 예외를 던지고 외부 호출을 하지 않는다")
    void recommend_위도가범위를벗어난맛집이포함된경우_RESTAURANT_COORDINATE_REQUIRED예외를던진다() {
        // given
        Restaurant outOfRangeLatitude = restaurant(
                ID_1, PublicationStatus.PUBLIC, LifecycleStatus.ACTIVE,
                BigDecimal.valueOf(91), BigDecimal.valueOf(127.0000));
        Restaurant validStop = activeRestaurant(ID_2, 37.5010, 127.0000);
        when(restaurantRepositoryPort.findAllByIds(anyCollection()))
                .thenReturn(List.of(outOfRangeLatitude, validStop));
        RestaurantCourseCommand command = new RestaurantCourseCommand(List.of(ID_1, ID_2));

        // when & then
        RestaurantCourseException exception = assertCourseException(
                () -> service.recommend(command), "RESTAURANT_COORDINATE_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(((RestaurantCourseSelectionDetails) exception.details()).selectedRestaurants())
                .containsExactly(new RestaurantCourseFailureDetails.SelectedRestaurant(
                        ID_1.toString(), outOfRangeLatitude.getName(), 1));
        verifyNoInteractions(courseRouteProviderPort);
    }

    // ---------------------------------------------------------------------
    // 정상 흐름과 크기 경계 (BR-COURSE-003, API 문서 4절)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("맛집이 2개인 하한 경계는 WAYPOINT 없이 START와 DESTINATION만 부여해 정상 반환한다")
    void recommend_맛집이2개인하한경계_WAYPOINT없이START와DESTINATION만부여한다() {
        // given
        RestaurantCourseCommand command = givenTwoStopCourseWithLeg(new CourseRouteLeg(1000, 120));

        // when
        RestaurantCourseResult result = service.recommend(command);

        // then
        assertThat(result.restaurants()).extracting(RestaurantCourseStop::role)
                .containsExactly(CourseStopRole.START, CourseStopRole.DESTINATION);
    }

    @Test
    @DisplayName("맛집이 5개인 상한 경계는 WAYPOINT 세 개를 포함해 정상 반환한다")
    void recommend_맛집이5개인상한경계_WAYPOINT세개를포함해정상반환한다() {
        // given
        RestaurantCourseCommand command = givenFiveStopCourse();

        // when
        RestaurantCourseResult result = service.recommend(command);

        // then
        assertThat(result.restaurants()).extracting(RestaurantCourseStop::role)
                .containsExactly(
                        CourseStopRole.START,
                        CourseStopRole.WAYPOINT,
                        CourseStopRole.WAYPOINT,
                        CourseStopRole.WAYPOINT,
                        CourseStopRole.DESTINATION);
        assertThat(result.restaurants()).extracting(RestaurantCourseStop::sequence)
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("정상 요청은 첫 stop을 START, 마지막을 DESTINATION, 중간을 WAYPOINT로 부여하고 sequence를 1부터 연속으로 부여한다")
    void recommend_정상요청_역할과sequence를올바르게부여한다() {
        // given
        RestaurantCourseCommand command =
                givenThreeStopCourseWithLegs(new CourseRouteLeg(1000, 120), new CourseRouteLeg(1500, 200));

        // when
        RestaurantCourseResult result = service.recommend(command);

        // then
        assertThat(result.restaurants()).extracting(RestaurantCourseStop::restaurantId)
                .containsExactly(ID_1, ID_2, ID_3);
        assertThat(result.restaurants()).extracting(RestaurantCourseStop::role)
                .containsExactly(CourseStopRole.START, CourseStopRole.WAYPOINT, CourseStopRole.DESTINATION);
        assertThat(result.restaurants()).extracting(RestaurantCourseStop::sequence)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("정상 요청의 segments는 인접 stop 쌍과 일치하고 개수가 stop 수보다 1 적다")
    void recommend_정상요청_segments가인접stop쌍과일치하고개수가stop수보다1적다() {
        // given
        RestaurantCourseCommand command =
                givenThreeStopCourseWithLegs(new CourseRouteLeg(1000, 120), new CourseRouteLeg(1500, 200));

        // when
        RestaurantCourseResult result = service.recommend(command);

        // then
        assertThat(result.segments()).hasSize(2);
        assertThat(result.segments().get(0).fromRestaurantId()).isEqualTo(ID_1);
        assertThat(result.segments().get(0).toRestaurantId()).isEqualTo(ID_2);
        assertThat(result.segments().get(0).distanceMeters()).isEqualTo(1000);
        assertThat(result.segments().get(0).durationSeconds()).isEqualTo(120);
        assertThat(result.segments().get(1).fromRestaurantId()).isEqualTo(ID_2);
        assertThat(result.segments().get(1).toRestaurantId()).isEqualTo(ID_3);
        assertThat(result.segments().get(1).distanceMeters()).isEqualTo(1500);
        assertThat(result.segments().get(1).durationSeconds()).isEqualTo(200);
    }

    @Test
    @DisplayName("정상 요청의 totalDistanceMeters와 totalDurationSeconds는 leg 합계와 일치한다")
    void recommend_정상요청_totalDistance와totalDuration이leg합계와일치한다() {
        // given
        RestaurantCourseCommand command =
                givenThreeStopCourseWithLegs(new CourseRouteLeg(1000, 120), new CourseRouteLeg(1500, 200));

        // when
        RestaurantCourseResult result = service.recommend(command);

        // then
        assertThat(result.totalDistanceMeters()).isEqualTo(2500);
        assertThat(result.totalDurationSeconds()).isEqualTo(320);
    }

    @Test
    @DisplayName("정상 요청의 expiresAt은 generatedAt으로부터 5분 뒤로 고정된다")
    void recommend_정상요청_expiresAt은generatedAt으로부터5분뒤로고정된다() {
        // given
        RestaurantCourseCommand command = givenTwoStopCourseWithLeg(new CourseRouteLeg(1000, 120));

        // when
        RestaurantCourseResult result = service.recommend(command);

        // then
        assertThat(result.expiresAt()).isEqualTo(result.generatedAt().plusMinutes(5));
    }

    @Test
    @DisplayName("정상 요청의 generatedAt은 주입한 고정 Clock 시각과 일치한다")
    void recommend_정상요청_generatedAt은주입한고정Clock시각과일치한다() {
        // given
        RestaurantCourseCommand command = givenTwoStopCourseWithLeg(new CourseRouteLeg(1000, 120));
        OffsetDateTime expectedGeneratedAt = OffsetDateTime.now(clock);

        // when
        RestaurantCourseResult result = service.recommend(command);

        // then
        assertThat(result.generatedAt()).isEqualTo(expectedGeneratedAt);
    }

    // ---------------------------------------------------------------------
    // 외부 호출 횟수 (NFR-EXTERNAL-005, ADR-ROUTE-001 9절)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("정상 요청 1건은 코스당 외부 경로 계산을 정확히 1회 호출한다")
    void recommend_정상요청1건은_외부경로계산을정확히1회호출한다() {
        // given
        RestaurantCourseCommand command = givenTwoStopCourseWithLeg(new CourseRouteLeg(1000, 120));

        // when
        service.recommend(command);

        // then
        verify(courseRouteProviderPort, times(1)).calculate(any());
    }

    @Test
    @DisplayName("외부 호출이 실패해도 재시도하지 않고 1회만 호출한다")
    void recommend_외부호출이실패해도_재시도하지않고1회만호출한다() {
        // given
        RestaurantCourseCommand command =
                givenTwoStopCourseWhereProviderThrows(CourseRouteFailureCategory.TIMEOUT);

        // when
        assertThatThrownBy(() -> service.recommend(command)).isInstanceOf(RestaurantCourseException.class);

        // then
        verify(courseRouteProviderPort, times(1)).calculate(any());
    }

    // ---------------------------------------------------------------------
    // 30km 상한 (FR-COURSE-002, API 문서 5절)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("leg 거리 합계가 30,000미터면 경계를 포함해 정상 반환한다")
    void recommend_leg거리합계가30000미터인경우_정상반환한다() {
        // given
        RestaurantCourseCommand command = givenTwoStopCourseWithLeg(new CourseRouteLeg(30_000, 1_000));

        // when
        RestaurantCourseResult result = service.recommend(command);

        // then
        assertThat(result.totalDistanceMeters()).isEqualTo(30_000);
    }

    @Test
    @DisplayName("leg 거리 합계가 30,001미터면 COURSE_DISTANCE_LIMIT_EXCEEDED 예외를 던진다")
    void recommend_leg거리합계가30001미터인경우_COURSE_DISTANCE_LIMIT_EXCEEDED예외를던진다() {
        // given
        RestaurantCourseCommand command = givenTwoStopCourseWithLeg(new CourseRouteLeg(30_001, 1_000));

        // when & then
        assertCourseException(
                () -> service.recommend(command), "COURSE_DISTANCE_LIMIT_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ---------------------------------------------------------------------
    // 외부 실패 매핑 (BR-COURSE-004, API 문서 6절)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Provider가 PARTIAL 실패를 던지면 COURSE_ROUTE_PARTIAL_FAILURE 예외로 변환한다")
    void recommend_Provider가PARTIAL실패를던진경우_COURSE_ROUTE_PARTIAL_FAILURE예외를던진다() {
        // given
        RestaurantCourseCommand command =
                givenTwoStopCourseWhereProviderThrows(CourseRouteFailureCategory.PARTIAL);

        // when & then
        assertCourseException(
                () -> service.recommend(command), "COURSE_ROUTE_PARTIAL_FAILURE", HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("Provider가 TIMEOUT 실패를 던지면 COURSE_ROUTE_PROVIDER_UNAVAILABLE 예외로 변환한다")
    void recommend_Provider가TIMEOUT실패를던진경우_COURSE_ROUTE_PROVIDER_UNAVAILABLE예외를던진다() {
        // given
        RestaurantCourseCommand command =
                givenTwoStopCourseWhereProviderThrows(CourseRouteFailureCategory.TIMEOUT);

        // when & then
        assertCourseException(
                () -> service.recommend(command), "COURSE_ROUTE_PROVIDER_UNAVAILABLE", HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("Provider가 RATE_LIMIT 실패를 던지면 COURSE_ROUTE_PROVIDER_UNAVAILABLE 예외로 변환한다")
    void recommend_Provider가RATE_LIMIT실패를던진경우_COURSE_ROUTE_PROVIDER_UNAVAILABLE예외를던진다() {
        // given
        RestaurantCourseCommand command =
                givenTwoStopCourseWhereProviderThrows(CourseRouteFailureCategory.RATE_LIMIT);

        // when & then
        assertCourseException(
                () -> service.recommend(command), "COURSE_ROUTE_PROVIDER_UNAVAILABLE", HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("Provider가 UPSTREAM 실패를 던지면 COURSE_ROUTE_PROVIDER_UNAVAILABLE 예외로 변환한다")
    void recommend_Provider가UPSTREAM실패를던진경우_COURSE_ROUTE_PROVIDER_UNAVAILABLE예외를던진다() {
        // given
        RestaurantCourseCommand command =
                givenTwoStopCourseWhereProviderThrows(CourseRouteFailureCategory.UPSTREAM);

        // when & then
        assertCourseException(
                () -> service.recommend(command), "COURSE_ROUTE_PROVIDER_UNAVAILABLE", HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("Provider가 SCHEMA 실패를 던지면 COURSE_ROUTE_PROVIDER_UNAVAILABLE 예외로 변환한다")
    void recommend_Provider가SCHEMA실패를던진경우_COURSE_ROUTE_PROVIDER_UNAVAILABLE예외를던진다() {
        // given
        RestaurantCourseCommand command =
                givenTwoStopCourseWhereProviderThrows(CourseRouteFailureCategory.SCHEMA);

        // when & then
        assertCourseException(
                () -> service.recommend(command), "COURSE_ROUTE_PROVIDER_UNAVAILABLE", HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("Provider가 PROVIDER_BLOCKED 실패를 던지면 COURSE_ROUTE_PROVIDER_UNAVAILABLE 예외로 변환한다")
    void recommend_Provider가PROVIDER_BLOCKED실패를던진경우_COURSE_ROUTE_PROVIDER_UNAVAILABLE예외를던진다() {
        // given
        RestaurantCourseCommand command =
                givenTwoStopCourseWhereProviderThrows(CourseRouteFailureCategory.PROVIDER_BLOCKED);

        // when & then
        assertCourseException(
                () -> service.recommend(command), "COURSE_ROUTE_PROVIDER_UNAVAILABLE", HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("leg 개수가 stop 수보다 1개 적지 않으면 COURSE_ROUTE_PARTIAL_FAILURE 예외를 던진다")
    void recommend_leg개수가stop수보다1적지않은경우_COURSE_ROUTE_PARTIAL_FAILURE예외를던진다() {
        // given
        Restaurant r1 = activeRestaurant(ID_1, 37.5000, 127.0000);
        Restaurant r2 = activeRestaurant(ID_2, 37.5010, 127.0000);
        Restaurant r3 = activeRestaurant(ID_3, 37.5020, 127.0000);
        when(restaurantRepositoryPort.findAllByIds(anyCollection())).thenReturn(List.of(r1, r2, r3));
        when(courseRouteProviderPort.calculate(any()))
                .thenReturn(new CourseRouteResult(List.of(new CourseRouteLeg(1000, 120))));
        RestaurantCourseCommand command = new RestaurantCourseCommand(List.of(ID_1, ID_2, ID_3));

        // when & then
        assertCourseException(
                () -> service.recommend(command), "COURSE_ROUTE_PARTIAL_FAILURE", HttpStatus.BAD_GATEWAY);
    }

    // ---------------------------------------------------------------------
    // 비저장 (NFR-PRIVACY-006, ADR-ROUTE-001 5.4절)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("정상 처리 후에도 Restaurant 저장을 호출하지 않는다")
    void recommend_정상처리후에도_Restaurant저장을호출하지않는다() {
        // given
        RestaurantCourseCommand command =
                givenThreeStopCourseWithLegs(new CourseRouteLeg(1000, 120), new CourseRouteLeg(1500, 200));

        // when
        service.recommend(command);

        // then
        verify(restaurantRepositoryPort, never()).save(any());
        verify(restaurantRepositoryPort, never()).insertIfAbsent(any());
    }

    @Test
    @DisplayName("Provider에 전달하는 요청에는 stop 좌표만 담기고 현재 위치·회원 식별자가 없다")
    void recommend_Provider에전달하는요청에는_stop좌표만담긴다() {
        // given
        RestaurantCourseCommand command =
                givenThreeStopCourseWithLegs(new CourseRouteLeg(1000, 120), new CourseRouteLeg(1500, 200));
        ArgumentCaptor<CourseRouteRequest> captor = ArgumentCaptor.forClass(CourseRouteRequest.class);

        // when
        service.recommend(command);

        // then
        // CourseRouteRequest는 stops(restaurantId, latitude, longitude) 필드만 선언하고 있어
        // 구조적으로 현재 위치·회원 식별자를 담을 수 없다. 여기서는 전달된 좌표값 자체가 선택 맛집과
        // 일치하는지만 확인한다.
        verify(courseRouteProviderPort).calculate(captor.capture());
        CourseRouteRequest captured = captor.getValue();
        assertThat(captured.stops()).extracting(waypoint -> waypoint.restaurantId())
                .containsExactly(ID_1, ID_2, ID_3);
        assertThat(captured.stops()).extracting(waypoint -> waypoint.latitude())
                .containsExactly(
                        BigDecimal.valueOf(37.5000), BigDecimal.valueOf(37.5010), BigDecimal.valueOf(37.5020));
        assertThat(captured.stops()).extracting(waypoint -> waypoint.longitude())
                .containsExactly(
                        BigDecimal.valueOf(127.0000), BigDecimal.valueOf(127.0000), BigDecimal.valueOf(127.0000));
    }

    // ---------------------------------------------------------------------
    // 픽스처와 도우미
    // ---------------------------------------------------------------------

    private RestaurantCourseCommand givenTwoStopCourseWithLeg(CourseRouteLeg leg) {
        Restaurant r1 = activeRestaurant(ID_1, 37.5000, 127.0000);
        Restaurant r2 = activeRestaurant(ID_2, 37.5010, 127.0000);
        when(restaurantRepositoryPort.findAllByIds(anyCollection())).thenReturn(List.of(r1, r2));
        when(courseRouteProviderPort.calculate(any())).thenReturn(new CourseRouteResult(List.of(leg)));
        return new RestaurantCourseCommand(List.of(ID_1, ID_2));
    }

    private RestaurantCourseCommand givenThreeStopCourseWithLegs(CourseRouteLeg leg1, CourseRouteLeg leg2) {
        Restaurant r1 = activeRestaurant(ID_1, 37.5000, 127.0000);
        Restaurant r2 = activeRestaurant(ID_2, 37.5010, 127.0000);
        Restaurant r3 = activeRestaurant(ID_3, 37.5020, 127.0000);
        when(restaurantRepositoryPort.findAllByIds(anyCollection())).thenReturn(List.of(r1, r2, r3));
        when(courseRouteProviderPort.calculate(any())).thenReturn(new CourseRouteResult(List.of(leg1, leg2)));
        return new RestaurantCourseCommand(List.of(ID_1, ID_2, ID_3));
    }

    private RestaurantCourseCommand givenFiveStopCourse() {
        Restaurant r1 = activeRestaurant(ID_1, 37.5000, 127.0000);
        Restaurant r2 = activeRestaurant(ID_2, 37.5010, 127.0000);
        Restaurant r3 = activeRestaurant(ID_3, 37.5020, 127.0000);
        Restaurant r4 = activeRestaurant(ID_4, 37.5030, 127.0000);
        Restaurant r5 = activeRestaurant(ID_5, 37.5040, 127.0000);
        when(restaurantRepositoryPort.findAllByIds(anyCollection())).thenReturn(List.of(r1, r2, r3, r4, r5));
        when(courseRouteProviderPort.calculate(any())).thenReturn(new CourseRouteResult(List.of(
                new CourseRouteLeg(500, 60),
                new CourseRouteLeg(500, 60),
                new CourseRouteLeg(500, 60),
                new CourseRouteLeg(500, 60))));
        return new RestaurantCourseCommand(List.of(ID_1, ID_2, ID_3, ID_4, ID_5));
    }

    private RestaurantCourseCommand givenTwoStopCourseWhereProviderThrows(CourseRouteFailureCategory category) {
        Restaurant r1 = activeRestaurant(ID_1, 37.5000, 127.0000);
        Restaurant r2 = activeRestaurant(ID_2, 37.5010, 127.0000);
        when(restaurantRepositoryPort.findAllByIds(anyCollection())).thenReturn(List.of(r1, r2));
        when(courseRouteProviderPort.calculate(any())).thenThrow(new CourseRouteProviderException(category));
        return new RestaurantCourseCommand(List.of(ID_1, ID_2));
    }

    private RestaurantCourseException assertCourseException(
            ThrowingCallable callable, String expectedCode, HttpStatus expectedStatus) {
        return (RestaurantCourseException) assertThatThrownBy(callable)
                .isInstanceOf(RestaurantCourseException.class)
                .satisfies(exception -> {
                    RestaurantCourseException courseException = (RestaurantCourseException) exception;
                    assertThat(courseException.code()).isEqualTo(expectedCode);
                    assertThat(courseException.status()).isEqualTo(expectedStatus);
                })
                .actual();
    }

    private Restaurant activeRestaurant(UUID restaurantId, double latitude, double longitude) {
        return restaurant(
                restaurantId, PublicationStatus.PUBLIC, LifecycleStatus.ACTIVE,
                BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }

    private Restaurant restaurant(
            UUID restaurantId,
            PublicationStatus publicationStatus,
            LifecycleStatus lifecycleStatus,
            BigDecimal latitude,
            BigDecimal longitude) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return new Restaurant(
                restaurantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "테스트 맛집 " + restaurantId,
                "kakao-place-" + restaurantId,
                "https://place.map.kakao.com/" + restaurantId,
                "서울특별시 종로구 테스트로 1",
                null,
                null,
                latitude,
                longitude,
                publicationStatus,
                lifecycleStatus,
                now,
                now,
                null);
    }

    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-%012d", n));
    }
}
