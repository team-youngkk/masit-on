package com.masiton.orchestration.application.command;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.creator.application.port.in.VerifiedCreatorRegistrationUseCase;
import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.restaurant.application.port.in.VerifiedRestaurantRegistrationUseCase;
import com.masiton.video.application.port.in.VerifiedVideoRegistrationUseCase;
import com.masiton.visit.application.port.in.RegisterVisitUseCase;

/**
 * 자동 검증 결과의 정식 등록 트랜잭션을 소유한다.
 * 개별 도메인은 MANDATORY 전파로 이 트랜잭션에 참여한다.
 */
@Service
public class AutoRegisterVerifiedContentService implements AutoRegisterVerifiedContentUseCase {

    private final VerifiedRestaurantRegistrationUseCase restaurantRegistration;
    private final VerifiedCreatorRegistrationUseCase creatorRegistration;
    private final VerifiedVideoRegistrationUseCase videoRegistration;
    private final RegisterVisitUseCase visitRegistration;

    public AutoRegisterVerifiedContentService(
            VerifiedRestaurantRegistrationUseCase restaurantRegistration,
            VerifiedCreatorRegistrationUseCase creatorRegistration,
            VerifiedVideoRegistrationUseCase videoRegistration,
            RegisterVisitUseCase visitRegistration) {
        this.restaurantRegistration = restaurantRegistration;
        this.creatorRegistration = creatorRegistration;
        this.videoRegistration = videoRegistration;
        this.visitRegistration = visitRegistration;
    }

    @Override
    @Transactional
    public RegistrationResult register(VerifiedContentCommand command) {
        requireCommand(command);
        if (!command.visitEvidenceConfirmed()) {
            throw new IllegalArgumentException("visitEvidenceConfirmed must be true for automatic registration.");
        }
        if (!command.creator().externalChannelId().equals(command.video().publisherExternalChannelId())) {
            throw new IllegalArgumentException("Video publisher channel does not match creator channel.");
        }

        VerifiedCreatorRegistrationUseCase.RegistrationResult creator = creatorRegistration.register(
                new VerifiedCreatorRegistrationUseCase.VerifiedCreatorCommand(
                        command.creator().externalChannelId(),
                        command.creator().channelName(),
                        command.creator().channelUrl()));
        VerifiedRestaurantRegistrationUseCase.RegistrationResult restaurant = restaurantRegistration.register(
                new VerifiedRestaurantRegistrationUseCase.VerifiedRestaurantCommand(
                        command.restaurant().regionId(),
                        command.restaurant().foodCategoryId(),
                        command.restaurant().name(),
                        command.restaurant().kakaoPlaceId(),
                        command.restaurant().kakaoPlaceUrl(),
                        command.restaurant().roadAddress(),
                        command.restaurant().detailAddress(),
                        command.restaurant().phoneNumber(),
                        command.restaurant().latitude(),
                        command.restaurant().longitude()));
        VerifiedVideoRegistrationUseCase.RegistrationResult video = videoRegistration.register(
                new VerifiedVideoRegistrationUseCase.VerifiedVideoCommand(
                        creator.creatorId(),
                        command.video().externalVideoId(),
                        command.video().publisherExternalChannelId(),
                        command.video().title(),
                        command.video().sourceUrl(),
                        command.video().thumbnailUrl(),
                        command.video().publishedAt(),
                        command.video().checkedAt()));
        RegisterVisitUseCase.VisitRegistrationResult visit = visitRegistration.register(
                new RegisterVisitUseCase.RegisterVisitCommand(
                        restaurant.restaurantId(),
                        creator.creatorId(),
                        video.videoId(),
                        true));

        return new RegistrationResult(
                restaurant.restaurantId(),
                creator.creatorId(),
                video.videoId(),
                visit.id(),
                restaurant.created(),
                creator.created(),
                video.created(),
                visit.created());
    }

    private void requireCommand(VerifiedContentCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.restaurant(), "restaurant");
        Objects.requireNonNull(command.creator(), "creator");
        Objects.requireNonNull(command.video(), "video");
    }
}
