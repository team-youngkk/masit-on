package com.masiton.restaurant.presentation.rest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.port.in.RestaurantMapPointsResult;
import com.masiton.restaurant.application.port.in.SearchRestaurantMapPointsCommand;
import com.masiton.restaurant.application.port.in.SearchRestaurantMapPointsUseCase;
import com.masiton.restaurant.infrastructure.web.MapClientAddressResolver;

/**
 * API-MAP-001 지도 영역 맛집 조회.
 * 근거: docs/05-specs/api/discovery/map-discovery-api.md,
 * docs/05-specs/api/common/{coordinate-contract.md, filtering-contract.md}
 */
@RestController
@RequestMapping("/api/restaurants")
public class RestaurantMapPointsController {

    private static final Set<String> KNOWN_FIELDS =
            Set.of("south", "west", "north", "east", "query", "district", "category", "creatorId");
    private static final Set<String> ARRAY_STYLE_FILTER_FIELDS = Set.of("district", "category", "creatorId");
    private static final Set<String> REQUIRED_BOUND_FIELDS = Set.of("south", "west", "north", "east");

    private final SearchRestaurantMapPointsUseCase searchRestaurantMapPointsUseCase;
    private final MapClientAddressResolver clientAddressResolver;

    public RestaurantMapPointsController(
            SearchRestaurantMapPointsUseCase searchRestaurantMapPointsUseCase,
            MapClientAddressResolver clientAddressResolver) {
        this.searchRestaurantMapPointsUseCase = searchRestaurantMapPointsUseCase;
        this.clientAddressResolver = clientAddressResolver;
    }

    @GetMapping("/map-points")
    public RestaurantMapPointsResponse mapPoints(
            @RequestParam MultiValueMap<String, String> queryParams, HttpServletRequest request) {
        validateParamNames(queryParams);
        validateSingleValue(queryParams);
        validateNoCommaList(queryParams);
        validateRequiredBounds(queryParams);

        BigDecimal south = parseDecimal(queryParams.getFirst("south"), "south");
        BigDecimal west = parseDecimal(queryParams.getFirst("west"), "west");
        BigDecimal north = parseDecimal(queryParams.getFirst("north"), "north");
        BigDecimal east = parseDecimal(queryParams.getFirst("east"), "east");

        RestaurantMapPointsResult result = searchRestaurantMapPointsUseCase.search(new SearchRestaurantMapPointsCommand(
                south, west, north, east,
                queryParams.getFirst("query"),
                queryParams.getFirst("district"),
                queryParams.getFirst("category"),
                queryParams.getFirst("creatorId"),
                clientAddressResolver.resolve(request)));

        return RestaurantMapPointsResponse.from(result);
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

    private void validateRequiredBounds(MultiValueMap<String, String> queryParams) {
        for (String field : REQUIRED_BOUND_FIELDS) {
            if (queryParams.getFirst(field) == null) {
                throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, field, field + "은 필수입니다.");
            }
        }
    }

    private BigDecimal parseDecimal(String raw, String field) {
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, "decimal 값만 허용합니다.");
        }
    }
}
