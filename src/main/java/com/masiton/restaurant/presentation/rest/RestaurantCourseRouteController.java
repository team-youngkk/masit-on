package com.masiton.restaurant.presentation.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.port.in.RecommendRestaurantCourseUseCase;
import com.masiton.restaurant.application.port.in.RestaurantCourseCommand;

import tools.jackson.databind.JsonNode;

/**
 * API-DISCOVERY-COURSE-001 맛집 코스 추천. 인증 없이 공개 접근하는 POST 조회다.
 * 이 컨트롤러는 식별자 형식만 검증한다. 개수·중복·공개 상태·좌표 검증은 Application이 수행한다.
 * 근거: docs/05-specs/api/discovery/restaurant-course-recommendation-api.md
 */
@RestController
@RequestMapping("/api/restaurants/course-routes")
public class RestaurantCourseRouteController {

    private static final Set<String> ALLOWED_REQUEST_FIELDS = Set.of("restaurantIds");

    private final RecommendRestaurantCourseUseCase recommendRestaurantCourseUseCase;

    public RestaurantCourseRouteController(RecommendRestaurantCourseUseCase recommendRestaurantCourseUseCase) {
        this.recommendRestaurantCourseUseCase = recommendRestaurantCourseUseCase;
    }

    @PostMapping
    public RestaurantCourseRouteResponse recommend(@RequestBody(required = false) JsonNode body) {
        RestaurantCourseRouteRequest request = request(body);
        List<String> rawIds = request == null ? null : request.restaurantIds();
        RestaurantCourseCommand command = new RestaurantCourseCommand(identifiers(rawIds));
        return RestaurantCourseRouteResponse.from(recommendRestaurantCourseUseCase.recommend(command));
    }

    private RestaurantCourseRouteRequest request(JsonNode body) {
        if (body == null || body.isNull()) {
            return null;
        }
        if (!body.isObject() || body.propertyNames().stream().anyMatch(name -> !ALLOWED_REQUEST_FIELDS.contains(name))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        JsonNode restaurantIds = body.get("restaurantIds");
        if (restaurantIds == null || restaurantIds.isNull()) {
            return new RestaurantCourseRouteRequest(null);
        }
        if (!restaurantIds.isArray()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode restaurantId : restaurantIds) {
            if (!restaurantId.isTextual()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            values.add(restaurantId.asText());
        }
        return new RestaurantCourseRouteRequest(values);
    }

    private List<UUID> identifiers(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(this::identifier).toList();
    }

    private UUID identifier(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER);
        }
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER);
        }
    }
}
