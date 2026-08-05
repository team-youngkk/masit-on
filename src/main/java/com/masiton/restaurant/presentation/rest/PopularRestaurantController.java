package com.masiton.restaurant.presentation.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.port.in.PopularRestaurantUseCase;

import jakarta.servlet.http.HttpServletRequest;

/**
 * API-POPULAR-001 인기 맛집 조회. 페이지·기간·정렬·신호 선택 쿼리를 지원하지 않으므로
 * filtering-contract.md 2절에 따라 쿼리 파라미터가 하나라도 오면 400 INVALID_REQUEST로 거부한다.
 * 근거: docs/05-specs/api/discovery/popular-restaurant-api.md
 */
@RestController
@RequestMapping("/api/restaurants/popular")
public class PopularRestaurantController {

    private final PopularRestaurantUseCase popularRestaurantUseCase;

    public PopularRestaurantController(PopularRestaurantUseCase popularRestaurantUseCase) {
        this.popularRestaurantUseCase = popularRestaurantUseCase;
    }

    @GetMapping
    public PopularRestaurantResponse findPopularRestaurants(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return PopularRestaurantResponse.from(popularRestaurantUseCase.findPopularRestaurants());
    }
}
