package com.masiton.video.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.video.application.port.out.VideoRepositoryPort;
import com.masiton.video.domain.model.Video;

import jakarta.persistence.EntityManager;

/**
 * VideoRepositoryPort의 Spring Data JPA 기반 구현체다.
 */
@Component
class VideoPersistenceAdapter implements VideoRepositoryPort {

    private final SpringDataVideoRepository springDataVideoRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    VideoPersistenceAdapter(
            SpringDataVideoRepository springDataVideoRepository,
            JdbcTemplate jdbcTemplate,
            EntityManager entityManager
    ) {
        this.springDataVideoRepository = springDataVideoRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Override
    public Video save(Video video) {
        VideoJpaEntity savedEntity = springDataVideoRepository.save(VideoMapper.toEntity(video));
        return VideoMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Video> findById(UUID id) {
        return springDataVideoRepository.findById(id)
                .map(VideoMapper::toDomain);
    }

    @Override
    public Optional<Video> findByIdForUpdate(UUID id) {
        return springDataVideoRepository.findByIdForUpdate(id)
                .map(VideoMapper::toDomain);
    }

    @Override
    public Optional<Video> findByExternalVideoId(String externalVideoId) {
        return springDataVideoRepository.findByExternalVideoId(externalVideoId).map(VideoMapper::toDomain);
    }

    @Override
    public Optional<Video> insertIfAbsent(Video video) {
        UUID id = jdbcTemplate.query("""
                        insert into video (
                            id, creator_id, external_video_id, publisher_external_channel_id, title, source_url,
                            thumbnail_url, published_at, publication_status, lifecycle_status,
                            external_availability_status, external_status_checked_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (external_video_id) do nothing
                        returning id
                        """,
                resultSet -> resultSet.next() ? resultSet.getObject("id", UUID.class) : null,
                video.getId(), video.getCreatorId(), video.getExternalVideoId(), video.getPublisherExternalChannelId(),
                video.getTitle(), video.getSourceUrl(), video.getThumbnailUrl(), video.getPublishedAt(),
                video.getPublicationStatus().name(), video.getLifecycleStatus().name(),
                video.getExternalAvailabilityStatus().name(), video.getExternalStatusCheckedAt());
        return id == null ? Optional.empty() : findById(id);
    }

    @Override
    public Optional<Video> assignCreatorIfUnassigned(UUID videoId, UUID creatorId) {
        UUID updatedId = jdbcTemplate.query(
                """
                update video
                   set creator_id = ?, updated_at = current_timestamp
                 where id = ?
                   and creator_id is null
                returning id
                """,
                resultSet -> resultSet.next() ? resultSet.getObject("id", UUID.class) : null,
                creatorId,
                videoId);
        // Earlier reference reads can leave a Video entity with a null creator in the persistence context.
        // The conditional JDBC update must be observed before the losing concurrent request validates it.
        entityManager.clear();
        return updatedId == null ? Optional.empty() : findById(updatedId);
    }
}
