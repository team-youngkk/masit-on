package com.masiton.restaurant.presentation;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.common.web.ErrorResponse;
import com.masiton.restaurant.application.port.in.RestaurantRegistrationUseCase;
import com.masiton.restaurant.application.port.in.SearchAdminPlaceCandidatesUseCase;

/** 관리자 확인 Token을 이용한 맛집 등록 API다. */
@RestController
@RequestMapping("/api/admin")
public class RestaurantRegistrationController {

    private final RestaurantRegistrationUseCase restaurantRegistrationUseCase;
    private final SearchAdminPlaceCandidatesUseCase searchAdminPlaceCandidatesUseCase;

    public RestaurantRegistrationController(
            RestaurantRegistrationUseCase restaurantRegistrationUseCase,
            SearchAdminPlaceCandidatesUseCase searchAdminPlaceCandidatesUseCase
    ) {
        this.restaurantRegistrationUseCase = restaurantRegistrationUseCase;
        this.searchAdminPlaceCandidatesUseCase = searchAdminPlaceCandidatesUseCase;
    }

    @PostMapping("/restaurant-registration-previews")
    public ResponseEntity<RestaurantPreviewResponse> preview(
            Authentication authentication,
            @RequestBody RestaurantPreviewRequest request
    ) {
        RestaurantRegistrationUseCase.RestaurantPreviewResult result = restaurantRegistrationUseCase.preview(
                new RestaurantRegistrationUseCase.RestaurantPreviewCommand(
                        adminAccountId(authentication),
                        request.name(),
                        request.kakaoPlaceUrl(),
                        request.roadAddress(),
                        request.detailAddress(),
                        request.phoneNumber(),
                        request.category()));
        return ResponseEntity.ok(new RestaurantPreviewResponse(
                result.decision(),
                result.confirmationToken(),
                result.expiresAt(),
                result.candidate() == null ? null : toPreviewCandidate(result.candidate()),
                result.existingResource()));
    }

    @PostMapping("/restaurants")
    public ResponseEntity<?> create(
            Authentication authentication,
            @RequestBody RestaurantCreateRequest request
    ) {
        RestaurantRegistrationUseCase.RestaurantCreationResult result = restaurantRegistrationUseCase.create(
                new RestaurantRegistrationUseCase.RestaurantCreateCommand(
                        adminAccountId(authentication), request.confirmationToken()));
        if (result.duplicate()) {
            RestaurantRegistrationUseCase.RestaurantCandidate restaurant = result.restaurant();
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "DUPLICATE_RESTAURANT",
                            "이미 등록된 맛집입니다.",
                            java.util.List.of(),
                            new RestaurantRegistrationUseCase.ExistingRestaurant(
                                    restaurant.id(), restaurant.name(), restaurant.roadAddress()),
                            MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));
        }
        ResponseEntity.BodyBuilder response = result.created()
                ? ResponseEntity.created(URI.create("/api/restaurants/" + result.restaurant().id()))
                : ResponseEntity.ok();
        return response.body(toRestaurantResponse(result.restaurant()));
    }

    @PostMapping("/restaurant-place-searches")
    public ResponseEntity<PlaceSearchResponse> searchPlaces(
            @RequestBody PlaceSearchRequest request
    ) {
        List<SearchAdminPlaceCandidatesUseCase.PlaceCandidateResult> results = searchAdminPlaceCandidatesUseCase.search(
                new SearchAdminPlaceCandidatesUseCase.SearchAdminPlaceCandidatesCommand(
                        request.name(), request.roadAddressHint()));
        return ResponseEntity.ok(new PlaceSearchResponse(results.stream().map(this::toPlaceSearchItem).toList()));
    }

    private UUID adminAccountId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    public record RestaurantPreviewRequest(
            String name,
            String kakaoPlaceUrl,
            String roadAddress,
            String detailAddress,
            String phoneNumber,
            String category) {
    }

    public record RestaurantCreateRequest(String confirmationToken) {
    }

    public record RestaurantPreviewResponse(
            RestaurantRegistrationUseCase.RestaurantPreviewResult.Decision decision,
            String confirmationToken,
            java.time.OffsetDateTime expiresAt,
            RestaurantPreviewCandidate candidate,
            RestaurantRegistrationUseCase.ExistingRestaurant existingResource) {
    }

    public record RestaurantPreviewCandidate(
            String name,
            String district,
            String category,
            String roadAddress,
            String detailAddress,
            String phoneNumber,
            String kakaoPlaceUrl) {
    }

    public record RestaurantResponse(
            UUID id,
            String name,
            String district,
            String category,
            String roadAddress,
            String detailAddress,
            String phoneNumber,
            String kakaoPlaceUrl) {
    }

    private RestaurantPreviewCandidate toPreviewCandidate(
            RestaurantRegistrationUseCase.RestaurantCandidate candidate
    ) {
        return new RestaurantPreviewCandidate(
                candidate.name(),
                candidate.district(),
                candidate.category(),
                candidate.roadAddress(),
                candidate.detailAddress(),
                candidate.phoneNumber(),
                candidate.kakaoPlaceUrl());
    }

    private RestaurantResponse toRestaurantResponse(RestaurantRegistrationUseCase.RestaurantCandidate candidate) {
        return new RestaurantResponse(
                candidate.id(),
                candidate.name(),
                candidate.district(),
                candidate.category(),
                candidate.roadAddress(),
                candidate.detailAddress(),
                candidate.phoneNumber(),
                candidate.kakaoPlaceUrl());
    }

    public record PlaceSearchRequest(String name, String roadAddressHint) {
    }

    public record PlaceSearchResponse(List<PlaceSearchItem> items) {
    }

    public record PlaceSearchItem(
            String placeName,
            String kakaoPlaceUrl,
            String roadAddress,
            String phoneNumber,
            String district) {
    }

    private PlaceSearchItem toPlaceSearchItem(SearchAdminPlaceCandidatesUseCase.PlaceCandidateResult result) {
        return new PlaceSearchItem(
                result.placeName(), result.kakaoPlaceUrl(), result.roadAddress(), result.phoneNumber(),
                result.district());
    }
}
