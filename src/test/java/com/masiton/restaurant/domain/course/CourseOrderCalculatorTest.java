package com.masiton.restaurant.domain.course;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 근거: TST-E3-COURSE-002, ADR-ROUTE-001 5.3절.
 * 첫 장소 고정, 최근접 이웃 정렬, 동률 ID 오름차순 안정 정렬의 결정론적 순서를 검증한다.
 */
@DisplayName("코스 방문 순서 계산기")
class CourseOrderCalculatorTest {

    @Test
    @DisplayName("입력 순서가 최근접 순서와 다르더라도 입력 첫 stop이 결과 첫 stop으로 고정된다")
    void order_입력순서와최근접순서가다른경우_입력첫stop이결과첫stop으로고정된다() {
        // given
        CourseStop start = stop(id(1), 37.9000, 127.9000);
        CourseStop near1 = stop(id(2), 37.5000, 127.0000);
        CourseStop near2 = stop(id(3), 37.5001, 127.0000);
        List<CourseStop> input = List.of(start, near1, near2);

        // when
        List<CourseStop> ordered = CourseOrderCalculator.order(input);

        // then
        assertThat(ordered.get(0)).isEqualTo(start);
    }

    @Test
    @DisplayName("출발지에서 먼 stop을 입력 2번째에 두어도 가까운 stop이 먼저 배치된다")
    void order_출발지에서먼stop이입력2번째에있어도_가까운stop이먼저배치된다() {
        // given
        CourseStop start = stop(id(1), 37.5000, 127.0000);
        CourseStop far = stop(id(2), 37.6000, 127.0000);
        CourseStop near = stop(id(3), 37.5010, 127.0000);
        List<CourseStop> input = List.of(start, far, near);

        // when
        List<CourseStop> ordered = CourseOrderCalculator.order(input);

        // then
        assertThat(ordered).extracting(CourseStop::restaurantId)
                .containsExactly(id(1), id(3), id(2));
    }

    @Test
    @DisplayName("출발지로부터 동일 거리인 두 후보가 있으면 식별자 오름차순으로 정렬한다")
    void order_출발지로부터동일거리인후보가있는경우_식별자오름차순으로정렬한다() {
        // given
        // 위도만 +0.01, -0.01로 대칭인 두 점은 경도 차이가 0이어서 haversine 거리가 정확히 같다.
        CourseStop start = stop(id(1), 37.5000, 127.0000);
        CourseStop north = stop(id(3), 37.5100, 127.0000);
        CourseStop south = stop(id(2), 37.4900, 127.0000);
        List<CourseStop> input = List.of(start, north, south);

        // when
        List<CourseStop> ordered = CourseOrderCalculator.order(input);

        // then
        assertThat(ordered).extracting(CourseStop::restaurantId)
                .containsExactly(id(1), id(2), id(3));
    }

    @Test
    @DisplayName("동일 거리 후보의 입력 순서를 바꾸거나 같은 입력을 반복 계산해도 같은 결과가 나온다")
    void order_동일거리후보의입력순서를바꾸거나반복계산해도_같은결과가나온다() {
        // given
        CourseStop start = stop(id(1), 37.5000, 127.0000);
        CourseStop north = stop(id(3), 37.5100, 127.0000);
        CourseStop south = stop(id(2), 37.4900, 127.0000);
        List<CourseStop> forwardInput = List.of(start, north, south);
        List<CourseStop> reversedInput = List.of(start, south, north);

        // when
        List<CourseStop> orderedFromForward = CourseOrderCalculator.order(forwardInput);
        List<CourseStop> orderedFromReversed = CourseOrderCalculator.order(reversedInput);
        List<CourseStop> orderedFromForwardAgain = CourseOrderCalculator.order(forwardInput);

        // then
        assertThat(orderedFromReversed).isEqualTo(orderedFromForward);
        assertThat(orderedFromForwardAgain).isEqualTo(orderedFromForward);
    }

    @Test
    @DisplayName("stop이 2개면 최근접 계산 없이 입력 순서 그대로 반환한다")
    void order_stop이2개인경우_입력순서그대로반환한다() {
        // given
        CourseStop start = stop(id(1), 37.5000, 127.0000);
        CourseStop destination = stop(id(2), 37.5010, 127.0000);
        List<CourseStop> input = List.of(start, destination);

        // when
        List<CourseStop> ordered = CourseOrderCalculator.order(input);

        // then
        assertThat(ordered).containsExactly(start, destination);
    }

    @Test
    @DisplayName("stop이 5개면 출발지부터 최근접 이웃 순서로 정상 처리된다")
    void order_stop이5개인경우_최근접이웃순서로정상처리된다() {
        // given
        CourseStop stop1 = stop(id(1), 37.5000, 127.0000);
        CourseStop stop2 = stop(id(2), 37.5005, 127.0000);
        CourseStop stop3 = stop(id(3), 37.5010, 127.0000);
        CourseStop stop4 = stop(id(4), 37.5020, 127.0000);
        CourseStop stop5 = stop(id(5), 37.5040, 127.0000);
        List<CourseStop> input = List.of(stop1, stop5, stop3, stop2, stop4);

        // when
        List<CourseStop> ordered = CourseOrderCalculator.order(input);

        // then
        assertThat(ordered).extracting(CourseStop::restaurantId)
                .containsExactly(id(1), id(2), id(3), id(4), id(5));
    }

    @Test
    @DisplayName("입력이 null이면 IllegalArgumentException을 던진다")
    void order_입력이null인경우_IllegalArgumentException을던진다() {
        // given
        // when
        // then
        assertThatThrownBy(() -> CourseOrderCalculator.order(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("입력이 1개면 IllegalArgumentException을 던진다")
    void order_입력이1개인경우_IllegalArgumentException을던진다() {
        // given
        List<CourseStop> input = List.of(stop(id(1), 37.5000, 127.0000));

        // when
        // then
        assertThatThrownBy(() -> CourseOrderCalculator.order(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("계산 후에도 입력 리스트는 변경되지 않는다")
    void order_계산후에도_입력리스트는변경되지않는다() {
        // given
        CourseStop start = stop(id(1), 37.5000, 127.0000);
        CourseStop far = stop(id(2), 37.6000, 127.0000);
        CourseStop near = stop(id(3), 37.5010, 127.0000);
        List<CourseStop> input = new ArrayList<>(List.of(start, far, near));
        List<CourseStop> snapshot = List.copyOf(input);

        // when
        CourseOrderCalculator.order(input);

        // then
        assertThat(input).containsExactlyElementsOf(snapshot);
    }

    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-%012d", n));
    }

    private static CourseStop stop(UUID restaurantId, double latitude, double longitude) {
        return new CourseStop(
                restaurantId, "맛집-" + restaurantId, BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }
}
