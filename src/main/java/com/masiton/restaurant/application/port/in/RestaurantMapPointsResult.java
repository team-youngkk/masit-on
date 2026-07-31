package com.masiton.restaurant.application.port.in;

import java.util.List;

public record RestaurantMapPointsResult(ResultStatus resultStatus, int limit, List<RestaurantMapPointSummary> items) {

    public enum ResultStatus {
        AVAILABLE,
        TOO_MANY_RESULTS
    }
}
