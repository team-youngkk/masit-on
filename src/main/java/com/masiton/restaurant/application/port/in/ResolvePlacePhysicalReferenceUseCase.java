package com.masiton.restaurant.application.port.in;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code BR-AIEXTRACT-009} 등록 단위 자동 실행이 확정한 Kakao 장소의 물리 필드(장소 식별자,
 * 전화번호, 좌표)와 서울시 자치구 기준 {@code Region}을 조회하는 공개 계약이다.
 *
 * <p>{@code dependency-rules.md} 3절에 따라 orchestration은 이 domain의 {@code port.in}만 호출하고
 * {@code port.out}({@link com.masiton.restaurant.application.port.out.PlaceVerificationPort},
 * {@link com.masiton.restaurant.application.port.out.RegionRepositoryPort})은 직접 호출하지 않는다.
 * 이 use case가 그 경계를 대신한다.</p>
 *
 * <p>대표 음식 카테고리는 이 계약의 책임이 아니다. {@code BR-AIEXTRACT-010} 카테고리 자동 선정은
 * {@link com.masiton.orchestration.application.port.in.ResolveFoodCategoryUseCase}가 별도로
 * 수행하며, 이 use case는 물리 필드만 완전성을 검증해 반환한다.</p>
 */
public interface ResolvePlacePhysicalReferenceUseCase {

    /**
     * @param restaurantName 등록 단위의 상호명 후보(제공자 조회 fallback 키워드로만 쓰인다)
     * @param kakaoPlaceUrl 이미 확정된(또는 관리자가 보충 입력한) Kakao 장소 URL
     * @return 물리 필드가 모두 채워진 장소와 그 장소가 속한 활성 {@code Region}. 조회 실패, 필수
     *         물리 필드 누락, 또는 서울시 자치구를 확인할 수 없는 지역이면 빈 값이다.
     */
    Optional<VerifiedPlacePhysicalReference> resolve(String restaurantName, URI kakaoPlaceUrl);

    record VerifiedPlacePhysicalReference(
            UUID regionId,
            String name,
            String kakaoPlaceId,
            String kakaoPlaceUrl,
            String roadAddress,
            String phoneNumber,
            BigDecimal latitude,
            BigDecimal longitude) {
    }
}
