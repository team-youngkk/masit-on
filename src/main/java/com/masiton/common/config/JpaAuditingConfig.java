package com.masiton.common.config;

import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * created_at, updated_at 자동 갱신을 위한 JPA Auditing 설정이다.
 * physical-data-model.md 7절: updated_at은 JPA auditing 또는 공통 리스너 중 한 방식만 사용한다.
 *
 * <p>기본 {@code DateTimeProvider}는 시각을 {@code LocalDateTime}으로 공급하며, 시간대 정보가 없어
 * {@link com.masiton.common.persistence.BaseAuditable}의 {@code OffsetDateTime} 필드로 자동 변환하지
 * 않는다({@code IllegalArgumentException: Cannot convert ... LocalDateTime to OffsetDateTime}). 감사
 * 대상 필드 타입과 일치하는 {@code OffsetDateTime}을 직접 공급해 변환 자체를 생략시킨다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    DateTimeProvider offsetDateTimeProvider() {
        return () -> Optional.of((TemporalAccessor) OffsetDateTime.now());
    }
}
