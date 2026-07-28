package com.masiton.restaurant.application.query;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
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
import com.masiton.restaurant.domain.model.FoodCategory;
import com.masiton.restaurant.domain.model.Region;

/**
 * API-DISCOVERY-001 맛집 목록 및 조건 검색을 처리한다.
 * BR-SEARCH-001~009 순서(검색어 정규화 -> district -> category -> creatorId)로 검증한 뒤
 * Query Port를 호출하고, 방문 유튜버는 배치 조회 후 이 계층에서 정렬·상위 3명 제한을 계산한다.
 */
@Service
public class RestaurantSearchQueryService implements SearchRestaurantsUseCase {

    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_DISPLAYED_VISITED_BY = 3;

    private final RegionRepositoryPort regionRepositoryPort;
    private final FoodCategoryRepositoryPort foodCategoryRepositoryPort;
    private final RestaurantSearchQueryPort restaurantSearchQueryPort;

    public RestaurantSearchQueryService(
            RegionRepositoryPort regionRepositoryPort,
            FoodCategoryRepositoryPort foodCategoryRepositoryPort,
            RestaurantSearchQueryPort restaurantSearchQueryPort) {
        this.regionRepositoryPort = regionRepositoryPort;
        this.foodCategoryRepositoryPort = foodCategoryRepositoryPort;
        this.restaurantSearchQueryPort = restaurantSearchQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantSearchResult search(SearchRestaurantsCommand command) {
        String normalizedQuery = normalizeQuery(command.query());
        UUID regionId = resolveRegionId(command.district());
        UUID foodCategoryId = resolveFoodCategoryId(command.category());
        UUID creatorId = resolveCreatorId(command.creatorId());

        RestaurantSearchQueryResult queryResult = restaurantSearchQueryPort.search(
                new RestaurantSearchCriteria(
                        normalizedQuery, regionId, foodCategoryId, creatorId, command.page(), command.size()));

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

    private String normalizeQuery(String rawQuery) {
        if (rawQuery == null) {
            return null;
        }
        String trimmed = rawQuery.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_QUERY_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "query", "최대 100자까지 입력할 수 있습니다.");
        }
        return trimmed;
    }

    private UUID resolveRegionId(String district) {
        if (district == null) {
            return null;
        }
        Region region = regionRepositoryPort.findByName(district)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_FIELD_VALUE, "district", "서울특별시 자치구 이름이 아닙니다."));
        return region.getId();
    }

    private UUID resolveFoodCategoryId(String category) {
        if (category == null) {
            return null;
        }
        FoodCategory foodCategory = foodCategoryRepositoryPort.findByName(category)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_FIELD_VALUE, "category", "지원하는 대표 음식 카테고리가 아닙니다."));
        return foodCategory.getId();
    }

    private UUID resolveCreatorId(String creatorId) {
        if (creatorId == null) {
            return null;
        }
        UUID parsedId;
        try {
            parsedId = UUID.fromString(creatorId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER, "creatorId", "식별자 형식이 올바르지 않습니다.");
        }
        if (!restaurantSearchQueryPort.existsPublicCreator(parsedId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_FIELD_VALUE, "creatorId", "존재하지 않거나 공개되지 않은 유튜버입니다.");
        }
        return parsedId;
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
