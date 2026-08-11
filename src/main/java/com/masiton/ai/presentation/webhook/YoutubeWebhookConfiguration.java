package com.masiton.ai.presentation.webhook;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(YoutubeWebhookProperties.class)
class YoutubeWebhookConfiguration {

    @Bean
    YoutubeAtomNotificationParser youtubeAtomNotificationParser() {
        return new YoutubeAtomNotificationParser();
    }
}
