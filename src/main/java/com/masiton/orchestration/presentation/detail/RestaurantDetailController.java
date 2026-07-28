package com.masiton.orchestration.presentation.detail;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.orchestration.application.query.RestaurantDetailQueryService;
import com.masiton.orchestration.application.query.RestaurantDetailResult;

/**
 * API-DETAIL-001 맛집 상세 조회의 입력 Adapter다. 식별자 형식 검증과 HTTP 변환만 수행하고
 * 조합 로직은 {@link RestaurantDetailQueryService}에 위임한다.
 */
@RestController
public class RestaurantDetailController {

    private final RestaurantDetailQueryService restaurantDetailQueryService;

    public RestaurantDetailController(RestaurantDetailQueryService restaurantDetailQueryService) {
        this.restaurantDetailQueryService = restaurantDetailQueryService;
    }

    @GetMapping("/api/restaurants/{restaurantId}")
    public RestaurantDetailResponse getRestaurantDetail(@PathVariable String restaurantId) {
        UUID id = parseRestaurantId(restaurantId);
        RestaurantDetailResult result = restaurantDetailQueryService.getRestaurantDetail(id);
        return RestaurantDetailResponse.from(result);
    }

    private UUID parseRestaurantId(String restaurantId) {
        try {
            return UUID.fromString(restaurantId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_IDENTIFIER", "식별자 형식이 올바르지 않습니다.");
        }
    }
}
