package com.masiton.security.infrastructure;

import java.util.Set;

/**
 * `/api/restaurants/` 하위 경로가 맛집 상세(`/api/restaurants/{restaurantId}`)인지 판정한다.
 *
 * <p>보안 설정과 회원 세션 필터가 같은 판정을 각자 구현하면 한쪽만 갱신되는 사고가 난다. 실제로
 * `/api/restaurants/popular` 추가 시 상세 조회로 오분류돼 완전 공개 조회에 회원 세션 확인이 붙었고
 * (docs/troubleshooting/pr-139-popular-restaurant-security-boundary.md), 같은 원인으로
 * `/api/restaurants/map-points`도 오분류돼 있었다. 두 방어선이 이 클래스 하나를 호출하도록 모아
 * 리터럴 경로를 한 곳에만 등록하게 한다.
 *
 * <p>`/api/restaurants/` 하위에 식별자가 아닌 리터럴 경로를 추가하면 {@link #NON_IDENTIFIER_SEGMENTS}에
 * 반드시 등록한다. 등록하지 않으면 그 경로는 맛집 상세로 취급돼 선택적 회원 인증과 세션 확인 대상이 된다.
 */
public final class RestaurantPathClassifier {

    private static final String DETAIL_PREFIX = "/api/restaurants/";

    /**
     * `/api/restaurants/` 하위의 리터럴 경로 세그먼트. 맛집 식별자가 아니며 완전 공개 조회다.
     * 근거: docs/05-specs/api/discovery/popular-restaurant-api.md,
     * docs/05-specs/api/discovery/map-discovery-api.md,
     * docs/05-specs/api/discovery/natural-language-restaurant-discovery-api.md
     */
    private static final Set<String> NON_IDENTIFIER_SEGMENTS =
            Set.of("popular", "map-points", "natural-language-search");

    private RestaurantPathClassifier() {
    }

    /**
     * 요청 경로가 맛집 상세 조회인지 판정한다. 리터럴 공개 경로와 하위 경로를 가진 요청은 제외한다.
     */
    public static boolean isRestaurantDetailPath(String requestUri) {
        if (requestUri == null || !requestUri.startsWith(DETAIL_PREFIX)) {
            return false;
        }
        String segment = requestUri.substring(DETAIL_PREFIX.length());
        return !segment.isEmpty() && !segment.contains("/") && !NON_IDENTIFIER_SEGMENTS.contains(segment);
    }

    /**
     * 요청 경로가 `/api/restaurants/` 하위의 완전 공개 리터럴 경로인지 판정한다.
     */
    public static boolean isNonIdentifierPublicPath(String requestUri) {
        if (requestUri == null || !requestUri.startsWith(DETAIL_PREFIX)) {
            return false;
        }
        return NON_IDENTIFIER_SEGMENTS.contains(requestUri.substring(DETAIL_PREFIX.length()));
    }
}
