package com.masiton.restaurant.domain.model;

/**
 * food_category_mapping의 대조 대상 근거 유형이다.
 * DB CHECK 제약({@code ck_food_category_mapping__source_type})의 허용값과 이름이 같아야 한다.
 */
public enum FoodCategoryMappingSourceType {

    KAKAO_PLACE_CATEGORY,
    MENU_EXPRESSION
}
