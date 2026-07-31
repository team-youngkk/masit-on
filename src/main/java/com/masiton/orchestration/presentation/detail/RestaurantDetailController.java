package com.masiton.orchestration.presentation.detail;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.orchestration.application.port.in.GetRestaurantDetailQuery;
import com.masiton.orchestration.application.query.RestaurantDetailResult;

/**
 * API-DETAIL-001 맛집 상세 조회의 입력 Adapter다. 식별자 형식 검증과 HTTP 변환만 수행하고
 * 조합 로직은 Application 입력 Port({@link GetRestaurantDetailQuery})에 위임한다.
 * dependency-rules.md 3절: Controller는 구현 클래스가 아닌 입력 Port에만 의존한다.
 */
@RestController
public class RestaurantDetailController {

    private final GetRestaurantDetailQuery getRestaurantDetailQuery;

    public RestaurantDetailController(GetRestaurantDetailQuery getRestaurantDetailQuery) {
        this.getRestaurantDetailQuery = getRestaurantDetailQuery;
    }

    @GetMapping("/api/restaurants/{restaurantId}")
    public RestaurantDetailResponse getRestaurantDetail(@PathVariable String restaurantId) {
        UUID id = parseRestaurantId(restaurantId);
        RestaurantDetailResult result = getRestaurantDetailQuery.getRestaurantDetail(id);
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
