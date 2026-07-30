package com.masiton.member.infrastructure.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MemberActionMailProperties.class)
public class MemberActionMailConfiguration {
}
