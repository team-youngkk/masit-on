package com.masiton.restaurant.domain.model;

/**
 * food_category_mapping의 pattern 일치 방식이다.
 * DB CHECK 제약({@code ck_food_category_mapping__match_type})의 허용값과 이름이 같아야 한다.
 */
public enum FoodCategoryMappingMatchType {

    EXACT,
    PARTIAL
}
