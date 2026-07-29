package com.masiton.member.infrastructure.configuration;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemberSessionConfiguration {

    @Bean
    Clock memberSessionClock() {
        return Clock.systemUTC();
    }
}
