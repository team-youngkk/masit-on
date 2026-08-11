package com.masiton.restaurant.infrastructure.external.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.masiton.restaurant.application.port.out.CourseRouteProviderPort;
import com.masiton.restaurant.application.port.out.CourseRouteQuotaPort;
import com.masiton.restaurant.infrastructure.redis.RedisCourseRouteQuota;

import java.time.Clock;

import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(KakaoMobilityProperties.class)
public class KakaoMobilityConfiguration {

    @Bean
    CourseRouteProviderPort kakaoMobilityCourseRouteProvider(
            ObjectMapper objectMapper,
            KakaoMobilityProperties properties,
            CourseRouteQuotaPort quotaPort
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new KakaoMobilityCourseRouteAdapter(httpClient, objectMapper, properties, quotaPort);
    }

    @Bean
    CourseRouteQuotaPort kakaoMobilityCourseRouteQuota(
            StringRedisTemplate redisTemplate,
            KakaoMobilityProperties properties,
            @Qualifier("restaurantCourseClock") Clock clock
    ) {
        return new RedisCourseRouteQuota(redisTemplate, properties, clock);
    }
}
