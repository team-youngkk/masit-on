package com.masiton.video.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * video 테이블에 대한 Spring Data JPA Repository다.
 * Infrastructure 내부 타입이며 Application·Presentation에서 직접 주입하지 않는다.
 */
interface SpringDataVideoRepository extends JpaRepository<VideoJpaEntity, UUID> {
}
