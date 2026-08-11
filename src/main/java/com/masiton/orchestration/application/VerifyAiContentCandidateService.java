package com.masiton.orchestration.application;

import java.net.URI;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase;
import com.masiton.restaurant.application.port.in.ResolveVerifiedRestaurantReferenceUseCase;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;

/** Kakao·YouTube 검증 순서와 교차 도메인 최소 Snapshot 조합을 소유한다. */
@Service
class VerifyAiContentCandidateService implements VerifyAiContentCandidateUseCase {

    private final ResolveVerifiedRestaurantReferenceUseCase restaurantReference;
    private final ResolveVerifiedVideoUseCase videoVerification;

    VerifyAiContentCandidateService(ResolveVerifiedRestaurantReferenceUseCase restaurantReference,
                                    ResolveVerifiedVideoUseCase videoVerification) {
        this.restaurantReference = restaurantReference;
        this.videoVerification = videoVerification;
    }

    @Override
    public Optional<VerifiedContent> verify(VerificationCommand command) {
        Optional<ResolveVerifiedRestaurantReferenceUseCase.VerifiedRestaurantReference> restaurant =
                restaurantReference.resolve(command.restaurantName(), command.candidateAddress(),
                        command.kakaoPlaceUrl(), command.menuExpression());
        if (restaurant.isEmpty()) {
            return Optional.empty();
        }
        Optional<VerifiedVideo> video = videoVerification.resolve(command.videoUrl());
        if (video.isEmpty() || !matchesVideo(command, video.get())) {
            return Optional.empty();
        }
        VerifiedVideo verifiedVideo = video.get();
        var verifiedRestaurant = restaurant.get();
        return Optional.of(new VerifiedContent(
                verifiedRestaurant.regionId(), verifiedRestaurant.foodCategoryId(), verifiedRestaurant.name(),
                verifiedRestaurant.kakaoPlaceId(), verifiedRestaurant.kakaoPlaceUrl(),
                verifiedRestaurant.roadAddress(), verifiedRestaurant.phoneNumber(),
                verifiedRestaurant.latitude(), verifiedRestaurant.longitude(),
                verifiedVideo.publisherExternalChannelId(), verifiedVideo.channelName(),
                "https://www.youtube.com/channel/" + verifiedVideo.publisherExternalChannelId(),
                verifiedVideo.externalVideoId(), verifiedVideo.title(), verifiedVideo.sourceUrl(),
                verifiedVideo.thumbnailUrl(), verifiedVideo.publishedAt(), verifiedVideo.checkedAt()));
    }

    private boolean matchesVideo(VerificationCommand command, VerifiedVideo video) {
        return command.videoId().equals(video.externalVideoId())
                && command.channelId().equals(video.publisherExternalChannelId())
                && nonBlank(video.channelName()) && nonBlank(video.title())
                && nonBlank(video.sourceUrl()) && nonBlank(video.thumbnailUrl());
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
