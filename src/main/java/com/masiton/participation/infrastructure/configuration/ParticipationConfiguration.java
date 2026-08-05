package com.masiton.participation.infrastructure.configuration;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ParticipationConfiguration {

    @Bean
    Clock participationClock() {
        return Clock.systemUTC();
    }
}
