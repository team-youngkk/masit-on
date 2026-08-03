package com.masiton.restaurant.application.query;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.restaurant.application.port.in.RestaurantMapPointSummary;
import com.masiton.restaurant.application.port.in.RestaurantMapPointsResult;
import com.masiton.restaurant.application.port.in.SearchRestaurantMapPointsCommand;
import com.masiton.restaurant.application.port.in.SearchRestaurantMapPointsUseCase;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.application.port.out.MapRateLimitPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.application.port.out.RestaurantMapPointRow;
import com.masiton.restaurant.application.port.out.RestaurantMapPointsCriteria;
import com.masiton.restaurant.application.port.out.RestaurantMapPointsQueryPort;
import com.masiton.visit.application.port.in.FindDistinctValidRestaurantIdsByCreatorQuery;

/**
 * API-MAP-001 지도 맛집 마커 조회를 처리한다.
 * BR-MAP-002~004 순서(호출 제한 -> 기존 필터 AND)로 검증한 뒤 Query Port를 호출한다.
 */
@Service
public class RestaurantMapPointsQueryService implements SearchRestaurantMapPointsUseCase {

    private static final int RESULT_LIMIT = 200;
    private static final long RATE_LIMIT_RETRY_AFTER_SECONDS = 1;

    private final MapRateLimitPort mapRateLimitPort;
    private final RestaurantMapPointsQueryPort restaurantMapPointsQueryPort;
    private final RestaurantSearchFilterResolver filterResolver;

    public RestaurantMapPointsQueryService(
            MapRateLimitPort mapRateLimitPort,
            RegionRepositoryPort regionRepositoryPort,
            FoodCategoryRepositoryPort foodCategoryRepositoryPort,
            RestaurantMapPointsQueryPort restaurantMapPointsQueryPort,
            FindDistinctValidRestaurantIdsByCreatorQuery findRestaurantIdsByCreatorQuery) {
        this.mapRateLimitPort = mapRateLimitPort;
        this.restaurantMapPointsQueryPort = restaurantMapPointsQueryPort;
        this.filterResolver = new RestaurantSearchFilterResolver(
                regionRepositoryPort, foodCategoryRepositoryPort, findRestaurantIdsByCreatorQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantMapPointsResult search(SearchRestaurantMapPointsCommand command) {
        if (!mapRateLimitPort.tryAcquire(command.clientAddress())) {
            throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMIT_EXCEEDED",
                    "너무 잦은 지도 조회 요청입니다. 잠시 후 다시 시도해 주세요.",
                    RATE_LIMIT_RETRY_AFTER_SECONDS);
        }

        String normalizedQuery = filterResolver.normalizeQuery(command.query());
        UUID regionId = filterResolver.resolveRegionId(command.district());
        UUID foodCategoryId = filterResolver.resolveFoodCategoryId(command.category());
        Set<UUID> candidateRestaurantIds = filterResolver.resolveCandidateRestaurantIds(command.creatorId());

        List<RestaurantMapPointRow> rows = restaurantMapPointsQueryPort.findMatching(
                new RestaurantMapPointsCriteria(normalizedQuery, regionId, foodCategoryId, candidateRestaurantIds),
                RESULT_LIMIT + 1);

        if (rows.size() > RESULT_LIMIT) {
            return new RestaurantMapPointsResult(
                    RestaurantMapPointsResult.ResultStatus.TOO_MANY_RESULTS, RESULT_LIMIT, List.of());
        }

        List<RestaurantMapPointSummary> items = rows.stream().map(this::toSummary).toList();
        return new RestaurantMapPointsResult(RestaurantMapPointsResult.ResultStatus.AVAILABLE, RESULT_LIMIT, items);
    }

    private RestaurantMapPointSummary toSummary(RestaurantMapPointRow row) {
        return new RestaurantMapPointSummary(
                row.id(), row.name(), row.category(), row.addressSummary(), row.latitude(), row.longitude());
    }
}
