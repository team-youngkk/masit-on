package com.masiton.restaurant.presentation.rest;

import java.util.List;

import com.masiton.restaurant.application.port.in.RestaurantFilterOptions;

public record RestaurantFilterOptionsResponse(
        List<String> districts,
        List<String> categories) {

    public static RestaurantFilterOptionsResponse from(RestaurantFilterOptions options) {
        return new RestaurantFilterOptionsResponse(options.districts(), options.categories());
    }
}
