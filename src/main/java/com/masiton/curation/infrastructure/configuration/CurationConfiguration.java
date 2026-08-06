package com.masiton.curation.infrastructure.configuration;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CurationConfiguration {
    @Bean
    Clock curationClock() {
        return Clock.systemUTC();
    }
}
