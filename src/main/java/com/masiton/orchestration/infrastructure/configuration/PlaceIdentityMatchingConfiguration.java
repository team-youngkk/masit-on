package com.masiton.orchestration.infrastructure.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlaceIdentityMatchingProperties.class)
public class PlaceIdentityMatchingConfiguration {
}
