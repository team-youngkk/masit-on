package com.masiton.ai.infrastructure.worker;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableConfigurationProperties(YoutubeChannelBackfillProperties.class)
public class YoutubeChannelBackfillConfiguration {
    @Bean(name = "aiBackfillTaskScheduler")
    ThreadPoolTaskScheduler aiBackfillTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("ai-backfill-poll-"); return scheduler;
    }
}
