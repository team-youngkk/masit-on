package com.masiton.restaurant.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.masiton.restaurant.domain.model.FoodCategoryMapping;
import com.masiton.restaurant.domain.model.FoodCategoryMappingSourceType;

/**
 * FoodCategoryMapping 저장소에 대한 Application 출력 Port다.
 * Application은 이 인터페이스에만 의존하고 Infrastructure Adapter가 구현한다.
 */
public interface FoodCategoryMappingRepositoryPort {

    FoodCategoryMapping save(FoodCategoryMapping foodCategoryMapping);

    Optional<FoodCategoryMapping> findById(UUID id);

    /**
     * 주어진 {@code sourceType}의 활성 매핑 행을 대조 순서(EXACT 우선, 그 안에서 priority 오름차순)로
     * 반환한다. 호출자는 이 순서를 유지한 채 순회하며 EXACT는 정규화 문자열 완전일치, PARTIAL은
     * 부분 문자열 포함으로 대조한다({@code BR-AIEXTRACT-010}).
     */
    List<FoodCategoryMapping> findActiveBySourceTypeOrderByMatchTypeThenPriority(
            FoodCategoryMappingSourceType sourceType);
}
