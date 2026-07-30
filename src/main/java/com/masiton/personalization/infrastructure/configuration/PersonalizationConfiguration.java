package com.masiton.personalization.infrastructure.configuration;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class PersonalizationConfiguration {

    @Bean
    Clock personalizationClock() {
        return Clock.systemUTC();
    }
}
