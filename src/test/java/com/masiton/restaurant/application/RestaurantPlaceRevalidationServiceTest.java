package com.masiton.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.masiton.restaurant.application.port.in.RestaurantPlaceRevalidationUseCase;
import com.masiton.restaurant.application.port.out.KakaoPlaceRevalidationPort;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore.ClaimedRestaurant;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore.Decision;
import com.masiton.restaurant.application.port.out.VerifiedPlace;
import com.masiton.restaurant.domain.model.LifecycleStatus;
import com.masiton.restaurant.domain.model.PublicationStatus;
import com.masiton.restaurant.domain.model.Restaurant;

class RestaurantPlaceRevalidationServiceTest {
    private final RestaurantPlaceRevalidationStore store = mock();
    private final KakaoPlaceRevalidationPort kakao = mock();
    private final RestaurantPlaceRevalidationPersistenceService persistence = mock();
    private final RestaurantPlaceRevalidationProperties properties = enabledProperties();
    private final Restaurant restaurant = restaurant();
    private final RestaurantPlaceRevalidationService service = new RestaurantPlaceRevalidationService(store, kakao,
            new RestaurantPlaceRevalidationClaimService(store, properties,
                    Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), ZoneOffset.UTC)), persistence,
            properties, Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), ZoneOffset.UTC));

    private static RestaurantPlaceRevalidationProperties enabledProperties() {
        RestaurantPlaceRevalidationProperties properties = new RestaurantPlaceRevalidationProperties();
        properties.setEnabled(true);
        return properties;
    }

    @Test @DisplayName("동일한 Kakao 결과는 NO_CHANGE로 감사하고 맛집을 수정하지 않는다")
    void 재검증_동일_변경없음() {
        claim(0); when(kakao.verify(any(), any(), any())).thenReturn(KakaoPlaceRevalidationPort.Result.found(place("맛집", "02-111-2222", "서울특별시 강남구 테헤란로 1")));
        var result = service.run(restaurant.getId());
        Decision decision = decision(); assertThat(result).isPresent(); assertThat(decision.outcome().name()).isEqualTo("VERIFIED"); assertThat(decision.correctedRestaurant()).isNull();
    }
    @Test @DisplayName("동일 구의 안전 필드 변경은 AUTO_CORRECTED로 원자 적용한다")
    void 재검증_변경_자동보정() {
        claim(0); when(kakao.verify(any(), any(), any())).thenReturn(KakaoPlaceRevalidationPort.Result.found(place("새 맛집", "02-333-4444", "서울특별시 강남구 역삼로 2")));
        service.run(restaurant.getId()); Decision decision=decision(); assertThat(decision.outcome().name()).isEqualTo("AUTO_CORRECTED"); assertThat(decision.correctedRestaurant().getName()).isEqualTo("새 맛집"); assertThat(decision.correctedRestaurant().getRoadAddress()).isEqualTo("서울특별시 강남구 역삼로 2");
    }

    @ParameterizedTest
    @CsvSource({
            "'서울특별시 강남구 테헤란로 1', '서울 강남구 테헤란로 1'",
            "'서울 강남구 테헤란로 1', '서울특별시 강남구 테헤란로 1'",
            "'부산광역시 해운대구 해운대로 1', '부산 해운대구 해운대로 1'",
            "'부산 해운대구 해운대로 1', '부산광역시 해운대구 해운대로 1'"
    })
    @DisplayName("시도 별칭만 다른 주소는 동일한 주소로 검증한다")
    void 재검증_시도별칭차이_보정하지않음(String currentAddress, String observedAddress) {
        // Given
        Restaurant current = withAddress(currentAddress);
        when(store.claim(eq(current.getId()), any(), any(), any())).thenReturn(Optional.of(
                new ClaimedRestaurant(current, 0, UUID.randomUUID(), "owner")));
        when(persistence.apply(any(), any(), any())).thenReturn(true);
        when(kakao.verify(any(), any(), any())).thenReturn(KakaoPlaceRevalidationPort.Result.found(
                place("맛집", "02-111-2222", observedAddress)));

        // When
        service.run(current.getId());

        // Then
        Decision result = decision();
        assertThat(result.outcome().name()).isEqualTo("VERIFIED");
        assertThat(result.correctedRestaurant()).isNull();
    }
    @Test @DisplayName("Kakao 매칭 실패는 기존 맛집을 수정하지 않고 MATCH_NOT_FOUND로 남긴다")
    void 재검증_매칭실패_원본미변경() {
        claim(0); when(kakao.verify(any(), any(), any())).thenReturn(KakaoPlaceRevalidationPort.Result.of(KakaoPlaceRevalidationPort.Kind.NOT_FOUND));
        service.run(restaurant.getId()); Decision decision=decision(); assertThat(decision.outcome().name()).isEqualTo("MATCH_NOT_FOUND"); assertThat(decision.correctedRestaurant()).isNull();
    }
    @Test @DisplayName("Kakao 429는 quota 오류로 분리해 bounded retry를 기록하고 맛집을 수정하지 않는다")
    void 재검증_429_재시도기록() {
        claim(0); when(kakao.verify(any(), any(), any())).thenReturn(KakaoPlaceRevalidationPort.Result.of(KakaoPlaceRevalidationPort.Kind.QUOTA_EXCEEDED));
        service.run(restaurant.getId()); Decision decision=decision(); assertThat(decision.outcome().name()).isEqualTo("RETRY_SCHEDULED"); assertThat(decision.errorCode()).isEqualTo("HTTP_429"); assertThat(decision.correctedRestaurant()).isNull();
    }
    @Test @DisplayName("외부 예외는 기존 맛집을 수정하지 않고 일반 재시도로 기록한다")
    void 재검증_외부실패_원본미변경() {
        claim(0); when(kakao.verify(any(), any(), any())).thenThrow(new IllegalStateException());
        service.run(restaurant.getId()); Decision decision=decision(); assertThat(decision.outcome().name()).isEqualTo("RETRY_SCHEDULED"); assertThat(decision.errorCode()).isEqualTo("HTTP_5XX"); assertThat(decision.correctedRestaurant()).isNull();
    }
    @Test @DisplayName("맛집 본문이 먼저 변경되면 stale 결과로 폐기하고 예외를 노출하지 않는다")
    void 재검증_맛집본문선변경_stale결과로폐기한다() {
        claim(0);
        when(kakao.verify(any(), any(), any())).thenReturn(KakaoPlaceRevalidationPort.Result.found(
                place("새 맛집", "02-333-4444", "서울특별시 강남구 역삼로 2")));
        when(persistence.apply(any(), any(), any())).thenThrow(new RestaurantPlaceRevalidationStaleException());

        var result = service.run(restaurant.getId());

        assertThat(result).contains(new RestaurantPlaceRevalidationUseCase.Result(
                restaurant.getId(), "STALE_DISCARDED", null));
    }
    @Test
    @DisplayName("다른 시도의 동명 구로 변경되면 자동 보정하지 않는다")
    void 재검증_다른시도동명구_검토대기로남긴다() {
        // Given
        Restaurant current = withAddress("서울특별시 중구 세종대로 1");
        when(store.claim(eq(current.getId()), any(), any(), any())).thenReturn(Optional.of(
                new ClaimedRestaurant(current, 0, UUID.randomUUID(), "owner")));
        when(persistence.apply(any(), any(), any())).thenReturn(true);
        when(kakao.verify(any(), any(), any())).thenReturn(KakaoPlaceRevalidationPort.Result.found(
                place("맛집", "051-111-2222", "부산광역시 중구 중앙대로 1")));
        // When
        service.run(current.getId());
        // Then
        Decision result = decision();
        assertThat(result.outcome().name()).isEqualTo("REVIEW_REQUIRED");
        assertThat(result.correctedRestaurant()).isNull();
    }

    @Test
    @DisplayName("서울 밖 같은 시군구의 전화번호 변경은 안전 보정한다")
    void 재검증_부산동일지역_전화번호를보정한다() {
        // Given
        Restaurant current = withAddress("부산광역시 해운대구 해운대로 1");
        when(store.claim(eq(current.getId()), any(), any(), any())).thenReturn(Optional.of(
                new ClaimedRestaurant(current, 0, UUID.randomUUID(), "owner")));
        when(persistence.apply(any(), any(), any())).thenReturn(true);
        when(kakao.verify(any(), any(), any())).thenReturn(KakaoPlaceRevalidationPort.Result.found(
                place("맛집", "051-333-4444", "부산광역시 해운대구 해운대로 1")));
        // When
        service.run(current.getId());
        // Then
        Decision result = decision();
        assertThat(result.outcome().name()).isEqualTo("AUTO_CORRECTED");
        assertThat(result.correctedRestaurant().getRegionId()).isEqualTo(current.getRegionId());
        assertThat(result.correctedRestaurant().getPhoneNumber()).isEqualTo("051-333-4444");
    }

    private Restaurant withAddress(String address) {
        return new Restaurant(restaurant.getId(), restaurant.getRegionId(), restaurant.getFoodCategoryId(),
                restaurant.getName(), restaurant.getKakaoPlaceId(), restaurant.getKakaoPlaceUrl(), address,
                null, restaurant.getPhoneNumber(), restaurant.getLatitude(), restaurant.getLongitude(),
                PublicationStatus.PUBLIC, LifecycleStatus.ACTIVE, null, null, null);
    }

    private void claim(int attempts) { when(store.claim(eq(restaurant.getId()), any(), any(), any())).thenReturn(Optional.of(new ClaimedRestaurant(restaurant, attempts, UUID.randomUUID(), "owner"))); when(persistence.apply(any(), any(), any())).thenReturn(true); }
    private Decision decision() { var c=org.mockito.ArgumentCaptor.forClass(Decision.class); verify(persistence).apply(any(), c.capture(), any()); return c.getValue(); }
    private VerifiedPlace place(String name,String phone,String address){return new VerifiedPlace("kakao-1",name,"https://place.map.kakao.com/1",address,phone,new BigDecimal("37.500000"),new BigDecimal("127.000000"));}
    private Restaurant restaurant(){return new Restaurant(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"맛집","kakao-1","https://place.map.kakao.com/1","서울특별시 강남구 테헤란로 1",null,"02-111-2222",new BigDecimal("37.500000"),new BigDecimal("127.000000"),PublicationStatus.PUBLIC,LifecycleStatus.ACTIVE,null,null,null);}
}
