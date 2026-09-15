package com.masiton.ai.infrastructure.worker;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

import io.micrometer.core.instrument.MeterRegistry;

import com.masiton.ai.application.port.out.YoutubeChannelBackfillQuotaPort;
import com.masiton.ai.infrastructure.redis.RedisYoutubeChannelBackfillQuota;
@Configuration
@EnableConfigurationProperties(YoutubeChannelBackfillProperties.class)
public class YoutubeChannelBackfillConfiguration {
    @Bean(name = "aiBackfillTaskScheduler")
    ThreadPoolTaskScheduler aiBackfillTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("ai-backfill-poll-"); return scheduler;
    }

    @Bean
    YoutubeChannelBackfillQuotaPort youtubeChannelBackfillQuota(
            StringRedisTemplate redisTemplate,
            YoutubeChannelBackfillProperties properties,
            @Qualifier("aiWorkerClock") Clock clock,
            MeterRegistry meterRegistry) {
        return new RedisYoutubeChannelBackfillQuota(redisTemplate, properties, clock, meterRegistry);
    }
}
