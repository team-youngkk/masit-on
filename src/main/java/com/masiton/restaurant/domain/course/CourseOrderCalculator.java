package com.masiton.restaurant.domain.course;

import java.util.ArrayList;
import java.util.List;

/**
 * 코스 방문 순서를 계산한다.
 * 근거: ADR-ROUTE-001 5.3절, API-DISCOVERY-COURSE-001 4절.
 * 첫 번째 입력 stop을 출발점으로 고정하고, 나머지는 좌표 직선거리(haversine) 최근접 이웃 순서로
 * 배치한다. 동률은 restaurantId 문자열 오름차순으로 안정 정렬해 결정론적 순서를 보장한다.
 */
public final class CourseOrderCalculator {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private CourseOrderCalculator() {
    }

    public static List<CourseStop> order(List<CourseStop> stops) {
        if (stops == null || stops.size() < 2) {
            throw new IllegalArgumentException("코스 순서 계산에는 최소 2개의 방문지가 필요합니다.");
        }

        List<CourseStop> remaining = new ArrayList<>(stops.subList(1, stops.size()));
        List<CourseStop> ordered = new ArrayList<>(stops.size());
        ordered.add(stops.get(0));

        CourseStop current = stops.get(0);
        while (!remaining.isEmpty()) {
            CourseStop nearest = nearest(current, remaining);
            ordered.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }

        return List.copyOf(ordered);
    }

    private static CourseStop nearest(CourseStop from, List<CourseStop> candidates) {
        CourseStop nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (CourseStop candidate : candidates) {
            double distance = haversineDistanceMeters(from, candidate);
            if (nearest == null) {
                nearest = candidate;
                nearestDistance = distance;
                continue;
            }

            int comparison = Double.compare(distance, nearestDistance);
            // ADR-ROUTE-001 5.3절: 동률은 restaurantId 오름차순으로 안정 정렬한다.
            if (comparison < 0
                    || (comparison == 0
                            && candidate.restaurantId().toString().compareTo(nearest.restaurantId().toString()) < 0)) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    private static double haversineDistanceMeters(CourseStop from, CourseStop to) {
        double lat1 = Math.toRadians(from.latitude().doubleValue());
        double lat2 = Math.toRadians(to.latitude().doubleValue());
        double deltaLat = Math.toRadians(to.latitude().doubleValue() - from.latitude().doubleValue());
        double deltaLon = Math.toRadians(to.longitude().doubleValue() - from.longitude().doubleValue());

        double sinHalfLat = Math.sin(deltaLat / 2);
        double sinHalfLon = Math.sin(deltaLon / 2);
        double a = sinHalfLat * sinHalfLat + Math.cos(lat1) * Math.cos(lat2) * sinHalfLon * sinHalfLon;
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }
}
