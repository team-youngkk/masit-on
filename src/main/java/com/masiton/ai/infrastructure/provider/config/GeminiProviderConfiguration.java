package com.masiton.ai.infrastructure.provider.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.masiton.ai.application.port.out.AiVideoExtractionProvider;

import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(GeminiProviderProperties.class)
public class GeminiProviderConfiguration {

    @Bean
    AiVideoExtractionProvider geminiVideoExtractionProvider(
            ObjectMapper objectMapper,
            GeminiProviderProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        return new GeminiHttpVideoExtractionAdapter(httpClient, objectMapper, properties);
    }
}
