package com.masiton.restaurant.infrastructure.revalidation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.masiton.restaurant.application.port.in.RestaurantPlaceRevalidationUseCase;
@Component class RestaurantPlaceRevalidationScheduler {
 private final RestaurantPlaceRevalidationUseCase useCase; RestaurantPlaceRevalidationScheduler(RestaurantPlaceRevalidationUseCase useCase){this.useCase=useCase;}
 @Scheduled(fixedDelayString="${masiton.restaurant.place-revalidation.poll-interval:PT5M}") public void poll(){useCase.poll();}
}
