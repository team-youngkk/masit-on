package com.masiton.restaurant.application.course;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.masiton.restaurant.application.port.in.RecommendRestaurantCourseUseCase;
import com.masiton.restaurant.application.port.in.RestaurantCourseCommand;
import com.masiton.restaurant.application.port.in.RestaurantCourseResult;
import com.masiton.restaurant.application.port.in.RestaurantCourseSegment;
import com.masiton.restaurant.application.port.in.RestaurantCourseStop;
import com.masiton.restaurant.application.port.in.RestaurantCourseVertex;
import com.masiton.restaurant.application.port.out.CourseRouteFailureCategory;
import com.masiton.restaurant.application.port.out.CourseRouteLeg;
import com.masiton.restaurant.application.port.out.CourseRouteProviderException;
import com.masiton.restaurant.application.port.out.CourseRouteProviderPort;
import com.masiton.restaurant.application.port.out.CourseRouteRequest;
import com.masiton.restaurant.application.port.out.CourseRouteResult;
import com.masiton.restaurant.application.port.out.CourseRouteVertex;
import com.masiton.restaurant.application.port.out.CourseRouteWaypoint;
import com.masiton.restaurant.application.port.out.RestaurantRepositoryPort;
import com.masiton.restaurant.domain.course.CourseOrderCalculator;
import com.masiton.restaurant.domain.course.CourseStop;
import com.masiton.restaurant.domain.course.CourseStopRole;
import com.masiton.restaurant.domain.model.LifecycleStatus;
import com.masiton.restaurant.domain.model.PublicationStatus;
import com.masiton.restaurant.domain.model.Restaurant;

/**
 * 근거: docs/05-specs/api/discovery/restaurant-course-recommendation-api.md,
 * docs/07-adr/integration/route-001-kakao-mobility-course-routing.md.
 *
 * 이 메서드는 의도적으로 {@code @Transactional}을 붙이지 않는다. CLAUDE.md 7절과 ADR-ROUTE-001 5.2절에 따라
 * 외부 HTTP 호출(Kakao Mobility) 중에는 DB 트랜잭션을 열지 않는다. 맛집 조회는 Repository Adapter가 자체
 * 트랜잭션 경계로 수행하고, 경로 계산 외부 호출은 그 바깥에서 이뤄진다. 코스 결과는 어디에도 저장하지 않는다
 * (BR-COURSE-003, ADR-ROUTE-001 5.4절).
 */
@Service
public class RestaurantCourseRecommendationService implements RecommendRestaurantCourseUseCase {

    private static final int MIN_STOPS = 2;
    private static final int MAX_STOPS = 5;
    private static final int MAX_TOTAL_DISTANCE_METERS = 30_000;
    private static final Duration RESULT_TTL = Duration.ofMinutes(5);

    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    private final RestaurantRepositoryPort restaurantRepositoryPort;
    private final CourseRouteProviderPort courseRouteProviderPort;
    private final Clock clock;

    public RestaurantCourseRecommendationService(
            RestaurantRepositoryPort restaurantRepositoryPort,
            CourseRouteProviderPort courseRouteProviderPort,
            @Qualifier("restaurantCourseClock") Clock clock) {
        this.restaurantRepositoryPort = restaurantRepositoryPort;
        this.courseRouteProviderPort = courseRouteProviderPort;
        this.clock = clock;
    }

    @Override
    public RestaurantCourseResult recommend(RestaurantCourseCommand command) {
        List<UUID> requestedIds = validatedIds(command);

        List<Restaurant> found = restaurantRepositoryPort.findAllByIds(requestedIds);
        if (found.size() < requestedIds.size()) {
            throw RestaurantCourseException.restaurantNotFound();
        }

        Map<UUID, Restaurant> byId = found.stream()
                .collect(Collectors.toMap(Restaurant::getId, Function.identity()));
        List<Restaurant> orderedByRequest = requestedIds.stream().map(byId::get).toList();

        requirePublic(orderedByRequest);
        requireValidCoordinates(orderedByRequest);

        List<CourseStop> stops = orderedByRequest.stream()
                .map(restaurant -> new CourseStop(
                        restaurant.getId(), restaurant.getName(), restaurant.getLatitude(), restaurant.getLongitude()))
                .toList();
        List<CourseStop> orderedStops = CourseOrderCalculator.order(stops);

        CourseRouteResult routeResult = calculateRoute(orderedStops, orderedByRequest);

        if (routeResult.legs().size() != orderedStops.size() - 1) {
            throw RestaurantCourseException.routePartialFailure(
                    RestaurantCourseFailureDetails.of(orderedByRequest, "PARTIAL"));
        }

        long totalDistanceMeters = 0;
        long totalDurationSeconds = 0;
        for (CourseRouteLeg leg : routeResult.legs()) {
            totalDistanceMeters += leg.distanceMeters();
            totalDurationSeconds += leg.durationSeconds();
        }
        if (totalDistanceMeters > MAX_TOTAL_DISTANCE_METERS) {
            throw RestaurantCourseException.distanceLimitExceeded();
        }

        OffsetDateTime generatedAt = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = generatedAt.plus(RESULT_TTL);

        return new RestaurantCourseResult(
                toCourseStops(orderedStops),
                toSegments(orderedStops, routeResult.legs()),
                Math.toIntExact(totalDistanceMeters),
                Math.toIntExact(totalDurationSeconds),
                generatedAt,
                expiresAt);
    }

    private List<UUID> validatedIds(RestaurantCourseCommand command) {
        List<UUID> ids = command == null ? null : command.restaurantIds();
        if (ids == null || ids.size() < MIN_STOPS || ids.size() > MAX_STOPS) {
            throw RestaurantCourseException.invalidCourseSize();
        }

        Set<UUID> unique = new HashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw RestaurantCourseException.duplicateRestaurant();
        }

        return ids;
    }

    private void requirePublic(List<Restaurant> restaurants) {
        for (int i = 0; i < restaurants.size(); i++) {
            Restaurant restaurant = restaurants.get(i);
            if (restaurant.getPublicationStatus() != PublicationStatus.PUBLIC
                    || restaurant.getLifecycleStatus() != LifecycleStatus.ACTIVE) {
                throw RestaurantCourseException.restaurantNotPublic(
                        RestaurantCourseSelectionDetails.of(restaurant, i + 1));
            }
        }
    }

    private void requireValidCoordinates(List<Restaurant> restaurants) {
        for (int i = 0; i < restaurants.size(); i++) {
            Restaurant restaurant = restaurants.get(i);
            BigDecimal latitude = restaurant.getLatitude();
            BigDecimal longitude = restaurant.getLongitude();
            if (latitude == null || longitude == null
                    || latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0
                    || longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
                throw RestaurantCourseException.coordinateRequired(
                        RestaurantCourseSelectionDetails.of(restaurant, i + 1));
            }
        }
    }

    private CourseRouteResult calculateRoute(List<CourseStop> orderedStops, List<Restaurant> selectedRestaurants) {
        List<CourseRouteWaypoint> waypoints = orderedStops.stream()
                .map(stop -> new CourseRouteWaypoint(stop.restaurantId(), stop.latitude(), stop.longitude()))
                .toList();

        try {
            // NFR-EXTERNAL-005: 코스당 외부 호출은 정확히 1회다. 재시도·fallback을 만들지 않는다.
            return courseRouteProviderPort.calculate(new CourseRouteRequest(waypoints));
        } catch (CourseRouteProviderException exception) {
            // 원인 예외는 cause로 연결하되, 메시지에는 category 이름만 남은 예외를 사용한다.
            RestaurantCourseException translated = switch (exception.category()) {
                case PARTIAL -> RestaurantCourseException.routePartialFailure(
                        RestaurantCourseFailureDetails.of(selectedRestaurants, "PARTIAL"));
                case SERVICE_RATE_LIMIT -> RestaurantCourseException.routeRateLimited(
                        RestaurantCourseFailureDetails.of(selectedRestaurants, "SERVICE_RATE_LIMIT"));
                default -> RestaurantCourseException.routeProviderUnavailable(
                        RestaurantCourseFailureDetails.of(selectedRestaurants, "PROVIDER_UNAVAILABLE"));
            };
            translated.initCause(exception);
            throw translated;
        }
    }

    private List<RestaurantCourseStop> toCourseStops(List<CourseStop> orderedStops) {
        List<RestaurantCourseStop> result = new ArrayList<>(orderedStops.size());
        int lastIndex = orderedStops.size() - 1;
        for (int i = 0; i <= lastIndex; i++) {
            CourseStop stop = orderedStops.get(i);
            CourseStopRole role;
            if (i == 0) {
                role = CourseStopRole.START;
            } else if (i == lastIndex) {
                role = CourseStopRole.DESTINATION;
            } else {
                role = CourseStopRole.WAYPOINT;
            }
            result.add(new RestaurantCourseStop(
                    i + 1, stop.restaurantId(), stop.name(), role, stop.latitude(), stop.longitude()));
        }
        return result;
    }

    private List<RestaurantCourseSegment> toSegments(List<CourseStop> orderedStops, List<CourseRouteLeg> legs) {
        List<RestaurantCourseSegment> segments = new ArrayList<>(legs.size());
        for (int i = 0; i < legs.size(); i++) {
            CourseStop from = orderedStops.get(i);
            CourseStop to = orderedStops.get(i + 1);
            CourseRouteLeg leg = legs.get(i);
            segments.add(new RestaurantCourseSegment(
                    from.restaurantId(),
                    to.restaurantId(),
                    leg.distanceMeters(),
                    leg.durationSeconds(),
                    leg.shapeStatus(),
                    toPath(leg.path())));
        }
        return segments;
    }

    private List<RestaurantCourseVertex> toPath(List<CourseRouteVertex> path) {
        return path.stream()
                .map(vertex -> new RestaurantCourseVertex(vertex.latitude(), vertex.longitude()))
                .toList();
    }
}
