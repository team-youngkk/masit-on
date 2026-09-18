package com.masiton.restaurant.infrastructure.revalidation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import java.time.Clock;
import com.masiton.restaurant.application.RestaurantPlaceRevalidationProperties;
@Configuration
@EnableConfigurationProperties(RestaurantPlaceRevalidationProperties.class)
class RestaurantPlaceRevalidationConfiguration {
    @Bean Clock restaurantPlaceRevalidationClock() { return Clock.systemUTC(); }
}
