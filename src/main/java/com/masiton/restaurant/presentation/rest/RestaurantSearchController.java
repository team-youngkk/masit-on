package com.masiton.restaurant.presentation.rest;

import java.util.List;
import java.util.Set;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.port.in.RestaurantSearchResult;
import com.masiton.restaurant.application.port.in.SearchRestaurantsCommand;
import com.masiton.restaurant.application.port.in.SearchRestaurantsUseCase;

/**
 * API-DISCOVERY-001 맛집 목록 및 조건 검색.
 * 근거: docs/05-specs/api/discovery/restaurant-discovery-api.md,
 * docs/05-specs/api/common/{filtering-contract.md, pagination-contract.md}
 */
@RestController
@RequestMapping("/api/restaurants")
public class RestaurantSearchController {

    private static final Set<String> KNOWN_FIELDS =
            Set.of("query", "district", "category", "creatorId", "page", "size");
    private static final Set<String> ARRAY_STYLE_FILTER_FIELDS = Set.of("district", "category", "creatorId");
    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50);

    private final SearchRestaurantsUseCase searchRestaurantsUseCase;

    public RestaurantSearchController(SearchRestaurantsUseCase searchRestaurantsUseCase) {
        this.searchRestaurantsUseCase = searchRestaurantsUseCase;
    }

    @GetMapping
    public RestaurantSearchResponse search(@RequestParam MultiValueMap<String, String> queryParams) {
        validateParamNames(queryParams);
        validateSingleValue(queryParams);
        validateNoCommaList(queryParams);

        String query = queryParams.getFirst("query");
        String district = queryParams.getFirst("district");
        String category = queryParams.getFirst("category");
        String creatorId = queryParams.getFirst("creatorId");
        int page = parsePage(queryParams.getFirst("page"));
        int size = parseSize(queryParams.getFirst("size"));

        RestaurantSearchResult result = searchRestaurantsUseCase.search(
                new SearchRestaurantsCommand(query, district, category, creatorId, page, size));

        return RestaurantSearchResponse.from(result);
    }

    private void validateParamNames(MultiValueMap<String, String> queryParams) {
        for (String name : queryParams.keySet()) {
            if (KNOWN_FIELDS.contains(name)) {
                continue;
            }
            String arrayStyleField = name.endsWith("[]") ? name.substring(0, name.length() - 2) : null;
            if (arrayStyleField != null && ARRAY_STYLE_FILTER_FIELDS.contains(arrayStyleField)) {
                throw new BusinessException(
                        ErrorCode.INVALID_FIELD_VALUE, arrayStyleField, "값은 한 번만 지정할 수 있습니다.");
            }
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void validateSingleValue(MultiValueMap<String, String> queryParams) {
        for (String field : KNOWN_FIELDS) {
            List<String> values = queryParams.get(field);
            if (values != null && values.size() > 1) {
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, "값은 한 번만 지정할 수 있습니다.");
            }
        }
    }

    private void validateNoCommaList(MultiValueMap<String, String> queryParams) {
        for (String field : ARRAY_STYLE_FILTER_FIELDS) {
            String value = queryParams.getFirst(field);
            if (value != null && value.contains(",")) {
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, "값은 한 번만 지정할 수 있습니다.");
            }
        }
    }

    private int parsePage(String raw) {
        if (raw == null) {
            return 1;
        }
        int page = parseInt(raw, "page");
        if (page < 1) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "page", "1 이상의 값만 허용합니다.");
        }
        return page;
    }

    private int parseSize(String raw) {
        if (raw == null) {
            return 20;
        }
        int size = parseInt(raw, "size");
        if (!ALLOWED_SIZES.contains(size)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "size", "10, 20, 50 중 하나만 허용합니다.");
        }
        return size;
    }

    private int parseInt(String raw, String field) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, "정수 값만 허용합니다.");
        }
    }
}
