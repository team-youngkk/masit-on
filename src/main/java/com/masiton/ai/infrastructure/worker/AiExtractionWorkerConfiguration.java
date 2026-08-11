package com.masiton.ai.infrastructure.worker;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.masiton.ai.application.AiWorkerDelay;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AiExtractionWorkerProperties.class)
public class AiExtractionWorkerConfiguration {

    @Bean
    Clock aiWorkerClock() {
        return Clock.systemUTC();
    }

    @Bean
    AiWorkerDelay aiWorkerDelay() {
        return duration -> {
            try {
                java.util.concurrent.TimeUnit.NANOSECONDS.sleep(duration.toNanos());
                return true;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        };
    }
}
