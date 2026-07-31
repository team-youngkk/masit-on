package com.masiton.restaurant.infrastructure.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MapRateLimitProperties.class)
public class MapRateLimitConfiguration {
}
