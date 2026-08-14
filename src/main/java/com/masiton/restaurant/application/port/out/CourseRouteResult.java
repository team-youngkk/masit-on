package com.masiton.restaurant.application.port.out;

import java.util.List;

/**
 * legs는 인접 stop 쌍 순서대로 담긴다.
 */
public record CourseRouteResult(List<CourseRouteLeg> legs) {

    public CourseRouteResult {
        legs = List.copyOf(legs);
    }
}
