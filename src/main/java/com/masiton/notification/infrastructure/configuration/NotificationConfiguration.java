package com.masiton.notification.infrastructure.configuration;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationConfiguration {

    @Bean
    Clock notificationClock() {
        return Clock.systemUTC();
    }
}
