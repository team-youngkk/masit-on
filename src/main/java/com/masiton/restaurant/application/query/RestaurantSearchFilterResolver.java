package com.masiton.restaurant.application.query;

import java.util.Set;
import java.util.UUID;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.domain.model.FoodCategory;
import com.masiton.restaurant.domain.model.Region;
import com.masiton.visit.application.port.in.CreatorRestaurantCandidates;
import com.masiton.visit.application.port.in.FindDistinctValidRestaurantIdsByCreatorQuery;

/**
 * 맛집 목록 검색과 지도 영역 조회가 공유하는 query·district·category·creatorId 검증·정규화 로직이다.
 * BR-SEARCH-001~009 순서(검색어 정규화 -&gt; district -&gt; category -&gt; creatorId)를 따른다.
 */
class RestaurantSearchFilterResolver {

    private static final int MAX_QUERY_LENGTH = 100;

    private final RegionRepositoryPort regionRepositoryPort;
    private final FoodCategoryRepositoryPort foodCategoryRepositoryPort;
    private final FindDistinctValidRestaurantIdsByCreatorQuery findRestaurantIdsByCreatorQuery;

    RestaurantSearchFilterResolver(
            RegionRepositoryPort regionRepositoryPort,
            FoodCategoryRepositoryPort foodCategoryRepositoryPort,
            FindDistinctValidRestaurantIdsByCreatorQuery findRestaurantIdsByCreatorQuery) {
        this.regionRepositoryPort = regionRepositoryPort;
        this.foodCategoryRepositoryPort = foodCategoryRepositoryPort;
        this.findRestaurantIdsByCreatorQuery = findRestaurantIdsByCreatorQuery;
    }

    String normalizeQuery(String rawQuery) {
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

    UUID resolveRegionId(String district) {
        if (district == null) {
            return null;
        }
        Region region = regionRepositoryPort.findByName(district)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_FIELD_VALUE, "district", "서울특별시 자치구 이름이 아닙니다."));
        return region.getId();
    }

    UUID resolveFoodCategoryId(String category) {
        if (category == null) {
            return null;
        }
        FoodCategory foodCategory = foodCategoryRepositoryPort.findByName(category)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_FIELD_VALUE, "category", "지원하는 대표 음식 카테고리가 아닙니다."));
        return foodCategory.getId();
    }

    Set<UUID> resolveCandidateRestaurantIds(String creatorId) {
        if (creatorId == null) {
            return null;
        }
        UUID parsedId;
        try {
            parsedId = UUID.fromString(creatorId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER, "creatorId", "식별자 형식이 올바르지 않습니다.");
        }
        CreatorRestaurantCandidates candidates =
                findRestaurantIdsByCreatorQuery.findDistinctValidRestaurantIdsByCreator(parsedId);
        if (!candidates.creatorPublic()) {
            throw new BusinessException(
                    ErrorCode.INVALID_FIELD_VALUE, "creatorId", "존재하지 않거나 공개되지 않은 유튜버입니다.");
        }
        return candidates.restaurantIds();
    }
}
