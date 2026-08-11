package com.masiton.restaurant.infrastructure.configuration;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestaurantCourseConfiguration {
    @Bean
    Clock restaurantCourseClock() {
        return Clock.systemUTC();
    }
}
