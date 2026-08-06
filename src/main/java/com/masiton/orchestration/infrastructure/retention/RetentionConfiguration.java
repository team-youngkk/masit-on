package com.masiton.orchestration.infrastructure.retention;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RetentionConfiguration {
    @Bean
    Clock retentionClock() {
        return Clock.systemUTC();
    }
}
