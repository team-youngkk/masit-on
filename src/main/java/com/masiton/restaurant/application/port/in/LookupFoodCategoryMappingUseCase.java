package com.masiton.restaurant.application.port.in;

import java.util.Optional;
import java.util.UUID;

/**
 * 다른 도메인이 {@code BR-AIEXTRACT-010} 대표 음식 카테고리 자동 선정에 필요한
 * {@code food_category_mapping}·{@code food_category} 기준정보를 조회할 때 쓰는 공개 계약이다.
 * {@code dependency-rules.md} 3절에 따라 orchestration은 이 domain의 {@code port.in}만 호출하고
 * {@code port.out}(Infrastructure Adapter용)도, {@code domain.model} Aggregate도 직접 참조하지
 * 않는다. 대조 순서(EXACT 우선, priority 오름차순)·같은 순위 복수 일치 충돌 판정은 이 domain이
 * 소유하는 {@code resolveByKakaoPlaceCategory}·{@code resolveByMenuExpression} 안에서 수행하고,
 * 호출자에게는 {@link MappingResolution}이라는 최소 결과만 넘긴다.
 */
public interface LookupFoodCategoryMappingUseCase {

    /** Kakao 장소 분류 표현(1순위 근거)을 활성 {@code food_category_mapping}에 대조한다. */
    MappingResolution resolveByKakaoPlaceCategory(String kakaoPlaceCategory);

    /** AI 메뉴 후보 표현(2순위 근거)을 활성 {@code food_category_mapping}에 대조한다. */
    MappingResolution resolveByMenuExpression(String menuExpression);

    Optional<String> findCategoryName(UUID foodCategoryId);

    /**
     * {@code BR-AIEXTRACT-011} 관리자 보충 입력(카테고리 보정)이 활성 기준정보를 가리키는지
     * 검증할 때 쓴다. 비활성·미존재 값은 빈 값을 반환한다.
     */
    Optional<String> findActiveCategoryName(UUID foodCategoryId);

    /** {@code food_category_mapping} 대조 결과 하나를 표현한다. 새 값은 추가하지 않는다. */
    enum MappingOutcome {
        /** 정확히 하나의 카테고리로 일치했다. */
        MATCHED,
        /** 일치하는 활성 행이 없다. */
        NONE,
        /** 같은 순위에서 서로 다른 카테고리로 일치해 임의로 고를 수 없다. */
        CONFLICT
    }

    /** 대조에 사용한 매핑 행 식별자와 대응 카테고리다. orchestration 내부 판정 결과로만 전달한다. */
    record ResolvedMapping(UUID mappingId, UUID foodCategoryId) {
    }

    record MappingResolution(MappingOutcome outcome, ResolvedMapping match) {

        public MappingResolution {
            if ((outcome == MappingOutcome.MATCHED) != (match != null)) {
                throw new IllegalArgumentException("match must be present only when outcome is MATCHED.");
            }
        }

        public static MappingResolution none() {
            return new MappingResolution(MappingOutcome.NONE, null);
        }

        public static MappingResolution conflict() {
            return new MappingResolution(MappingOutcome.CONFLICT, null);
        }

        public static MappingResolution matched(UUID mappingId, UUID foodCategoryId) {
            return new MappingResolution(MappingOutcome.MATCHED, new ResolvedMapping(mappingId, foodCategoryId));
        }
    }
}
