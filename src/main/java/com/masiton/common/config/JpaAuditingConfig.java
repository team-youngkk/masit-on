package com.masiton.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * created_at, updated_at 자동 갱신을 위한 JPA Auditing 설정이다.
 * physical-data-model.md 7절: updated_at은 JPA auditing 또는 공통 리스너 중 한 방식만 사용한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
