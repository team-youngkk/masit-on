package com.masiton.restaurant.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.restaurant.application.port.in.VerifiedRestaurantRegistrationUseCase;
import com.masiton.restaurant.application.port.out.RestaurantRepositoryPort;
import com.masiton.restaurant.domain.model.LifecycleStatus;
import com.masiton.restaurant.domain.model.PublicationStatus;
import com.masiton.restaurant.domain.model.Restaurant;

/**
 * 자동 확정 orchestration이 보유한 트랜잭션에 참여하는 Restaurant 등록 Adapter다.
 */
@Service
public class VerifiedRestaurantRegistrationService implements VerifiedRestaurantRegistrationUseCase {

    private final RestaurantRepositoryPort restaurantRepository;

    public VerifiedRestaurantRegistrationService(RestaurantRepositoryPort restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RegistrationResult register(VerifiedRestaurantCommand command) {
        requireCommand(command);
        return restaurantRepository.findByKakaoPlaceId(command.kakaoPlaceId())
                .map(existing -> new RegistrationResult(existing.getId(), false))
                .orElseGet(() -> insert(command));
    }

    private RegistrationResult insert(VerifiedRestaurantCommand command) {
        Restaurant restaurant = new Restaurant(
                UUID.randomUUID(),
                command.regionId(),
                command.foodCategoryId(),
                command.name(),
                command.kakaoPlaceId(),
                command.kakaoPlaceUrl(),
                command.roadAddress(),
                command.detailAddress(),
                command.phoneNumber(),
                command.latitude(),
                command.longitude(),
                PublicationStatus.PUBLIC,
                LifecycleStatus.ACTIVE,
                null,
                null,
                null);
        return restaurantRepository.insertIfAbsent(restaurant)
                .map(saved -> new RegistrationResult(saved.getId(), true))
                .orElseGet(() -> restaurantRepository.findByKakaoPlaceId(command.kakaoPlaceId())
                        .map(existing -> new RegistrationResult(existing.getId(), false))
                        .orElseThrow(() -> new IllegalStateException("Concurrent restaurant result was not found.")));
    }

    private void requireCommand(VerifiedRestaurantCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.regionId(), "regionId");
        Objects.requireNonNull(command.foodCategoryId(), "foodCategoryId");
        requireText(command.name(), "name");
        requireText(command.kakaoPlaceId(), "kakaoPlaceId");
        requireText(command.kakaoPlaceUrl(), "kakaoPlaceUrl");
        requireText(command.roadAddress(), "roadAddress");
        requireText(command.phoneNumber(), "phoneNumber");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
    }
}
