package com.masiton.restaurant.application.naturallanguage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.masiton.common.web.BusinessException;
import com.masiton.restaurant.application.port.out.NaturalLanguageParser;

/** parser 담당 구현이 연결되기 전에도 자연어 경계를 fail-closed로 유지한다. */
@Configuration
class NaturalLanguageParserFallbackConfiguration {

    @Bean
    @ConditionalOnMissingBean(NaturalLanguageParser.class)
    NaturalLanguageParser unavailableNaturalLanguageParser() {
        return sentence -> {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "NATURAL_LANGUAGE_UNAVAILABLE",
                    "자연어 해석 구성요소를 사용할 수 없습니다.");
        };
    }
}
