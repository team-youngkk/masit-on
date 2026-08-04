package com.masiton.common.idempotency.infrastructure.configuration;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class IdempotencyConfiguration {

    @Bean
    Clock idempotencyClock() {
        return Clock.systemUTC();
    }
}
