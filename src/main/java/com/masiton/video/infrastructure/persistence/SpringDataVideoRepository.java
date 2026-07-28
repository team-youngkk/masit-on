package com.masiton.video.infrastructure.persistence;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

/**
 * video 테이블에 대한 Spring Data JPA Repository다.
 * Infrastructure 내부 타입이며 Application·Presentation에서 직접 주입하지 않는다.
 */
interface SpringDataVideoRepository extends JpaRepository<VideoJpaEntity, UUID> {
    Optional<VideoJpaEntity> findByExternalVideoId(String externalVideoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select video from VideoJpaEntity video where video.id = :id")
    Optional<VideoJpaEntity> findByIdForUpdate(UUID id);
}
