package com.masiton.restaurant.application;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.restaurant.application.port.in.RestaurantRegistrationUseCase;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.application.port.out.PlaceVerificationPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.application.port.out.RestaurantRepositoryPort;
import com.masiton.restaurant.application.port.out.VerifiedPlace;
import com.masiton.restaurant.domain.model.FoodCategory;
import com.masiton.restaurant.domain.model.LifecycleStatus;
import com.masiton.restaurant.domain.model.PublicationStatus;
import com.masiton.restaurant.domain.model.Region;
import com.masiton.restaurant.domain.model.Restaurant;
import com.masiton.security.application.AcquiredConfirmationToken;
import com.masiton.security.application.IssuedConfirmationToken;
import com.masiton.security.application.port.in.ConfirmationTokenUseCase;
import com.masiton.security.domain.model.ConfirmationTokenResourceType;
import com.masiton.security.domain.model.ConfirmationTokenStatus;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("맛집 등록 애플리케이션 서비스")
class RestaurantRegistrationServiceTest {

    private final PlaceVerificationPort placeVerificationPort = mock(PlaceVerificationPort.class);
    private final RestaurantRepositoryPort restaurantRepository = mock(RestaurantRepositoryPort.class);
    private final RegionRepositoryPort regionRepository = mock(RegionRepositoryPort.class);
    private final FoodCategoryRepositoryPort foodCategoryRepository = mock(FoodCategoryRepositoryPort.class);
    private final ConfirmationTokenUseCase confirmationTokenUseCase = mock(ConfirmationTokenUseCase.class);
    private final RestaurantRegistrationService service = new RestaurantRegistrationService(
            placeVerificationPort,
            restaurantRepository,
            regionRepository,
            foodCategoryRepository,
            confirmationTokenUseCase,
            new ObjectMapper());

    private final UUID adminId = UUID.randomUUID();
    private final UUID regionId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @Test
    @DisplayName("정상 미리보기는 핵심 Entity를 저장하지 않고 확인 Token을 발급한다")
    void 미리보기_정상장소확인_READY토큰발급하고맛집저장하지않는다() {
        // given
        prepareReferences();
        when(placeVerificationPort.verify(any(), any(), any())).thenReturn(Optional.of(verifiedPlace()));
        when(restaurantRepository.findByKakaoPlaceId("place-1")).thenReturn(Optional.empty());
        when(confirmationTokenUseCase.issue(any())).thenReturn(
                new IssuedConfirmationToken("opaque-token", OffsetDateTime.parse("2026-07-27T12:10:00Z")));

        // when
        RestaurantRegistrationUseCase.RestaurantPreviewResult result = service.preview(command());

        // then
        assertThat(result.decision()).isEqualTo(RestaurantRegistrationUseCase.RestaurantPreviewResult.Decision.READY);
        assertThat(result.confirmationToken()).isEqualTo("opaque-token");
        assertThat(result.candidate().district()).isEqualTo("마포구");
        assertThat(result.candidate().category()).isEqualTo("한식");
        verify(restaurantRepository, never()).insertIfAbsent(any());
    }

    @Test
    @DisplayName("동일 Kakao 장소는 미리보기에서 기존 맛집을 반환하고 Token을 만들지 않는다")
    void 미리보기_동일카카오장소_DUPLICATE기존자원반환하고토큰발급하지않는다() {
        // given
        prepareReferences();
        when(placeVerificationPort.verify(any(), any(), any())).thenReturn(Optional.of(verifiedPlace()));
        Restaurant existing = restaurant(UUID.randomUUID(), "place-1");
        when(restaurantRepository.findByKakaoPlaceId("place-1")).thenReturn(Optional.of(existing));

        // when
        RestaurantRegistrationUseCase.RestaurantPreviewResult result = service.preview(command());

        // then
        assertThat(result.decision()).isEqualTo(RestaurantRegistrationUseCase.RestaurantPreviewResult.Decision.DUPLICATE);
        assertThat(result.confirmationToken()).isNull();
        assertThat(result.existingResource()).isEqualTo(
                new RestaurantRegistrationUseCase.ExistingRestaurant(
                        existing.getId(), existing.getName(), existing.getRoadAddress()));
        verify(confirmationTokenUseCase, never()).issue(any());
    }

    @Test
    @DisplayName("카카오 검증 실패는 EXTERNAL_SERVICE_ERROR로 변환한다")
    void 미리보기_외부검증실패_EXTERNAL_SERVICE_ERROR() {
        prepareReferences();
        when(placeVerificationPort.verify(any(), any(), any())).thenThrow(new PlaceVerificationFailedException());

        assertThatThrownBy(() -> service.preview(command()))
                .isInstanceOf(com.masiton.common.web.BusinessException.class)
                .extracting(exception -> ((com.masiton.common.web.BusinessException) exception).code())
                .isEqualTo(com.masiton.common.web.ErrorCode.EXTERNAL_SERVICE_ERROR.name());
    }

    @Test
    @DisplayName("발급된 Snapshot을 확정하면 공개 맛집과 CREATED 완료 상태를 같은 흐름에서 만든다")
    void 확정_발급토큰_맛집생성과토큰완료처리한다() {
        // given
        UUID tokenId = UUID.randomUUID();
        when(confirmationTokenUseCase.acquire(eq("opaque-token"), eq(adminId),
                eq(ConfirmationTokenResourceType.RESTAURANT)))
                .thenReturn(acquired(tokenId, ConfirmationTokenStatus.ISSUED, null));
        when(restaurantRepository.findByKakaoPlaceId("place-1")).thenReturn(Optional.empty());
        when(restaurantRepository.insertIfAbsent(any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        // when
        RestaurantRegistrationUseCase.RestaurantCreationResult result = service.create(
                new RestaurantRegistrationUseCase.RestaurantCreateCommand(adminId, "opaque-token"));

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.duplicate()).isFalse();
        assertThat(result.restaurant().district()).isEqualTo("마포구");
        verify(confirmationTokenUseCase).completeCreated(eq(tokenId), eq(result.restaurant().id()));
    }

    @Test
    @DisplayName("완료된 CREATED Token 재시도는 새 맛집을 만들지 않고 같은 후보를 반환한다")
    void 확정_CREATED토큰재시도_기존맛집을반환하고새저장을하지않는다() {
        // given
        UUID tokenId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        when(confirmationTokenUseCase.acquire(eq("opaque-token"), eq(adminId),
                eq(ConfirmationTokenResourceType.RESTAURANT)))
                .thenReturn(acquired(tokenId, ConfirmationTokenStatus.CREATED, restaurantId));
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant(restaurantId, "place-1")));

        // when
        RestaurantRegistrationUseCase.RestaurantCreationResult result = service.create(
                new RestaurantRegistrationUseCase.RestaurantCreateCommand(adminId, "opaque-token"));

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.duplicate()).isFalse();
        assertThat(result.restaurant().id()).isEqualTo(restaurantId);
        verify(restaurantRepository, never()).insertIfAbsent(any());
        verify(confirmationTokenUseCase, never()).completeCreated(any(), any());
    }

    @Test
    @DisplayName("확정 직전 다른 요청이 동일 장소를 만들면 Token을 DUPLICATE로 완료한다")
    void 확정_동시중복_기존맛집으로DUPLICATE완료처리한다() {
        // given
        UUID tokenId = UUID.randomUUID();
        Restaurant concurrent = restaurant(UUID.randomUUID(), "place-1");
        when(confirmationTokenUseCase.acquire(eq("opaque-token"), eq(adminId),
                eq(ConfirmationTokenResourceType.RESTAURANT)))
                .thenReturn(acquired(tokenId, ConfirmationTokenStatus.ISSUED, null));
        when(restaurantRepository.findByKakaoPlaceId("place-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrent));
        when(restaurantRepository.insertIfAbsent(any())).thenReturn(Optional.empty());

        // when
        RestaurantRegistrationUseCase.RestaurantCreationResult result = service.create(
                new RestaurantRegistrationUseCase.RestaurantCreateCommand(adminId, "opaque-token"));

        // then
        assertThat(result.duplicate()).isTrue();
        assertThat(result.restaurant().id()).isEqualTo(concurrent.getId());
        verify(confirmationTokenUseCase).completeDuplicate(tokenId, concurrent.getId());
    }

    @Test
    @DisplayName("카카오가 서울 형식이 아닌 도로명주소를 반환하면 외부 서비스 오류로 변환한다")
    void 미리보기_카카오주소형식오류_EXTERNAL_SERVICE_ERROR() {
        prepareReferences();
        when(placeVerificationPort.verify(any(), any(), any())).thenReturn(Optional.of(new VerifiedPlace(
                "place-1",
                "맛잇온 테스트 식당",
                "https://place.map.kakao.com/place-1",
                "부산광역시 해운대구 해운대로 1",
                "02-000-0000")));

        assertThatThrownBy(() -> service.preview(command()))
                .isInstanceOf(com.masiton.common.web.BusinessException.class)
                .extracting(exception -> ((com.masiton.common.web.BusinessException) exception).code())
                .isEqualTo(com.masiton.common.web.ErrorCode.EXTERNAL_SERVICE_ERROR.name());
    }

    private void prepareReferences() {
        when(regionRepository.findByName("마포구")).thenReturn(Optional.of(new Region(
                regionId, "SEOUL_MAPO", "마포구", (short) 14, true, null, null)));
        when(foodCategoryRepository.findByName("한식")).thenReturn(Optional.of(new FoodCategory(
                categoryId, "KOREAN", "한식", (short) 1, true, null, null)));
    }

    private RestaurantRegistrationUseCase.RestaurantPreviewCommand command() {
        return new RestaurantRegistrationUseCase.RestaurantPreviewCommand(
                adminId,
                "입력 맛집명",
                "https://place.map.kakao.com/place-1",
                "서울특별시 마포구 월드컵로 1",
                null,
                "02-000-0000",
                "한식");
    }

    private VerifiedPlace verifiedPlace() {
        return new VerifiedPlace(
                "place-1",
                "맛잇온 테스트 식당",
                "https://place.map.kakao.com/place-1",
                "서울특별시 마포구 월드컵로 1",
                "02-000-0000");
    }

    private AcquiredConfirmationToken acquired(UUID tokenId, ConfirmationTokenStatus status, UUID resultResourceId) {
        return new AcquiredConfirmationToken(
                tokenId,
                (short) 1,
                "place-1",
                snapshotJson(),
                status,
                resultResourceId);
    }

    private String snapshotJson() {
        return """
                {"regionId":"%s","foodCategoryId":"%s","kakaoPlaceId":"place-1",
                "name":"맛잇온 테스트 식당","district":"마포구","category":"한식",
                "kakaoPlaceUrl":"https://place.map.kakao.com/place-1",
                "roadAddress":"서울특별시 마포구 월드컵로 1","detailAddress":null,"phoneNumber":"02-000-0000"}
                """.formatted(regionId, categoryId);
    }

    private Restaurant restaurant(UUID id, String placeId) {
        return new Restaurant(
                id, regionId, categoryId, "기존 맛집", placeId,
                "https://place.map.kakao.com/" + placeId,
                "서울특별시 마포구 월드컵로 1", null, "02-000-0000",
                PublicationStatus.PUBLIC, LifecycleStatus.ACTIVE, null, null, null);
    }
}
