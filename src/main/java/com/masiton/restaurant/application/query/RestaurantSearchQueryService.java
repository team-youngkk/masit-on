package com.masiton.restaurant.application.query;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.restaurant.application.port.in.RestaurantSearchResult;
import com.masiton.restaurant.application.port.in.RestaurantSummary;
import com.masiton.restaurant.application.port.in.SearchRestaurantsCommand;
import com.masiton.restaurant.application.port.in.SearchRestaurantsUseCase;
import com.masiton.restaurant.application.port.in.VisitedCreatorSummary;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.application.port.out.RestaurantSearchCriteria;
import com.masiton.restaurant.application.port.out.RestaurantSearchQueryPort;
import com.masiton.restaurant.application.port.out.RestaurantSearchQueryResult;
import com.masiton.restaurant.application.port.out.RestaurantSearchRow;
import com.masiton.restaurant.application.port.out.VisitedByRow;
import com.masiton.visit.application.port.in.FindDistinctValidRestaurantIdsByCreatorQuery;

/**
 * API-DISCOVERY-001 맛집 목록 및 조건 검색을 처리한다.
 * BR-SEARCH-001~009 순서(검색어 정규화 -> district -> category -> creatorId -> tags)로 검증한 뒤
 * Query Port를 호출하고, 방문 유튜버는 배치 조회 후 이 계층에서 정렬·상위 3명 제한을 계산한다.
 */
@Service
public class RestaurantSearchQueryService implements SearchRestaurantsUseCase {

    private static final int MAX_DISPLAYED_VISITED_BY = 3;

    private final RestaurantSearchQueryPort restaurantSearchQueryPort;
    private final RestaurantSearchFilterResolver filterResolver;

    public RestaurantSearchQueryService(
            RegionRepositoryPort regionRepositoryPort,
            FoodCategoryRepositoryPort foodCategoryRepositoryPort,
            RestaurantSearchQueryPort restaurantSearchQueryPort,
            FindDistinctValidRestaurantIdsByCreatorQuery findRestaurantIdsByCreatorQuery) {
        this.restaurantSearchQueryPort = restaurantSearchQueryPort;
        this.filterResolver = new RestaurantSearchFilterResolver(
                regionRepositoryPort, foodCategoryRepositoryPort, findRestaurantIdsByCreatorQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantSearchResult search(SearchRestaurantsCommand command) {
        String normalizedQuery = filterResolver.normalizeQuery(command.query());
        UUID regionId = filterResolver.resolveRegionId(command.district());
        UUID foodCategoryId = filterResolver.resolveFoodCategoryId(command.category());
        Set<UUID> candidateRestaurantIds = filterResolver.resolveCandidateRestaurantIds(command.creatorId());

        RestaurantSearchQueryResult queryResult = restaurantSearchQueryPort.search(
                new RestaurantSearchCriteria(
                        normalizedQuery,
                        regionId,
                        foodCategoryId,
                        candidateRestaurantIds,
                        Set.copyOf(command.tags()),
                        command.page(),
                        command.size()));

        Map<UUID, List<VisitedByRow>> visitedByRestaurantId = loadVisitedBy(queryResult.rows());
        List<RestaurantSummary> items = queryResult.rows().stream()
                .map(row -> toSummary(row, visitedByRestaurantId.getOrDefault(row.id(), List.of())))
                .toList();

        int totalPages = queryResult.totalElements() == 0
                ? 0
                : (int) Math.ceil((double) queryResult.totalElements() / command.size());
        boolean hasNext = command.page() < totalPages;

        return new RestaurantSearchResult(
                items, command.page(), command.size(), queryResult.totalElements(), totalPages, hasNext);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateFilters(SearchRestaurantsCommand command) {
        filterResolver.normalizeQuery(command.query());
        filterResolver.resolveRegionId(command.district());
        filterResolver.resolveFoodCategoryId(command.category());
        filterResolver.resolveCandidateRestaurantIds(command.creatorId());
    }

    private Map<UUID, List<VisitedByRow>> loadVisitedBy(List<RestaurantSearchRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<UUID> restaurantIds = rows.stream().map(RestaurantSearchRow::id).toList();
        return restaurantSearchQueryPort.findVisitedByRestaurantIds(restaurantIds).stream()
                .collect(Collectors.groupingBy(VisitedByRow::restaurantId));
    }

    private RestaurantSummary toSummary(RestaurantSearchRow row, List<VisitedByRow> visitedBy) {
        List<VisitedCreatorSummary> visible = visitedBy.stream()
                .sorted(Comparator.comparing(VisitedByRow::channelName).thenComparing(VisitedByRow::creatorId))
                .limit(MAX_DISPLAYED_VISITED_BY)
                .map(visit -> new VisitedCreatorSummary(visit.creatorId(), visit.channelName()))
                .toList();
        int remaining = Math.max(0, visitedBy.size() - MAX_DISPLAYED_VISITED_BY);
        return new RestaurantSummary(row.id(), row.name(), row.district(), row.category(), visible, remaining);
    }
}
