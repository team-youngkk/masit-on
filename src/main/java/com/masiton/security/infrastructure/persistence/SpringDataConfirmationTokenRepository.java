package com.masiton.security.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Infrastructure 내부 전용 Spring Data Repository다.
 * Application이나 다른 도메인에서 직접 주입하지 않는다.
 */
interface SpringDataConfirmationTokenRepository extends JpaRepository<ConfirmationTokenJpaEntity, UUID> {
}
